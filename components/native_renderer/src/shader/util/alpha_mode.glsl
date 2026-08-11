#ifndef ALPHA_MODE_GLSL
#define ALPHA_MODE_GLSL
#include "common/render_abi.hpp"

const uint ALPHA_MODE_OPAQUE = ALPHA_OPAQUE;
const uint ALPHA_MODE_CUTOUT = ALPHA_CUTOUT;
const uint ALPHA_MODE_TRANSMISSION = ALPHA_TRANSMISSION;
const uint ALPHA_MODE_CUTOUT_EPSILON = ALPHA_CUTOUT_EPSILON;
const uint ALPHA_MODE_CUTOUT_LOW = ALPHA_CUTOUT_LOW;
const uint ALPHA_MODE_COVERAGE = ALPHA_COVERAGE;
const uint ALPHA_MODE_ADDITIVE = ALPHA_ADDITIVE;
// 12..19 are Radiance's text modes. Flywheel-only material modes use the
// remaining values in the packed five-bit alpha field.
const uint ALPHA_MODE_FLYWHEEL_LIGHTNING = ALPHA_FLYWHEEL_LIGHTNING;
const uint ALPHA_MODE_FLYWHEEL_GLINT = ALPHA_FLYWHEEL_GLINT;
const uint ALPHA_MODE_FLYWHEEL_CRUMBLING = ALPHA_FLYWHEEL_CRUMBLING;
const uint ALPHA_MODE_FLYWHEEL_TRANSLUCENT = ALPHA_FLYWHEEL_TRANSLUCENT;
// Unlit opaque overlay stroke (Catnip outline_solid): written by transparent_only.rchit and
// excluded from shadow/reflection rays by the primary-only instance mask.
const uint ALPHA_MODE_ORDERED_OPAQUE = ALPHA_ORDERED_OPAQUE;

// Kept as a source-level alias for external shader packs compiled against the old name.
const uint ALPHA_MODE_TRANSPARENT = ALPHA_MODE_TRANSMISSION;

const float CUTOUT_ALPHA_THRESHOLD = 0.5;
const float CUTOUT_LOW_ALPHA_THRESHOLD = 0.1;

bool isCutoutAlphaMode(uint alphaMode) {
    return alphaMode == ALPHA_MODE_CUTOUT || alphaMode == ALPHA_MODE_CUTOUT_LOW
        || alphaMode == ALPHA_MODE_CUTOUT_EPSILON;
}

bool isTransmissionAlphaMode(uint alphaMode) {
    return alphaMode == ALPHA_MODE_TRANSMISSION;
}

bool isCoverageAlphaMode(uint alphaMode) {
    return alphaMode == ALPHA_MODE_COVERAGE;
}

bool isAdditiveAlphaMode(uint alphaMode) {
    return alphaMode == ALPHA_MODE_ADDITIVE || alphaMode == ALPHA_MODE_FLYWHEEL_LIGHTNING
        || alphaMode == ALPHA_MODE_FLYWHEEL_GLINT;
}

bool isFlywheelLayerAlphaMode(uint alphaMode) {
    return alphaMode == ALPHA_MODE_ADDITIVE || (alphaMode >= ALPHA_MODE_FLYWHEEL_LIGHTNING
        && alphaMode <= ALPHA_MODE_FLYWHEEL_TRANSLUCENT);
}

float resolveSurfaceAlpha(float alpha, uint alphaMode) {
    alpha = clamp(alpha, 0.0, 1.0);

    if (alphaMode == ALPHA_MODE_OPAQUE) { return 1.0; }

    if (alphaMode == ALPHA_MODE_CUTOUT) {
        return alpha >= CUTOUT_ALPHA_THRESHOLD ? 1.0 : 0.0;
    }

    if (alphaMode == ALPHA_MODE_CUTOUT_EPSILON) {
        return alpha >= 0.01 ? 1.0 : 0.0;
    }

    if (alphaMode == ALPHA_MODE_CUTOUT_LOW) {
        return alpha >= CUTOUT_LOW_ALPHA_THRESHOLD ? 1.0 : 0.0;
    }

    return alpha;
}

uint getSourceAlphaRule(uint packedData) {
    return (packedData >> PACKED_ALPHA_RULE_SHIFT) & SOURCE_ALPHA_RULE_MASK;
}

float resolveSourceAlpha(float texelAlpha, float vertexAlpha, float globalAlpha, uint mode, uint rule) {
    float preGlobal = texelAlpha * vertexAlpha;
    float combined = preGlobal * globalAlpha;
    float tested = combined;
    float threshold = 0.0;
    if (rule == SOURCE_ALPHA_TEXEL_HALF) { tested = texelAlpha; threshold = 0.5; }
    if (rule == SOURCE_ALPHA_TEXEL_LOW) { tested = texelAlpha; threshold = 0.1; }
    if (rule == SOURCE_ALPHA_TEXEL_EPSILON) { tested = texelAlpha; threshold = 0.01; }
    if (rule == SOURCE_ALPHA_POST_HALF) threshold = 0.5;
    if (rule == SOURCE_ALPHA_POST_LOW) threshold = 0.1;
    if (rule == SOURCE_ALPHA_POST_EPSILON) threshold = 0.01;
    if (rule == SOURCE_ALPHA_PRE_GLOBAL_LOW) { tested = preGlobal; threshold = 0.1; }
    if (rule != 0u && tested < threshold) return 0.0;
    return resolveSurfaceAlpha(combined, mode);
}

#endif
