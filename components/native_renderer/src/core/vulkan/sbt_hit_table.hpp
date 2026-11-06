#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <span>
#include <stdexcept>
#include <vector>
#include <unordered_map>
#include <algorithm>

namespace mcvr {

inline size_t hitTableBytes(size_t records, size_t stride) {
    if (records == 0 || stride == 0) throw std::invalid_argument("Empty SBT hit table");
    if (records > std::numeric_limits<size_t>::max() / stride) throw std::length_error("SBT hit table overflow");
    return records * stride;
}

// Handles are immutable for the lifetime of one pipeline's SBT. Keep padding zero,
// and compare record identities, not a hash or just the allocated byte count.
inline std::vector<uint8_t> packHitTable(std::span<const uint32_t> indices,
                                         std::span<const uint8_t> handles,
                                         size_t handleSize,
                                         size_t stride,
                                         size_t firstHit,
                                         size_t hitCount) {
    if (handleSize == 0 || stride < handleSize || handles.size() % handleSize != 0)
        throw std::invalid_argument("Invalid SBT handle layout");
    const auto groups = handles.size() / handleSize;
    if (firstHit > groups || hitCount > groups - firstHit) throw std::out_of_range("SBT handle palette overflow");
    for (auto index : indices)
        if (index >= hitCount) throw std::out_of_range("Unknown SBT hit group");
    std::vector<uint8_t> packed(hitTableBytes(indices.size(), stride), 0);
    for (size_t i = 0; i < indices.size(); ++i)
        std::memcpy(packed.data() + i * stride, handles.data() + (firstHit + indices[i]) * handleSize, handleSize);
    return packed;
}

struct HitRange {
    size_t offset;
    std::shared_ptr<const std::vector<uint32_t>> groups;
};

// One pipeline/frame SBT owns this CPU cache. Group blocks are immutable CPU ids,
// never page/BLAS owners. Only blocks used by the latest successful pack survive.
class PackedHitBlocks {
    struct Block {
        std::shared_ptr<const std::vector<uint32_t>> owner;
        std::vector<uint8_t> bytes;
        uint64_t seen = 0;
    };
    std::unordered_map<const void *, Block> blocks_;
    std::vector<uint32_t> palette_;
    std::vector<uint8_t> scratch_;
    uint64_t generation_ = 0;
    size_t stride_ = 0, handleSize_ = 0, firstHit_ = 0, hitCount_ = 0;

  public:
    size_t reusedBytes = 0, generatedBytes = 0;
    size_t cachedBytes() const {
        size_t result = scratch_.capacity();
        for (const auto &[id, block] : blocks_) result += block.bytes.capacity();
        return result;
    }
    const std::vector<uint8_t> &pack(std::span<const uint32_t> indices,
                                     std::span<const HitRange> stable,
                                     std::span<const uint32_t> palette,
                                     std::span<const uint8_t> handles,
                                     size_t handleSize,
                                     size_t stride,
                                     size_t firstHit,
                                     size_t hitCount) {
        if (handleSize == 0 || stride < handleSize || handles.size() % handleSize ||
            firstHit > handles.size() / handleSize || hitCount > handles.size() / handleSize - firstHit)
            throw std::invalid_argument("Invalid packed block handle palette");
        if (stride != stride_ || handleSize != handleSize_ || firstHit != firstHit_ || hitCount != hitCount_ ||
            palette.size() < palette_.size() || !std::equal(palette_.begin(), palette_.end(), palette.begin()))
            blocks_.clear();
        stride_ = stride;
        handleSize_ = handleSize;
        firstHit_ = firstHit;
        hitCount_ = hitCount;
        palette_.assign(palette.begin(), palette.end());
        scratch_.resize(hitTableBytes(indices.size(), stride));
        reusedBytes = generatedBytes = 0;
        ++generation_;
        auto write = [&](size_t begin, size_t end) {
            auto *destination = scratch_.data() + begin * stride;
            std::fill(destination, destination + (end - begin) * stride, uint8_t{0});
            for (size_t i = begin; i < end; ++i) {
                if (indices[i] >= hitCount) throw std::out_of_range("Unknown dynamic SBT group");
                std::memcpy(scratch_.data() + i * stride, handles.data() + (firstHit + indices[i]) * handleSize,
                            handleSize);
            }
            generatedBytes += (end - begin) * stride;
        };
        try {
            size_t cursor = 0;
            for (const auto &range : stable) {
                if (!range.groups || range.groups->empty() || range.offset < cursor || range.offset > indices.size() ||
                    range.groups->size() > indices.size() - range.offset)
                    throw std::invalid_argument("Invalid stable SBT range");
                write(cursor, range.offset);
                auto found = blocks_.find(range.groups.get());
                if (found == blocks_.end()) {
                    std::vector<uint32_t> resolved;
                    resolved.reserve(range.groups->size());
                    for (auto id : *range.groups) {
                        if (id >= palette.size()) throw std::out_of_range("Unknown stable SBT group");
                        resolved.push_back(palette[id]);
                    }
                    auto packed = packHitTable(resolved, handles, handleSize, stride, firstHit, hitCount);
                    generatedBytes += packed.size();
                    found =
                        blocks_.emplace(range.groups.get(), Block{range.groups, std::move(packed), generation_}).first;
                } else {
                    reusedBytes += found->second.bytes.size();
                    found->second.seen = generation_;
                }
                std::memcpy(scratch_.data() + range.offset * stride, found->second.bytes.data(),
                            found->second.bytes.size());
                cursor = range.offset + range.groups->size();
            }
            write(cursor, indices.size());
            std::erase_if(blocks_, [&](const auto &entry) { return entry.second.seen != generation_; });
        } catch (...) {
            blocks_.clear(); // Failed CPU preparation cannot accumulate historical identities.
            throw;
        }
        return scratch_;
    }
};

// Recording-thread owned and scoped to one immutable pipeline/pass/frame SBT.
// Only the latest version is cached; older versions belong to the frame retainer.
template <class Buffer>
class HitTableCache {
  public:
    struct Selection {
        std::shared_ptr<Buffer> buffer;
        bool uploaded;
    };
    std::span<const uint32_t> observedRecords() const noexcept {
        return indices_ ? std::span<const uint32_t>(*indices_) : std::span<const uint32_t>{};
    }

    template <class Prepare, class Record, class Retain>
    Selection acquire(const std::shared_ptr<const std::vector<uint32_t>> &indices,
                      Prepare &&prepare,
                      Record &&record,
                      Retain &&retain) {
        if (!indices || indices->empty()) throw std::invalid_argument("Empty SBT hit table");
        if (buffer_ && (indices_ == indices || *indices_ == *indices)) {
            retain(buffer_);
            indices_ = indices;
            return {buffer_, false};
        }

        auto pendingIndices = indices;
        auto pending = prepare();
        if (!pending) throw std::logic_error("SBT preparation returned no buffer");
        // Retain before recording: even a later recording failure must not destroy
        // a buffer already referenced by this command buffer. Never rewrite it.
        retain(pending);
        record(pending);
        // Commit only after all preparation and recording succeeded. Swaps cannot
        // throw; failed attempts leave the last valid version available.
        indices_.swap(pendingIndices);
        buffer_.swap(pending);
        return {buffer_, true};
    }

  private:
    std::shared_ptr<const std::vector<uint32_t>> indices_;
    std::shared_ptr<Buffer> buffer_;
};

} // namespace mcvr
