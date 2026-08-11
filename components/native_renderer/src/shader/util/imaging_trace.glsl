#ifndef IMAGING_TRACE_GLSL
#define IMAGING_TRACE_GLSL
#include "util/imaging_distance.glsl"
float imagingTraceLimit(MainRay ray, float transportLimit) {
    if (worldUBO.uiPrimaryOwner != 0u) return transportLimit;
    uint sampleBits = xxhash32(uvec3(gl_LaunchIDEXT.xy, worldUBO.seed ^ 0x714f2a91u));
    float sampleValue = float(sampleBits >> 8u) * (1.0 / 16777216.0);
    return imagingDistanceLimit(rayDirectImage(ray), ray.origin, ray.direction,
        worldUBO.cameraViewMatInv[3].xyz, worldUBO.fadeStart, worldUBO.fadeEnd,
        sampleValue, transportLimit);
}
#endif
