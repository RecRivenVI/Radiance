#version 460
#extension GL_EXT_ray_tracing : require
#extension GL_GOOGLE_include_directive : require

#include "util/ray.glsl"
#include "util/util.glsl"
#include "common/shared.hpp"

layout(set = 0, binding = 0) uniform sampler2D textures[];

layout(set = 5, binding = 0) uniform sampler2D transLUT;

layout(set = 2, binding = 0) uniform WorldUniform {
    WorldUBO worldUBO;
};

layout(set = 2, binding = 2) uniform SkyUniform {
    SkyUBO skyUBO;
};

layout(location = 1) rayPayloadInEXT ShadowRay shadowRay;

#include "common/celestial.glsl"
#include "util/celestial_profile.glsl"
#include "util/weather_optics.glsl"

vec2 transmittanceUv(float planetRadius, float viewCos) {
    float u = clamp(viewCos * 0.5 + 0.5, 0.0, 1.0);
    float v = clamp((planetRadius - ADV_ATMOSPHERE_RG) / (ADV_ATMOSPHERE_RT - ADV_ATMOSPHERE_RG), 0.0, 1.0);
    return vec2(u, v);
}

vec3 sampleTransmittance(float planetRadius, float viewCos) {
    vec2 uv = transmittanceUv(planetRadius, viewCos);
    return sampleTexture(transLUT, uv, false).rgb;
}

void main() {
    if (shadowRay.pad0 == 1u) {
        shadowRay.radiance += vec3(1.0) * shadowRay.throughput;
        return;
    }

    if (worldUBO.skyType != 1u) return;
    bool sun = celestialSunDirection().y >= 0.0;
    vec3 lightDirection = shadowRay.pad0==2u ? normalize(gl_WorldRayDirectionEXT) : (sun ? celestialSunDirection() : celestialMoonDirection());
    float height = max(float(worldUBO.cameraPos.y) + gl_WorldRayOriginEXT.y + 70.0, 1.0);
    float r = clamp(ADV_ATMOSPHERE_RG + height, ADV_ATMOSPHERE_RG, ADV_ATMOSPHERE_RT);
    vec3 transmittance = sampleTransmittance(r, celestialAtmosphereCosine(lightDirection.y)) *
        weatherTransmittance(height, lightDirection, skyUBO.rainGradient);
    vec3 radiance;
    if (shadowRay.pad0 == 2u) {
        float pdf;
        radiance = celestialTextureRadiance(normalize(gl_WorldRayDirectionEXT), sun, pdf);
    } else radiance = (sun ? ADV_SUN_RADIANCE : ADV_MOON_RADIANCE * skyUBO.celestialProfile.z) * weatherDirectStrength(skyUBO.rainGradient, skyUBO.thunderGradient);
    shadowRay.radiance += radiance * transmittance * shadowRay.throughput;
}
