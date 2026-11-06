#pragma once
#include "core/render/quad_topology.hpp"
#include "core/render/geometry_policy.hpp"
#include "core/vulkan/vertex.hpp"

namespace mcvr::geometry {
inline constexpr uint32_t pairedGeometryBit = 1u << 19;
struct PreparedTopology {
    std::vector<uint32_t> vertexFlags;
    topology::Report report;
    std::vector<uint32_t> sheetPairs;
};
// Position coincidence alone cannot distinguish a two-sided sheet from the
// shared wall of two differently authored media. Keep that provenance beside
// the existing primitive ownership, before material packing loses source UVs.
inline void markSameMaterialSheets(std::span<const vk::VertexFormat::PBRVertex> vertices,
                                   std::span<const uint32_t> indices,
                                   bool quads,
                                   std::vector<uint32_t> &flags,
                                   std::vector<uint32_t> &sheetPairs) {
    const size_t stride = quads ? 6 : 3;
    const size_t corners = quads ? 4 : 3;
    std::unordered_map<topology::Key, size_t, topology::KeyHash> first;
    auto polygon = [&](size_t at) {
        return std::array<uint32_t, 4>{indices[at], indices[at + 1], indices[at + 2], indices[at + (quads ? 4 : 2)]};
    };
    for (size_t at = 0; at < indices.size(); at += stride) {
        if ((flags[at / 3] & topology::paired) == 0) continue;
        auto b = polygon(at);
        topology::Key key;
        for (size_t j = 0; j < 4; ++j) {
            const auto &p = vertices[b[j]].pos;
            key[j] = topology::bits({p.x, p.y, p.z});
        }
        if (!quads) key[3] = {};
        std::sort(key.begin(), key.end());
        auto [it, inserted] = first.try_emplace(key, at);
        if (inserted) continue;
        auto a = polygon(it->second);
        bool same = true;
        glm::vec2 amin(INFINITY), amax(-INFINITY), bmin(INFINITY), bmax(-INFINITY);
        for (size_t j = 0; j < corners; ++j) {
            const auto &bv = vertices[b[j]];
            amin = glm::min(amin, vertices[a[j]].textureUV);
            amax = glm::max(amax, vertices[a[j]].textureUV);
            bmin = glm::min(bmin, bv.textureUV);
            bmax = glm::max(bmax, bv.textureUV);
            auto match =
                std::find_if(a.begin(), a.begin() + corners, [&](auto id) { return vertices[id].pos == bv.pos; });
            if (match == a.begin() + corners) {
                same = false;
                continue;
            }
            const auto &av = vertices[*match];
            same &= av.useTexture == bv.useTexture && av.textureID == bv.textureID &&
                    av.useColorLayer == bv.useColorLayer && av.colorLayer == bv.colorLayer &&
                    av.alphaMode == bv.alphaMode && av.useOverlay == bv.useOverlay && av.overlayUV == bv.overlayUV;
        }
        same &= amin == bmin && amax == bmax;
        if (!same) continue;
        if (sheetPairs.empty()) sheetPairs.resize(flags.size());
        const auto pair = static_cast<uint32_t>(it->second / 3 + 1);
        for (auto start : {at, it->second}) {
            flags[start / 3] |= topology::sameMaterialSheet;
            sheetPairs[start / 3] = pair;
            if (quads) flags[start / 3 + 1] |= topology::sameMaterialSheet;
            if (quads) sheetPairs[start / 3 + 1] = pair;
        }
    }
}
inline PreparedTopology
prepare(std::vector<vk::VertexFormat::PBRVertex> &vertices, std::vector<uint32_t> &indices, bool authoredQuads) {
    std::vector<topology::Point> positions;
    positions.reserve(vertices.size());
    for (const auto &v : vertices) positions.push_back({v.pos.x, v.pos.y, v.pos.z});
    auto out = topology::reconcileAuthored(positions, indices, authoredQuads, concordantQuads());
    if (out.report.nonfinite) throw std::invalid_argument("Nonfinite quad position");
    std::vector<uint32_t> sheetPairs;
    if (out.report.pairedQuads || out.report.pairedTriangles)
        markSameMaterialSheets(vertices, indices, authoredQuads && topology::canonicalQuadExpansion(indices),
                               out.primitiveFlags, sheetPairs);
    indices = std::move(out.indices);
    PreparedTopology result{{}, out.report, std::move(sheetPairs)};
    if (out.report.pairedQuads || out.report.pairedTriangles)
        result.vertexFlags = topology::attachPrimitiveFlags(vertices, indices, out.primitiveFlags);
    return result;
}
} // namespace mcvr::geometry
