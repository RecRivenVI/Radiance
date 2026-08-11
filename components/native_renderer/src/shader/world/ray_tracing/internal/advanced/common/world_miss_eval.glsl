#include "common/shared.hpp"
#include "util/ray.glsl"
#include "util/util.glsl"

layout(set = 0, binding = 0) uniform sampler2D textures[];
layout(set = 5, binding = 0) uniform sampler2D transLUT;
layout(set = 5, binding = 2) uniform samplerCube skyFull;

layout(set = 2, binding = 0) uniform WorldUniform {
    WorldUBO worldUBO;
};

layout(set = 2, binding = 1) uniform LastWorldUniform {
    WorldUBO lastWorldUbo;
};

layout(set = 2, binding = 2) uniform SkyUniform {
    SkyUBO skyUBO;
};

layout(location = 0) rayPayloadInEXT MainRay mainRay;

#include "common/volumetric_cloud.glsl"
#include "util/celestial_profile.glsl"
#include "util/weather_optics.glsl"


uint starHash(uvec2 value) {
    uint h = value.x * 0x8da6b343u ^ value.y * 0xd8163841u ^ 0xcb1ab31fu;
    h ^= h >> 16u;
    h *= 0x7feb352du;
    h ^= h >> 15u;
    h *= 0x846ca68bu;
    return h ^ (h >> 16u);
}

vec3 evalPathTracedStars(vec3 rayDir) {
    const uvec2 gridSize = uvec2(512u, 256u);
    vec2 sphericalUv = vec2(atan(rayDir.z, rayDir.x) * INV_TWO_PI + 0.5,
                            asin(clamp(rayDir.y, -1.0, 1.0)) * INV_PI + 0.5);
    uvec2 cell = uvec2(floor(fract(sphericalUv) * vec2(gridSize)));
    uint h = starHash(cell);
    if ((h % 44u) != 0u) { return vec3(0.0); }

    vec2 jitter = vec2(float((h >> 8u) & 0xffffu), float((h >> 16u) & 0xffffu)) / 65535.0;
    vec2 centerUv = (vec2(cell) + 0.15 + jitter * 0.70) / vec2(gridSize);
    float longitude = (centerUv.x - 0.5) * TWO_PI;
    float latitude = (centerUv.y - 0.5) * PI;
    vec3 centerDir = vec3(cos(latitude) * cos(longitude), sin(latitude),
                          cos(latitude) * sin(longitude));
    float angularDistance = acos(clamp(dot(normalize(rayDir), centerDir), -1.0, 1.0));
    float radius = mix(0.00065, 0.00125, float(h & 0xffu) / 255.0);
    float coverage = 1.0 - smoothstep(radius * 0.35, radius, angularDistance);
    return vec3(coverage * 1.8);
}

void main() {
    mainRay.directLightRadiance.x = 1.0;

    vec3 rayDir = normalize(gl_WorldRayDirectionEXT);
    vec3 backgroundRadiance;
    float progress = clamp(skyUBO.rainGradient, 0.0, 1.0);

    backgroundRadiance = texture(skyFull, rayDir).rgb;

    if (worldUBO.skyType == 1) {
        float cameraHeight = max(float(worldUBO.cameraPos.y) + worldUBO.cameraViewMatInv[3].y, -69.0);
        vec3 pPlanet = vec3(0.0, ADV_ATMOSPHERE_RG + cameraHeight + 70.0, 0.0);
        float r = clamp(length(pPlanet), ADV_ATMOSPHERE_RG, ADV_ATMOSPHERE_RT);
        vec3 up = pPlanet / max(r, 1e-6);
        float mu = clamp(dot(up, rayDir), -1.0, 1.0);
        vec3 transmittance = sampleCloudAtmosphereTransmittance(r, celestialAtmosphereCosine(mu));

        for (int celestial = 0; celestial < 2; ++celestial) {
            bool sun = celestial == 0;
            float profilePdf;
            vec3 radiance = celestialTextureRadiance(rayDir, sun, profilePdf) * transmittance *
                weatherTransmittance(cameraHeight + 70.0, rayDir, progress);
            float competingPdf = celestialDirectionPdf(rayDir);
            float weight = mainRay.previousBsdfPdf > 0.0 ? celestialMis(mainRay.previousBsdfPdf, competingPdf) : 1.0;
            backgroundRadiance += radiance * weight;
        }

        backgroundRadiance += evalPathTracedStars(rayDir) * clamp(skyUBO.starBrightness, 0.0, 1.0);
    }

#if ADV_ALLOW_VOLUMETRIC_CLOUD_MISS
    if (worldUBO.skyType == 1 && ADV_CLOUD_MODE == 2u) {
        VolumetricCloudResult cloudResult =
            rayUseIndirectVolumetricCloud(mainRay) ?
                applyVolumetricCloudBudgeted(gl_WorldRayOriginEXT, rayDir, backgroundRadiance,
                                             ADV_INDIRECT_VOLUMETRIC_CLOUD_VIEW_STEPS,
                                             ADV_INDIRECT_VOLUMETRIC_CLOUD_LIGHT_STEPS,
                                             ADV_INDIRECT_VOLUMETRIC_CLOUD_AMBIENT_STEPS) :
                applyVolumetricCloud(gl_WorldRayOriginEXT, rayDir, backgroundRadiance);
        backgroundRadiance = cloudResult.color;
        vec3 clampedBackground = max(backgroundRadiance, vec3(0.0));
        vec3 cloudOnly = max(cloudResult.color - clampedBackground * cloudResult.transmittance, vec3(0.0));
        float backgroundLum = volumetricCloudLuminance(clampedBackground);
        float cloudLum = volumetricCloudLuminance(cloudOnly);
        float cloudPresence = cloudLum / max(backgroundLum + cloudLum, 1e-4);
        float starVisibility = min(cloudResult.transmittance, 1.0 - cloudPresence * 1.35);
        mainRay.directLightRadiance.x = cloudSaturate(starVisibility);
    }
#endif

    // A synthetic imaging boundary can lie inside a real dielectric. Keep the
    // Beer absorption of this final finite segment; fading does not turn it into air.
    if (rayDirectImage(mainRay) && worldUBO.uiPrimaryOwner == 0u &&
        worldUBO.fadeEnd > worldUBO.fadeStart && mainRay.mediumDepth > 0u)
        backgroundRadiance *= exp(-max(mainRay.currentMedium.xyz, vec3(0.0)) * max(gl_RayTmaxEXT, 0.0));
    mainRay.radiance += backgroundRadiance * mainRay.throughput;
    raySetStop(mainRay, true);
    mainRay.hitT = INF_DISTANCE;
}
