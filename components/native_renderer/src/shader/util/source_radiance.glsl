#ifndef RADIANCE_SOURCE_RADIANCE_GLSL
#define RADIANCE_SOURCE_RADIANCE_GLSL
// A material's radiance is independent of which path discovers it. Retain both
// configured gains, applying their product once to every PBR emission consumer.
float materialEmissionStrength() {
#if defined(ADV_DIRECT_LIGHT_STRENGTH) && defined(ADV_INDIRECT_LIGHT_STRENGTH)
    return max(0.0, float(ADV_DIRECT_LIGHT_STRENGTH) * float(ADV_INDIRECT_LIGHT_STRENGTH));
#elif defined(VPT_DIRECT_LIGHT_STRENGTH) && defined(VPT_INDIRECT_LIGHT_STRENGTH)
    return max(0.0, float(VPT_DIRECT_LIGHT_STRENGTH) * float(VPT_INDIRECT_LIGHT_STRENGTH));
#else
    return 1.0;
#endif
}
vec3 materialEmissionRadiance(vec3 tint, float emission) {
    return max(tint, vec3(0.0)) * max(emission, 0.0) * materialEmissionStrength();
}
#endif
