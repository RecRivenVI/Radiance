#pragma once
#include "common/shared.hpp"
#include "core/render/material_faces.hpp"
#include <algorithm>
#include <array>
#include <bit>
#include <cmath>
#include <cstdint>
#include <map>
#include <span>
#include <set>
#include <stdexcept>
#include <vector>

namespace mcvr::surface {
struct LayerSourceTriangle {
    std::array<vk::VertexFormat::PBRVertex, 3> vertices;
    uint32_t faces, primitivePolicy, sourceGeometry, sourcePrimitive;
};
struct SurfaceLayers {
    std::vector<std::vector<std::vector<LayerSourceTriangle>>> primitives;
    std::vector<size_t> removedGeometries;
    uint64_t topologyKey = 0;
    size_t attachedTriangles = 0;
};
inline bool ordinaryLayerVertex(const vk::VertexFormat::PBRVertex &v) {
    const auto mode = v.alphaMode & abi::PACKED_ALPHA_MASK;
    return (mode == abi::ALPHA_OPAQUE || mode == abi::ALPHA_CUTOUT || mode == abi::ALPHA_CUTOUT_LOW ||
            mode == abi::ALPHA_CUTOUT_EPSILON || mode == abi::ALPHA_COVERAGE) &&
           ((v.alphaMode >> abi::SOURCE_ALPHA_RULE_SHIFT) & abi::SOURCE_ALPHA_RULE_MASK) !=
               abi::SOURCE_ALPHA_PRE_GLOBAL_LOW &&
           v.useColorLayer <= 1 && v.useNorm <= 1 && v.useTexture && v.textureID > 0 &&
           v.textureID <= abi::EYE_TEXTURE_MASK;
}
inline vk::VertexFormat::PBRVertex layerVertex(vk::VertexFormat::PBRVertex v, glm::vec4 draw) {
    const auto colour = v.useColorLayer ? v.colorLayer : glm::vec4(1);
    v.colorLayer = colour * draw;
    v.useColorLayer = 1;
    return v;
}

// The same physical, same-oriented triangle can carry several independently UV-mapped
// source draws. Reverse twins remain separate interfaces owned by the topology contract.
inline SurfaceLayers composeSurfaceLayers(std::vector<uint32_t> &flags,
                                          std::vector<std::vector<vk::VertexFormat::PBRVertex>> &vertices,
                                          std::vector<std::vector<uint32_t>> &indices,
                                          std::vector<glm::vec4> &drawColours,
                                          const std::vector<std::vector<uint32_t>> &vertexPolicies,
                                          std::span<const uint8_t> qualified = {}) {
    const size_t count = vertices.size();
    if (flags.size() != count || indices.size() != count || drawColours.size() != count ||
        vertexPolicies.size() != count || (!qualified.empty() && qualified.size() != count))
        throw std::invalid_argument("Surface layer column mismatch");
    using Point = std::array<uint32_t, 3>;
    using Key = std::array<Point, 3>;
    struct Owner {
        size_t geometry, primitive;
        std::array<uint32_t, 3> indices;
    };
    std::map<Key, std::vector<Owner>> owners;
    SurfaceLayers result;
    result.primitives.resize(count);
    std::vector<std::vector<bool>> removed(count);
    const auto point = [](const auto &v) -> Point {
        if (!std::isfinite(v.pos.x) || !std::isfinite(v.pos.y) || !std::isfinite(v.pos.z))
            throw std::invalid_argument("Nonfinite layered surface");
        return {std::bit_cast<uint32_t>(v.pos.x), std::bit_cast<uint32_t>(v.pos.y), std::bit_cast<uint32_t>(v.pos.z)};
    };
    const auto sourceTriangle = [&](size_t g, size_t p, const std::array<uint32_t, 3> &order) {
        LayerSourceTriangle record{};
        for (size_t j = 0; j < 3; ++j) record.vertices[j] = layerVertex(vertices[g].at(order[j]), drawColours[g]);
        record.faces = flags[g];
        record.primitivePolicy = vertexPolicies[g].empty() ? 0u : vertexPolicies[g].at(indices[g].at(p * 3));
        record.sourceGeometry = static_cast<uint32_t>(g);
        record.sourcePrimitive = static_cast<uint32_t>(p);
        return record;
    };
    const auto canonical = [](Key ordered) {
        Key key = ordered;
        for (size_t shift = 1; shift < 3; ++shift) {
            Key rotated{ordered[shift], ordered[(shift + 1) % 3], ordered[(shift + 2) % 3]};
            if (rotated < key) key = rotated;
        }
        return key;
    };
    std::vector<bool> eligible(count);
    std::set<Key> blocked;
    std::map<Key, uint64_t> ownership;
    for (size_t g = 0; g < count; ++g) {
        if (indices[g].size() % 3) throw std::invalid_argument("Layered surface requires complete triangles");
        eligible[g] =
            (qualified.empty() || qualified[g]) &&
            std::all_of(vertices[g].begin(), vertices[g].end(), ordinaryLayerVertex) &&
            !(glm::vec3(drawColours[g]) != glm::vec3(1) &&
              std::any_of(vertices[g].begin(), vertices[g].end(), [](const auto &v) { return v.useOverlay != 0; }));
        for (size_t p = 0; p < indices[g].size() / 3; ++p) {
            const Key key =
                canonical({point(vertices[g].at(indices[g][p * 3])), point(vertices[g].at(indices[g][p * 3 + 1])),
                           point(vertices[g].at(indices[g][p * 3 + 2]))});
            constexpr uint32_t backend = abi::FACE_PAIRED_BIT | abi::FACE_SURFACE_LAYERS_BIT;
            const uint32_t policy = vertexPolicies[g].empty() ? 0u : vertexPolicies[g].at(indices[g][p * 3]);
            const uint64_t signature = (uint64_t(flags[g] & ~backend) << 1u) | (policy & 1u);
            const auto [seen, first] = ownership.emplace(key, signature);
            // An unknown/coincident operation must not be reordered across a known
            // coating. Keep every source draw at this interface on the legacy path.
            if (!eligible[g] || (!first && seen->second != signature)) blocked.insert(key);
        }
    }
    for (size_t g = 0; g < count; ++g) {
        if (indices[g].size() % 3) throw std::invalid_argument("Layered surface requires complete triangles");
        result.primitives[g].resize(indices[g].size() / 3);
        removed[g].resize(indices[g].size() / 3);
        if (!eligible[g]) continue;
        for (size_t p = 0; p < indices[g].size() / 3; ++p) {
            std::array<uint32_t, 3> ix{indices[g][p * 3], indices[g][p * 3 + 1], indices[g][p * 3 + 2]};
            Key ordered{point(vertices[g].at(ix[0])), point(vertices[g].at(ix[1])), point(vertices[g].at(ix[2]))};
            if (ordered[0] == ordered[1] || ordered[1] == ordered[2] || ordered[0] == ordered[2]) continue;
            if (!std::all_of(ix.begin(), ix.end(), [&](auto i) { return ordinaryLayerVertex(vertices[g].at(i)); }))
                continue;
            // Cyclic canonicalization preserves winding. Sorting all three corners would
            // incorrectly turn an opposite-facing thin-sheet member into a coating.
            Key key = ordered;
            for (size_t shift = 1; shift < 3; ++shift) {
                Key rotated{ordered[shift], ordered[(shift + 1) % 3], ordered[(shift + 2) % 3]};
                if (rotated < key) key = rotated;
            }
            if (blocked.contains(key)) continue;
            auto &candidates = owners[key];
            const Owner *owner = nullptr;
            for (const auto &candidate : candidates) {
                constexpr uint32_t backend = abi::FACE_PAIRED_BIT | abi::FACE_SURFACE_LAYERS_BIT;
                if (candidate.geometry != g &&
                    ((flags[candidate.geometry] ^ flags[g]) & ~(faces::mask | backend)) == 0u &&
                    (faces::effective(flags[candidate.geometry]) & ~faces::effective(flags[g])) == 0u) {
                    owner = &candidate;
                    break;
                }
            }
            if (!owner) {
                candidates.push_back({g, p, ix});
                continue;
            }
            std::array<uint32_t, 3> aligned{};
            for (size_t j = 0; j < 3; ++j)
                for (size_t k = 0; k < 3; ++k)
                    if (ordered[k] == point(vertices[owner->geometry].at(owner->indices[j]))) aligned[j] = ix[k];
            auto &stack = result.primitives[owner->geometry][owner->primitive];
            if (stack.empty()) stack.push_back(sourceTriangle(owner->geometry, owner->primitive, owner->indices));
            stack.push_back(sourceTriangle(g, p, aligned));
            removed[g][p] = true;
            ++result.attachedTriangles;
        }
    }
    if (!result.attachedTriangles) return result;
    uint64_t key = 1469598103934665603ull;
    const auto hash = [&](uint64_t value) { key = (key ^ value) * 1099511628211ull; };
    for (size_t g = 0; g < count; ++g) {
        bool layered = false;
        std::vector<uint32_t> kept;
        std::vector<std::vector<LayerSourceTriangle>> stacks;
        for (size_t p = 0; p < removed[g].size(); ++p) {
            hash(g);
            hash(p);
            hash(removed[g][p]);
            if (removed[g][p]) continue;
            for (size_t j = 0; j < 3; ++j) kept.push_back(indices[g][p * 3 + j]);
            auto &stack = result.primitives[g][p];
            layered |= !stack.empty();
            for (const auto &record : stack) {
                hash(record.sourceGeometry);
                hash(record.sourcePrimitive);
            }
            stacks.push_back(std::move(stack));
        }
        indices[g] = std::move(kept);
        result.primitives[g] = std::move(stacks);
        if (indices[g].empty()) result.removedGeometries.push_back(g);
        if (layered) {
            for (auto &vertex : vertices[g]) vertex = layerVertex(vertex, drawColours[g]);
            drawColours[g] = glm::vec4(1);
            flags[g] |= abi::FACE_SURFACE_LAYERS_BIT;
        }
    }
    result.topologyKey = key;
    return result;
}

template <class... Columns>
void eraseLayerGeometries(const SurfaceLayers &result, Columns &...columns) {
    for (auto it = result.removedGeometries.rbegin(); it != result.removedGeometries.rend(); ++it) {
        const auto index = static_cast<std::ptrdiff_t>(*it);
        if (!((size_t(index) < columns.size()) && ...)) throw std::out_of_range("Surface layer removal column");
        (columns.erase(columns.begin() + index), ...);
    }
}
} // namespace mcvr::surface
