#pragma once
#include <cstdint>
#include <span>
#include <stdexcept>
#include <unordered_set>

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
inline void validateCut(std::span<const uint64_t> keys, std::span<const uint64_t> roots) {
    if (keys.size() > 8192 || roots.size() > 8192) throw std::length_error("Voxy selection budget");
    std::unordered_set<uint64_t> members(keys.begin(), keys.end());
    if (members.size() != keys.size()) throw std::invalid_argument("Duplicate Voxy selection");
    for (uint64_t key : keys) {
        auto node = NodeKey::decode(key);
        while (node.level < 4) {
            node = node.parent();
            if (members.contains(node.encode())) throw std::invalid_argument("Overlapping Voxy parent and child");
        }
    }
    for (auto root : roots)
        if (NodeKey::decode(root).level != 4) throw std::invalid_argument("Voxy requested root is not level 4");
}
} // namespace mcvr::voxy
