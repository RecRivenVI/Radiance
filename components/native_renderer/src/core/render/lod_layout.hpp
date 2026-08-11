#pragma once
#include <cstdint>
#include <span>
#include <stdexcept>
#include <unordered_set>
#include <algorithm>
#include <vector>
#include <utility>
#include <glm/glm.hpp>

namespace mcvr::lod {
struct HistoryEntry {
    std::pair<uint64_t, uint64_t> identity;
    glm::mat4 transform;

    bool matches(std::pair<uint64_t, uint64_t> current) const {
        return identity == current;
    }

    glm::mat4 previousTransform(std::pair<uint64_t, uint64_t> current, const glm::mat4 &fallback) const {
        return matches(current) ? transform : fallback;
    }
};

// Far selection/recording only affects near coverage while old near geometry is held for handoff.
// The near publication and confirmed-empty cells remain unconditional coverage dependencies.
inline bool canReuseCoverage(bool sameNear, bool sameEmpty, bool sameFar, bool hasHandoff) {
    return sameNear && sameEmpty && (sameFar || !hasHandoff);
}

inline uint32_t coverageHash(glm::ivec3 cell) {
    return (uint32_t(cell.x) * 73856093u) ^ (uint32_t(cell.y) * 19349663u) ^ (uint32_t(cell.z) * 83492791u);
}

inline bool coverageContains(std::span<const glm::ivec4> cells, glm::ivec3 cell) {
    if (cells.empty()) return false;
    const auto mask = cells.size() - 1;
    size_t index = coverageHash(cell) & mask;
    for (size_t probe = 0; probe < cells.size(); ++probe) {
        const auto &candidate = cells[index];
        if (!candidate.w) return false;
        if (glm::ivec3(candidate) == cell) return true;
        index = (index + 1) & mask;
    }
    return false;
}

// Only complete published near coverage can remove a whole far node. Partial
// overlaps continue through the per-hit rule, including retained handoff pages.
inline bool fullyCovered(std::span<const glm::ivec4> cells, glm::ivec3 blockOrigin, uint32_t level) {
    if (level > 4 || cells.empty()) return false;
    const glm::ivec3 first = blockOrigin / 16;
    const int width = 2 << level;
    for (int y = 0; y < width; ++y)
        for (int z = 0; z < width; ++z)
            for (int x = 0; x < width; ++x)
                if (!coverageContains(cells, first + glm::ivec3(x, y, z))) return false;
    return true;
}

struct Quad {
    uint32_t position, extent, materialFace, tint;
    uint32_t material() const {
        return materialFace & 0xffffff;
    }
    uint32_t face() const {
        return (materialFace >> 24) & 7;
    }
};
static_assert(sizeof(Quad) == 16);
inline constexpr size_t modelWords = 56;
struct NodeKey {
    uint32_t level;
    int32_t x, y, z;
    static int32_t signedBits(uint64_t value, unsigned bits) {
        const auto sign = uint64_t(1) << (bits - 1);
        return int32_t((value ^ sign) - sign);
    }
    static NodeKey decode(uint64_t key) {
        const auto level = uint32_t(key >> 61);
        if (level > 4) throw std::invalid_argument("Invalid LOD level");
        return {level, signedBits(key & 0x1fffff, 21), signedBits((key >> 21) & 0x7ffff, 19),
                signedBits((key >> 40) & 0x1fffff, 21)};
    }
    uint64_t encode() const {
        if (level > 4 || x < -(1 << 20) || x >= (1 << 20) || z < -(1 << 20) || z >= (1 << 20) || y < -(1 << 18) ||
            y >= (1 << 18))
            throw std::invalid_argument("LOD node coordinate range");
        return (uint64_t(level) << 61) | (uint64_t(uint32_t(z) & 0x1fffff) << 40) |
               (uint64_t(uint32_t(y) & 0x7ffff) << 21) | (uint32_t(x) & 0x1fffff);
    }
    NodeKey parent() const {
        if (level >= 4) throw std::invalid_argument("LOD root has no parent");
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
        if (rows.size() > maxRows * 3 || rows.size() % 3 || minY < -(1 << 18) || maxY >= (1 << 18) || minY > maxY)
            throw std::invalid_argument("LOD requested coverage range");
        for (size_t i = 0; i < rows.size(); i += 3) {
            const auto z = rows[i], first = rows[i + 1], last = rows[i + 2];
            if (z < -(1 << 20) || z >= (1 << 20) || first < -(1 << 20) || last >= (1 << 20) || first > last ||
                int64_t(last) - first > 129 || (i && rows[i - 3] >= z))
                throw std::invalid_argument("LOD requested coverage row");
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
    if (keys.size() > 8192) throw std::length_error("LOD selection budget");
    std::unordered_set<uint64_t> members(keys.begin(), keys.end());
    if (members.size() != keys.size()) throw std::invalid_argument("Duplicate LOD selection");
    for (uint64_t key : keys) {
        auto node = NodeKey::decode(key);
        while (node.level < 4) {
            node = node.parent();
            if (members.contains(node.encode())) throw std::invalid_argument("Overlapping LOD parent and child");
        }
    }
}
} // namespace mcvr::lod
