#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <span>
#include <stdexcept>
#include <vector>

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

// Recording-thread owned and scoped to one immutable pipeline/pass/frame SBT.
// Only the latest version is cached; older versions belong to the frame retainer.
template <class Buffer>
class HitTableCache {
  public:
    struct Selection {
        std::shared_ptr<Buffer> buffer;
        bool uploaded;
    };

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
