#ifndef EMISSIVE_OVERLAY_GLSL
#define EMISSIVE_OVERLAY_GLSL
#include "util/source_draw_colour.glsl"

vec3 sampleEmissiveOverlay(uint textureID, vec2 uv, float lod, vec4 drawColour) {
    if (textureID == 0u) { return vec3(0.0); }

    vec4 overlay = sampleTexture(textures[nonuniformEXT(textureID)], uv, lod, false);
    return resolveEyeRadiance(overlay, drawColour);
}

#endif
