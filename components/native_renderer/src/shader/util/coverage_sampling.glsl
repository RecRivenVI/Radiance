#ifndef COVERAGE_SAMPLING_GLSL
#define COVERAGE_SAMPLING_GLSL

#include "util/scene_sample_key.glsl"

// FidelityFX SSSR's existing 128x128 scrambling tile and 256-sample Sobol data.
// The same immutable tables are shared by all frames; layers get independent
// tile translations/sample permutations so stacked alpha is not correlated.
layout(set = 1, binding = 13, std430) readonly buffer CoverageSobol { uint values[]; } coverageSobol;
layout(set = 1, binding = 14, std430) readonly buffer CoverageScrambling { uint values[]; } coverageScrambling;

float coverageSample(uint geometry, uint primitive, uint bounce) {
    uint layerSeed = sceneSamplingGeometry(geometry) ^ (primitive * 0x9E3779B9u) ^ ubo.samplingSeed ^ (bounce * 0x85EBCA6Bu);
    uint layer = pcg(layerSeed);
    uvec2 pixel = (gl_LaunchIDEXT.xy + uvec2(layer, layer >> 7u)) & 127u;
    uint dimension = bounce & 7u;
    uint sampleIndex = (ubo.samplingFrameIndex ^ (layer >> 14u)) & 255u;
    uint value = coverageSobol.values[sampleIndex * 256u + dimension] ^
        coverageScrambling.values[(pixel.x + pixel.y * 128u) * 8u + dimension];
    // Preserve arbitrary alpha probabilities instead of quantizing them to 1/256.
    uint fine = xxhash32(uvec3(gl_LaunchIDEXT.xy, layer ^ ubo.samplingFrameIndex)) & 65535u;
    return float((value << 16u) | fine) * (1.0 / 16777216.0);
}

#endif
