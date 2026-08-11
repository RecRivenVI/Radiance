#ifndef MATERIAL_DECODE_GLSL
#define MATERIAL_DECODE_GLSL
#include "common/render_abi.hpp"

bool hasColorLayer(uint packedData) {
    return (packedData & PACKED_COLOR_BIT) != 0u;
}

bool hasColorLayerMix(uint packedData) {
    return (packedData & PACKED_COLOR_MIX_BIT) != 0u;
}

bool hasTexture(uint packedData) {
    return (packedData & PACKED_TEXTURE_BIT) != 0u;
}

bool hasOverlay(uint packedData) {
    return (packedData & PACKED_OVERLAY_BIT) != 0u;
}

bool hasGlint(uint packedData) {
    return (packedData & PACKED_GLINT_BIT) != 0u;
}

uint getGlintMode(uint packedData) {
    return (packedData >> PACKED_GLINT_SHIFT) & PACKED_GLINT_MASK;
}

bool hasNorm(uint packedData) {
    return (packedData & PACKED_NORMAL_BIT) != 0u;
}

bool hasLight(uint packedData) {
    return (packedData & PACKED_LIGHT_BIT) != 0u;
}

bool hasNoHeightSurface(uint packedData) {
    return (packedData & PACKED_NO_HEIGHT_BIT) != 0u;
}

uint getAlphaMode(uint packedData) {
    return (packedData >> PACKED_ALPHA_SHIFT) & PACKED_ALPHA_MASK;
}

uint getCoordinate(uint packedData) {
    return (packedData >> PACKED_COORDINATE_SHIFT) & PACKED_COORDINATE_MASK;
}
#endif
