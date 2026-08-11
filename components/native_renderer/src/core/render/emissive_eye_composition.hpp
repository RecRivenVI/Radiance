#pragma once
#include "core/render/source_emission.hpp"
#include "core/render/material_faces.hpp"
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <span>
#include <stdexcept>
#include <vector>

namespace mcvr::surface {
struct EyeAttachment {
    size_t base, overlay;
    uint32_t texture;
};

inline uint32_t eyeTexture(uint32_t texture, uint32_t sourceFaces) {
    if (texture == 0 || texture > abi::EYE_TEXTURE_MASK)
        throw std::invalid_argument("Eye texture outside bounded descriptor table");
    const auto face = faces::effective(sourceFaces);
    return texture | ((face & faces::back) ? abi::EYE_CULL_BACK_BIT : 0u) |
           ((face & faces::front) ? abi::EYE_CULL_FRONT_BIT : 0u);
}

inline bool eyeBaseCovers(uint32_t base, uint32_t overlay) {
    // The surviving base must exist wherever the overlay exists. Different facing is retained
    // in the coating; other material/ownership bits may not be silently discarded.
    return ((base ^ overlay) & ~faces::mask) == 0u && (faces::effective(base) & ~faces::effective(overlay)) == 0u;
}

template <class V>
bool sameEyeSurface(const std::vector<V> &a,
                    const std::vector<uint32_t> &ai,
                    const std::vector<V> &b,
                    const std::vector<uint32_t> &bi) {
    if (a.size() != b.size() || ai != bi) return false;
    const auto equal = [](float x, float y) { return std::abs(x - y) <= 1.0e-5f; };
    for (size_t i = 0; i < a.size(); ++i) {
        if (!equal(a[i].pos.x, b[i].pos.x) || !equal(a[i].pos.y, b[i].pos.y) || !equal(a[i].pos.z, b[i].pos.z) ||
            !equal(a[i].textureUV.x, b[i].textureUV.x) || !equal(a[i].textureUV.y, b[i].textureUV.y))
            return false;
    }
    return true;
}

template <class V>
std::vector<EyeAttachment> eyeAttachments(std::span<const uint32_t> faces,
                                          std::span<const uint32_t> policies,
                                          const std::vector<std::vector<V>> &vertices,
                                          const std::vector<std::vector<uint32_t>> &indices) {
    const size_t count = vertices.size();
    if (faces.size() != count || policies.size() != count || indices.size() != count)
        throw std::invalid_argument("Eye composition column count mismatch");
    std::vector<EyeAttachment> result;
    std::vector<bool> occupied(count, false);
    for (size_t overlay = 0; overlay < count; ++overlay) {
        if (!eyeOverlay(policies[overlay]) || vertices[overlay].empty()) continue;
        const auto &mesh = vertices[overlay];
        const auto texture = mesh.front().textureID;
        if (!texture || !mesh.front().useTexture) continue;
        bool uniform = true;
        for (const auto &v : mesh)
            if (!v.useTexture || v.textureID != texture) {
                uniform = false;
                break;
            }
        if (!uniform) continue;
        const auto accepts = [&](size_t base) {
            // Do not broaden composition to fractional/physical-transmission bases: their
            // independent coverage needs a separate layered-material contract.
            if (faces[base] != faces[overlay])
                for (const auto &vertex : vertices[base])
                    if ((vertex.alphaMode & abi::PACKED_ALPHA_MASK) != abi::ALPHA_OPAQUE) return false;
            return eyeBase(policies[base]) && !occupied[base] && eyeBaseCovers(faces[base], faces[overlay]) &&
                   sameEyeSurface(vertices[base], indices[base], mesh, indices[overlay]);
        };
        size_t base = count;
        for (size_t i = overlay; i-- > 0;)
            if (accepts(i)) {
                base = i;
                break;
            }
        if (base == count)
            for (size_t i = overlay + 1; i < count; ++i)
                if (accepts(i)) {
                    base = i;
                    break;
                }
        if (base != count) {
            result.push_back({base, overlay, eyeTexture(texture, faces[overlay])});
            occupied[base] = true;
        }
    }
    return result;
}

// Move the overlay's independent draw state to the surviving surface, then erase all
// columns together. No pointer into the submitted Java buffers escapes this operation.
template <class Colour, class... Columns>
void applyEyeAttachments(const std::vector<EyeAttachment> &attachments,
                         std::vector<Colour> &drawColours,
                         std::vector<Colour> &eyeColours,
                         std::vector<uint32_t> &textures,
                         const Colour &identity,
                         Columns &...columns) {
    const size_t count = drawColours.size();
    if (!((columns.size() == count) && ...)) throw std::invalid_argument("Eye composition owner column mismatch");
    size_t previous = 0;
    bool first = true;
    for (const auto &entry : attachments) {
        if (entry.base >= count || entry.overlay >= count || entry.base == entry.overlay)
            throw std::out_of_range("Eye composition owner index");
        if (!first && entry.overlay <= previous)
            throw std::invalid_argument("Eye composition overlays must be unique and ordered");
        for (const auto &other : attachments)
            if (entry.base == other.overlay)
                throw std::invalid_argument("Eye composition cannot remove an attachment owner");
        previous = entry.overlay;
        first = false;
    }
    textures.assign(count, 0);
    eyeColours.assign(count, identity);
    for (const auto &entry : attachments) {
        textures[entry.base] = entry.texture;
        eyeColours[entry.base] = drawColours[entry.overlay];
    }
    for (auto it = attachments.rbegin(); it != attachments.rend(); ++it) {
        const auto i = static_cast<std::ptrdiff_t>(it->overlay);
        drawColours.erase(drawColours.begin() + i);
        eyeColours.erase(eyeColours.begin() + i);
        textures.erase(textures.begin() + i);
        (columns.erase(columns.begin() + i), ...);
    }
}
} // namespace mcvr::surface
