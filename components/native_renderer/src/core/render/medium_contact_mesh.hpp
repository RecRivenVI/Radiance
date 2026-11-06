#pragma once
#include "core/render/medium_contacts.hpp"
#include "common/shared.hpp"
#include "common/render_abi.hpp"
#include "common/primitive_policy.hpp"

namespace mcvr::medium {
// Read the final per-vertex optical role, not the Minecraft render-layer bucket.
// CPU preparation retains only potential dielectric triangles, not opaque terrain.
inline std::shared_ptr<Mesh> makeMesh(const std::vector<std::vector<vk::VertexFormat::PBRVertex>> &vertices,
                                      const std::vector<std::vector<uint32_t>> &indices,
                                      const std::vector<std::vector<uint32_t>> &policies,
                                      std::span<const uint32_t> flags = {},
                                      const std::vector<std::vector<uint32_t>> &sheetPairs = {}) {
    if (!flags.empty() && std::none_of(flags.begin(), flags.end(), [](uint32_t f) {
            return (f & (abi::FACE_MEDIUM_BOUNDARY_BIT | abi::FACE_SURFACE_LAYERS_BIT)) != 0;
        }))
        return {};
    auto mesh = std::make_shared<Mesh>();
    mesh->primitiveCounts.resize(indices.size());
    for (uint32_t g = 0; g < indices.size(); ++g) {
        mesh->primitiveCounts[g] = uint32_t(indices[g].size() / 3);
        if (g < flags.size() && !(flags[g] & (abi::FACE_MEDIUM_BOUNDARY_BIT | abi::FACE_SURFACE_LAYERS_BIT))) continue;
        for (uint32_t p = 0; p < mesh->primitiveCounts[g]; ++p) {
            const auto first = indices[g][p * 3];
            const auto &v = vertices[g].at(first);
            if ((v.alphaMode & abi::PACKED_ALPHA_MASK) != abi::ALPHA_TRANSMISSION &&
                !(g < flags.size() && (flags[g] & abi::FACE_SURFACE_LAYERS_BIT)))
                continue;
            Triangle t;
            t.geometry = g;
            t.primitive = p;
            t.sameMaterialSheet = g < policies.size() && !policies[g].empty() &&
                                  (policies[g].at(first) & topology::sameMaterialSheet) != 0;
            if (g < sheetPairs.size() && !sheetPairs[g].empty()) t.sheetPair = sheetPairs[g].at(p);
            for (uint32_t i = 0; i < 3; ++i) t.points[i] = glm::dvec3(vertices[g].at(indices[g][p * 3 + i]).pos);
            mesh->triangles.push_back(t);
        }
    }
    mesh->build();
    mesh->internal = contacts(*mesh, *mesh, glm::dmat4(1), true);
    return mesh;
}
} // namespace mcvr::medium
