#ifndef COVERAGE_BLEND_GLSL
#define COVERAGE_BLEND_GLSL

#ifndef RADIANCE_COVERAGE_MODE
#define RADIANCE_COVERAGE_MODE 0
#endif

#ifndef RADIANCE_COVERAGE_CAPTURE
#define RADIANCE_COVERAGE_CAPTURE 1
#endif

// Radiance is linear HDR; these are coverage weights, not dielectric transmission.
void accumulateCoverage(inout vec3 radiance, inout float remaining, vec3 foreground, float alpha) {
    alpha = clamp(alpha, 0.0, 1.0);
    radiance += remaining * alpha * foreground;
    remaining *= 1.0 - alpha;
}

const int coverageDeterministicLayers = 8;

// Keep the filtered AOV sum equal to the unfiltered HDR foreground. In particular,
// emission must not also remain in the diffuse/specular continuation channel.
void coverageForegroundChannels(vec3 fullRadiance, vec3 direct, vec3 emission, bool diffuse, float alpha,
    out vec3 directChannel, out vec3 indirectChannel, out vec3 specularChannel, out vec3 emissionChannel) {
    directChannel = diffuse ? alpha * direct : vec3(0.0);
    indirectChannel = diffuse ? alpha * (fullRadiance - direct - emission) : vec3(0.0);
    specularChannel = diffuse ? vec3(0.0) : alpha * (fullRadiance - emission);
    emissionChannel = alpha * emission;
}

#endif
