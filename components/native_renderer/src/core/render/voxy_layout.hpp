#pragma once
#include <cstdint>
#include <span>
#include <stdexcept>
#include <unordered_set>
#include <algorithm>
#include <vector>

namespace mcvr::voxy {
struct NodeKey {
    uint32_t level;
    int32_t x, y, z;
    static NodeKey decode(uint64_t key) {
        const auto level = uint32_t(key >> 60);
        if (level > 4 || (key & 15)) throw std::invalid_argument("Invalid Voxy node key");
        return {level, int32_t(int64_t(key << 36) >> 40), int32_t(int64_t(key << 4) >> 56),
                int32_t(int64_t(key << 12) >> 40)};
    }
    uint64_t encode() const {
        if (level > 4 || x < -(1 << 23) || x >= (1 << 23) || y < -128 || y > 127 || z < -(1 << 23) || z >= (1 << 23))
            throw std::invalid_argument("Voxy node coordinate range");
        return (uint64_t(level) << 60) | (uint64_t(uint32_t(y) & 255) << 52) |
               (uint64_t(uint32_t(z) & 0xffffff) << 28) | (uint64_t(uint32_t(x) & 0xffffff) << 4);
    }
    NodeKey parent() const {
        if (level >= 4) throw std::invalid_argument("Voxy root has no parent");
        return {level + 1, x >> 1, y >> 1, z >> 1};
    }
};
// The requested source domain is not a set of resident meshes. Store complete
// horizontal intervals instead of imposing the residency limit on coverage.
class RootCoverage {
  public:
    static constexpr size_t maxRows = 130;
    RootCoverage() = default;
    RootCoverage(std::span<const int32_t> rows, int32_t minY, int32_t maxY)
        : rows_(rows.begin(), rows.end()), minY_(minY), maxY_(maxY) {
        if (rows.size() > maxRows * 3 || rows.size() % 3 || minY < -128 || maxY > 127 || minY > maxY)
            throw std::invalid_argument("Voxy requested coverage range");
        for (size_t i = 0; i < rows.size(); i += 3) {
            const auto z = rows[i], first = rows[i + 1], last = rows[i + 2];
            if (z < -(1 << 23) || z >= (1 << 23) || first < -(1 << 23) || last >= (1 << 23) || first > last ||
                int64_t(last) - first > 129 || (i && rows[i - 3] >= z))
                throw std::invalid_argument("Voxy requested coverage row");
        }
    }
    bool contains(uint64_t key) const {
        const auto node = NodeKey::decode(key);
        if (node.level != 4 || node.y < minY_ || node.y > maxY_) return false;
        size_t low = 0, high = rows_.size() / 3;
        while (low < high) {
            const auto middle = (low + high) / 2;
            if (rows_[middle * 3] < node.z)
                low = middle + 1;
            else
                high = middle;
        }
        return low < rows_.size() / 3 && rows_[low * 3] == node.z && rows_[low * 3 + 1] <= node.x &&
               node.x <= rows_[low * 3 + 2];
    }

  private:
    std::vector<int32_t> rows_;
    int32_t minY_ = 0, maxY_ = -1;
};
inline void validateCut(std::span<const uint64_t> keys) {
    if (keys.size() > 8192) throw std::length_error("Voxy selection budget");
    std::unordered_set<uint64_t> members(keys.begin(), keys.end());
    if (members.size() != keys.size()) throw std::invalid_argument("Duplicate Voxy selection");
    for (uint64_t key : keys) {
        auto node = NodeKey::decode(key);
        while (node.level < 4) {
            node = node.parent();
            if (members.contains(node.encode())) throw std::invalid_argument("Overlapping Voxy parent and child");
        }
    }
}
} // namespace mcvr::voxy
