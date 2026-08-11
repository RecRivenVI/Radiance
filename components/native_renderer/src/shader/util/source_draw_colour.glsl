#ifndef SOURCE_DRAW_COLOUR_GLSL
#define SOURCE_DRAW_COLOUR_GLSL
#include "common/render_abi.hpp"
vec4 resolveDrawColour(uint flags, vec4 colour) {
    return (flags & APPEARANCE_DRAW_COLOUR) != 0u ? colour : vec4(1.0);
}
vec4 resolveEyeColour(uint flags, vec4 colour) {
    return (flags & APPEARANCE_EYE_COLOUR) != 0u ? colour : vec4(1.0);
}
vec3 resolveEyeRadiance(vec4 overlay, vec4 drawColour) {
    return max(overlay.rgb * drawColour.rgb, vec3(0.0)) * clamp(overlay.a, 0.0, 1.0);
}
// The coating's facing is independent of its surviving base surface. Hardware front includes
// the model's optional FRONT canonicalization, which must be undone exactly once.
uint eyeOverlayTextureForQuery(uint packedTexture, uint baseFaces, bool hardwareFront, bool directImage) {
    bool sourceFront = hardwareFront != ((baseFaces & FACE_HARDWARE_FRONT_FLIP_BIT) != 0u);
    uint hidden = sourceFront ? EYE_CULL_FRONT_BIT : EYE_CULL_BACK_BIT;
    if (directImage && (packedTexture & hidden) != 0u) return 0u;
    return packedTexture & EYE_TEXTURE_MASK;
}
bool decodeSurfaceGeometryTag(float tag, uint recordCount, out uint geometryIndex) {
    geometryIndex = 0u;
    if (isnan(tag) || isinf(tag) || tag < 1.0 || tag > 16777216.0
        || tag != floor(tag) || tag > float(recordCount)) return false;
    geometryIndex = uint(tag) - 1u;
    return geometryIndex < recordCount;
}
#endif
