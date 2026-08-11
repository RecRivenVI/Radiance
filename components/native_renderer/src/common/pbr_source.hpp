#pragma once
#include "shared.hpp"
#include <array>
#include <cstdint>
#include <cstring>
#include <span>
#include <stdexcept>

namespace mcvr {
// Versioned CPU input ABI. Final BLAS/material streams keep their existing layouts.
inline constexpr int legacyPbrSourceFormat = abi::FORMAT_PBR_TRIANGLE;
inline constexpr int compactPbrSourceFormat = abi::FORMAT_PBR_COMPACT_V1;
inline constexpr uint32_t compactPbrSourceVersion = abi::SOURCE_COMPACT_VERSION;
inline constexpr uint32_t compactPbrSourceBytes = abi::PBR_COMPACT_BYTES;
using CompactPbrSource = std::array<uint32_t, abi::PBR_COMPACT_BYTES / 4>;
static_assert(sizeof(CompactPbrSource) == compactPbrSourceBytes);
static_assert(sizeof(vk::VertexFormat::PBRVertex) == abi::PBR_CANONICAL_BYTES);

inline uint32_t pbrSourceStride(int format) {
    if (format == legacyPbrSourceFormat) return abi::PBR_CANONICAL_BYTES;
    if (format == compactPbrSourceFormat) return compactPbrSourceBytes;
    throw std::invalid_argument("Unknown PBR source format");
}

inline uint32_t pbrSourceWords(uint32_t version, uint32_t declaredWords) {
    if (version == 0 && (declaredWords == 0 || declaredWords == abi::PBR_CANONICAL_BYTES / 4))
        return abi::PBR_CANONICAL_BYTES / 4;
    if (version == compactPbrSourceVersion && declaredWords == abi::PBR_COMPACT_BYTES / 4)
        return abi::PBR_COMPACT_BYTES / 4;
    throw std::invalid_argument("PBR source job version/stride mismatch");
}

// This encoder is a test/reference utility, never a per-frame 128 -> 100 repacking step.
inline CompactPbrSource encodeCompactPbr(const vk::VertexFormat::PBRVertex &v) {
    if (v.useNorm > 1 || v.useColorLayer > 3 || v.useTexture > 1 || v.useOverlay > 1 || v.useGlint > 3 ||
        v.useLight > 1 || v.coordinate > 255 || v.alphaMode > 255)
        throw std::invalid_argument("PBR discrete field exceeds compact v1 domain");
    CompactPbrSource out{};
    std::memcpy(out.data(), &v.pos, 12);
    std::memcpy(out.data() + abi::COMPACT_NORM_WORD, &v.norm, 12);
    std::memcpy(out.data() + abi::COMPACT_COLOR_LAYER_WORD, &v.colorLayer, 16);
    std::memcpy(out.data() + abi::COMPACT_TEXTURE_UV_WORD, &v.textureUV, 8);
    std::memcpy(out.data() + abi::COMPACT_OVERLAY_UV_WORD, &v.overlayUV, 8);
    out[abi::COMPACT_TEXTURE_ID_WORD] = v.textureID;
    std::memcpy(out.data() + abi::COMPACT_GLINT_UV_WORD, &v.glintUV, 8);
    out[abi::COMPACT_GLINT_TEXTURE_WORD] = v.glintTexture;
    std::memcpy(out.data() + abi::COMPACT_LIGHT_UV_WORD, &v.lightUV, 8);
    std::memcpy(out.data() + abi::COMPACT_ALBEDO_EMISSION_WORD, &v.albedoEmission, 4);
    std::memcpy(out.data() + abi::COMPACT_POST_BASE_WORD, &v.postBase, 12);
    out[abi::COMPACT_PACKED_MODES_WORD] =
        v.useNorm | (v.useColorLayer << abi::COMPACT_COLOR_SHIFT) | (v.useTexture << abi::COMPACT_TEXTURE_SHIFT) |
        (v.useOverlay << abi::COMPACT_OVERLAY_SHIFT) | (v.useGlint << abi::COMPACT_GLINT_SHIFT) |
        (v.useLight << abi::COMPACT_LIGHT_SHIFT) | (v.coordinate << abi::COMPACT_COORDINATE_SHIFT) |
        (v.alphaMode << abi::COMPACT_ALPHA_SHIFT);
    return out;
}

inline vk::VertexFormat::PBRVertex decodeCompactPbr(std::span<const std::byte> input) {
    if (input.size() != compactPbrSourceBytes) throw std::invalid_argument("Invalid compact PBR source length");
    CompactPbrSource words;
    std::memcpy(words.data(), input.data(), sizeof(words));
    const uint32_t flags = words[abi::COMPACT_PACKED_MODES_WORD];
    if (flags & 0xff000000u) throw std::invalid_argument("Unsupported compact PBR source flags");
    vk::VertexFormat::PBRVertex v{};
    std::memcpy(&v.pos, words.data(), 12);
    std::memcpy(&v.norm, words.data() + abi::COMPACT_NORM_WORD, 12);
    std::memcpy(&v.colorLayer, words.data() + abi::COMPACT_COLOR_LAYER_WORD, 16);
    std::memcpy(&v.textureUV, words.data() + abi::COMPACT_TEXTURE_UV_WORD, 8);
    std::memcpy(&v.overlayUV, words.data() + abi::COMPACT_OVERLAY_UV_WORD, 8);
    v.textureID = words[abi::COMPACT_TEXTURE_ID_WORD];
    std::memcpy(&v.glintUV, words.data() + abi::COMPACT_GLINT_UV_WORD, 8);
    v.glintTexture = words[abi::COMPACT_GLINT_TEXTURE_WORD];
    std::memcpy(&v.lightUV, words.data() + abi::COMPACT_LIGHT_UV_WORD, 8);
    std::memcpy(&v.albedoEmission, words.data() + abi::COMPACT_ALBEDO_EMISSION_WORD, 4);
    std::memcpy(&v.postBase, words.data() + abi::COMPACT_POST_BASE_WORD, 12);
    v.useNorm = flags & 1u;
    v.useColorLayer = (flags >> abi::COMPACT_COLOR_SHIFT) & 3u;
    v.useTexture = (flags >> abi::COMPACT_TEXTURE_SHIFT) & 1u;
    v.useOverlay = (flags >> abi::COMPACT_OVERLAY_SHIFT) & 1u;
    v.useGlint = (flags >> abi::COMPACT_GLINT_SHIFT) & 3u;
    v.useLight = (flags >> abi::COMPACT_LIGHT_SHIFT) & 1u;
    v.coordinate = (flags >> abi::COMPACT_COORDINATE_SHIFT) & 255u;
    v.alphaMode = (flags >> abi::COMPACT_ALPHA_SHIFT) & 255u;
    return v;
}
} // namespace mcvr
