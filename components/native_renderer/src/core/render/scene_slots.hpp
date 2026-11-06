#pragma once

#include <cstddef>
#include <cstdint>
#include <deque>
#include <map>
#include <optional>
#include <stdexcept>
#include <unordered_map>
#include <vector>

namespace mcvr {

// Stable world-table addresses for one view. Geometry ownership and Vulkan
// retirement stay with the frame, even when a source stops selecting a slot.
class SceneSlots {
  public:
    struct Range {
        uint32_t slot, record, group, geometries;
    };

    void begin() {
        ++frame_;
        additions_ = removals_ = moves_ = 0;
        retired_.clear();
        while (!quarantine_.empty() && quarantine_.front().until <= frame_) {
            const auto &entry = quarantine_.front();
            records_[entry.range.geometries].push_back({entry.range.record, entry.range.group});
            if (entry.releaseSlot) freeSlots_.push_back(entry.range.slot);
            quarantine_.pop_front();
        }
    }

    std::optional<Range> select(uint64_t source, uint32_t geometries, uint32_t *poseSlot = nullptr) {
        if (source == 0) return {};
        auto found = sources_.find(source);
        if (found == sources_.end()) {
            uint32_t slot;
            if (freeSlots_.empty()) {
                if (entries_.size() >= limit) throw std::length_error("Scene instance slot range exhausted");
                slot = static_cast<uint32_t>(entries_.size());
                entries_.push_back({});
            } else {
                slot = freeSlots_.back();
                freeSlots_.pop_back();
            }
            const auto records = allocateRecords(geometries);
            entries_[slot] = {source, frame_, records.geometry, records.group, geometries};
            sources_.emplace(source, slot);
            active_.push_back(slot);
            ++additions_;
            if (poseSlot) *poseSlot = slot;
            return Range{slot, records.geometry, records.group, geometries};
        }

        const auto slot = found->second;
        if (poseSlot) *poseSlot = slot;
        auto &entry = entries_[slot];
        // The legacy list may contain more than one placement of a history key.
        // The additional occurrence keeps its own temporary row for this view.
        if (entry.selected == frame_) return {};
        entry.selected = frame_;
        if (entry.geometries != geometries) {
            const auto records = allocateRecords(geometries);
            retire({slot, entry.record, entry.group, entry.geometries}, false);
            entry.record = records.geometry;
            entry.group = records.group;
            entry.geometries = geometries;
            ++moves_;
        }
        return Range{slot, entry.record, entry.group, geometries};
    }

    void finish() {
        size_t remaining = 0;
        for (uint32_t slot : active_) {
            auto &entry = entries_[slot];
            if (entry.selected == frame_) {
                active_[remaining++] = slot;
                continue;
            }
            retire({slot, entry.record, entry.group, entry.geometries}, true);
            sources_.erase(entry.source);
            entry = {};
            ++removals_;
        }
        active_.resize(remaining);
    }

    uint32_t slotCount() const {
        return static_cast<uint32_t>(entries_.size());
    }
    uint32_t recordCount() const {
        return recordCount_;
    }
    uint32_t groupCount() const {
        return groupCount_;
    }
    uint32_t additions() const {
        return additions_;
    }
    uint32_t removals() const {
        return removals_;
    }
    uint32_t moves() const {
        return moves_;
    }
    const std::vector<Range> &retired() const {
        return retired_;
    }

  private:
    static constexpr uint32_t limit = 1u << 24u;
    struct Entry {
        uint64_t source, selected;
        uint32_t record, group, geometries;
    };
    struct Records {
        uint32_t geometry, group;
    };
    struct Retired {
        Range range;
        uint64_t until;
        bool releaseSlot;
    };

    Records allocateRecords(uint32_t geometries) {
        auto &bin = records_[geometries];
        if (!bin.empty()) {
            const auto result = bin.back();
            bin.pop_back();
            return result;
        }
        if (geometries >= limit - groupCount_) throw std::length_error("Scene hit group range exhausted");
        const Records result{recordCount_, groupCount_};
        recordCount_ += geometries;
        groupCount_ += geometries + 1; // Only the SBT includes a shadow record.
        return result;
    }

    void retire(Range range, bool releaseSlot) {
        retired_.push_back(range);
        quarantine_.push_back({range, frame_ + 2, releaseSlot});
    }

    uint64_t frame_ = 0;
    uint32_t recordCount_ = 0, groupCount_ = 0;
    uint32_t additions_ = 0, removals_ = 0, moves_ = 0;
    std::vector<Entry> entries_;
    std::vector<uint32_t> active_, freeSlots_;
    std::vector<Range> retired_;
    std::unordered_map<uint64_t, uint32_t> sources_;
    std::map<uint32_t, std::vector<Records>> records_;
    std::deque<Retired> quarantine_;
};

} // namespace mcvr
