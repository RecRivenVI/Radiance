#ifndef RAY_GLSL
#define RAY_GLSL

#include "util/ray_payloads.glsl"

#if defined(VPT_MATERIAL_STATE_BINDING)
layout(set = 5, binding = VPT_MATERIAL_STATE_BINDING, rgba16f) uniform image2DArray rayMaterialStateImage;
#elif defined(ADV_MATERIAL_STATE_BINDING)
layout(set = 5, binding = ADV_MATERIAL_STATE_BINDING, rgba16f) uniform image2DArray rayMaterialStateImage;
#else
layout(set = 5, binding = 7, rgba16f) uniform image2DArray rayMaterialStateImage;
#endif

#include "util/ui_primary_visibility.glsl"
#include "common/query_policy.hpp"
#include "util/ray_origin.glsl"
#include "util/coverage_blend.glsl"

const uint rayBounceMask = 0xFFu;
const uint rayStopBit = 1u << 9u;
const uint rayContinueBit = 1u << 10u;
const uint rayNoisyBit = 1u << 11u;
const uint rayLobeShift = 12u;
const uint rayLobeMask = 0x3u << rayLobeShift;
const uint raySkipFogBit = 1u << 14u;
const uint rayCaptureSurfaceBit = 1u << 16u;
const uint raySurfaceCacheWrittenBit = 1u << 17u;
const uint raySurfaceCacheTargetSecondaryBit = 1u << 18u;
const uint rayIndirectVolumetricCloudBit = 1u << 19u;
const uint rayDirectImageBit = 1u << 21u;
const uint rayPriorityCaptureBit = 1u << 22u;
const uint rayPriorityShadeBit = 1u << 23u;
const uint rayCoverageCaptureBit = 1u << 24u;
const uint rayCoverageHitBit = 1u << 25u;
bool rayCoverageCapture(MainRay ray) {
    return RADIANCE_COVERAGE_MODE == 1u && (ray.stateBits & (rayCoverageCaptureBit | rayDirectImageBit)) ==
        (rayCoverageCaptureBit | rayDirectImageBit);
}
void raySetCoverageCapture(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? ray.stateBits | rayCoverageCaptureBit : ray.stateBits & ~rayCoverageCaptureBit;
}
bool rayPrioritySelection(MainRay ray) {
    return (ray.stateBits & (rayPriorityCaptureBit | rayPriorityShadeBit)) != 0u;
}
bool rayPriorityLayerAllowed(MainRay ray) {
#ifdef MCVR_PRIORITY_TARGET
#ifndef MCVR_PRIORITY_LAYER
#define MCVR_PRIORITY_LAYER 1
#endif
    return !rayPrioritySelection(ray) || MCVR_PRIORITY_LAYER == MCVR_PRIORITY_TARGET;
#else
    return true;
#endif
}

bool rayDirectImage(MainRay ray) { return (ray.stateBits & rayDirectImageBit) != 0u; }
void raySetDirectImage(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? ray.stateBits | rayDirectImageBit : ray.stateBits & ~rayDirectImageBit;
}
uint rayQueryPurpose(MainRay ray) { return rayDirectImage(ray) ? queryDirect : queryTransport; }
bool rayShouldContinue(MainRay ray);
uint rayLobeType(MainRay ray);
void rayAdvanceQuery(inout MainRay ray) {
    if (!rayShouldContinue(ray))
        raySetDirectImage(ray, advanceQuery(rayQueryPurpose(ray), rayLobeType(ray)) == queryDirect);
}
uint queryTraversalFlags(uint purpose, uint flags) {
    return purpose == queryTransport ?
        flags & ~(gl_RayFlagsCullBackFacingTrianglesEXT | gl_RayFlagsCullFrontFacingTrianglesEXT) : flags;
}

ivec3 rayMaterialStateCoord(int layer) {
    return ivec3(ivec2(gl_LaunchIDEXT.xy), layer);
}

void raySetBounce(inout MainRay ray, uint bounce) {
    ray.stateBits = (ray.stateBits & ~rayBounceMask) | (bounce & rayBounceMask);
}

uint rayBounce(MainRay ray) {
    return ray.stateBits & rayBounceMask;
}

void raySetStop(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? (ray.stateBits | rayStopBit) : (ray.stateBits & ~rayStopBit);
}

bool rayShouldStop(MainRay ray) {
    return (ray.stateBits & rayStopBit) != 0u;
}

void raySetContinue(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? (ray.stateBits | rayContinueBit) : (ray.stateBits & ~rayContinueBit);
}

bool rayShouldContinue(MainRay ray) {
    return (ray.stateBits & rayContinueBit) != 0u;
}

void raySetNoisy(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? (ray.stateBits | rayNoisyBit) : (ray.stateBits & ~rayNoisyBit);
}

bool rayIsNoisy(MainRay ray) {
    return (ray.stateBits & rayNoisyBit) != 0u;
}

void raySetSkipFog(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? (ray.stateBits | raySkipFogBit) : (ray.stateBits & ~raySkipFogBit);
}

bool raySkipFog(MainRay ray) {
    return (ray.stateBits & raySkipFogBit) != 0u;
}

void raySetCaptureSurface(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? (ray.stateBits | rayCaptureSurfaceBit) : (ray.stateBits & ~rayCaptureSurfaceBit);
}

bool rayCaptureSurface(MainRay ray) {
    return (ray.stateBits & rayCaptureSurfaceBit) != 0u;
}

void raySetSurfaceCacheWritten(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? (ray.stateBits | raySurfaceCacheWrittenBit) :
                              (ray.stateBits & ~raySurfaceCacheWrittenBit);
}

bool raySurfaceCacheWritten(MainRay ray) {
    return (ray.stateBits & raySurfaceCacheWrittenBit) != 0u;
}

void raySetSurfaceCacheTargetSecondary(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? (ray.stateBits | raySurfaceCacheTargetSecondaryBit) :
                              (ray.stateBits & ~raySurfaceCacheTargetSecondaryBit);
}

bool raySurfaceCacheTargetSecondary(MainRay ray) {
    return (ray.stateBits & raySurfaceCacheTargetSecondaryBit) != 0u;
}

void raySetIndirectVolumetricCloud(inout MainRay ray, bool enabled) {
    ray.stateBits = enabled ? (ray.stateBits | rayIndirectVolumetricCloudBit) :
                              (ray.stateBits & ~rayIndirectVolumetricCloudBit);
}

bool rayUseIndirectVolumetricCloud(MainRay ray) {
    return (ray.stateBits & rayIndirectVolumetricCloudBit) != 0u;
}

void raySetLobeType(inout MainRay ray, uint lobeType) {
    ray.stateBits = (ray.stateBits & ~rayLobeMask) | ((lobeType & 0x3u) << rayLobeShift);
}

uint rayLobeType(MainRay ray) {
    return (ray.stateBits & rayLobeMask) >> rayLobeShift;
}

void rayClearMaterial(inout MainRay ray) {
    imageStore(rayMaterialStateImage, rayMaterialStateCoord(0), vec4(0.0));
    imageStore(rayMaterialStateImage, rayMaterialStateCoord(1), vec4(0.0));
    imageStore(rayMaterialStateImage, rayMaterialStateCoord(2), vec4(0.0));
}

void rayStoreMaterial(inout MainRay ray,
                       vec4 albedoValue,
                       vec3 f0,
                       float roughness,
                       float metallic,
                       float transmission,
                       float ior,
                       float emission) {
    imageStore(rayMaterialStateImage, rayMaterialStateCoord(0), albedoValue);
    imageStore(rayMaterialStateImage, rayMaterialStateCoord(1), vec4(f0, roughness));
    imageStore(rayMaterialStateImage, rayMaterialStateCoord(2), vec4(metallic, transmission, ior, emission));
}

void rayStoreAux(inout MainRay ray, vec2 aux) {
    ray.pad0 = packHalf2x16(aux);
}

// Reuse only the material scratch alpha. Shading keeps the accepted surface opaque;
// the ray generator reads coverage separately before tracing another branch.
void rayStoreCoverage(inout MainRay ray, float coverage, bool coverageSurface) {
    ray.stateBits &= ~rayCoverageHitBit;
    if (!rayCoverageCapture(ray) || !coverageSurface) return;
    memoryBarrierImage();
    vec4 albedo = imageLoad(rayMaterialStateImage, rayMaterialStateCoord(0));
    albedo.a = coverage;
    imageStore(rayMaterialStateImage, rayMaterialStateCoord(0), albedo);
    ray.stateBits |= rayCoverageHitBit;
}

float rayLoadCoverage(MainRay ray) {
    if ((ray.stateBits & rayCoverageHitBit) == 0u) return 1.0;
    memoryBarrierImage();
    return clamp(imageLoad(rayMaterialStateImage, rayMaterialStateCoord(0)).a, 0.0, 1.0);
}

vec2 rayLoadAux(MainRay ray) {
    return unpackHalf2x16(ray.pad0);
}

MaterialInfo rayLoadMaterial(MainRay ray) {
    MaterialInfo mat;
    memoryBarrierImage();
    vec4 albedoValue = imageLoad(rayMaterialStateImage, rayMaterialStateCoord(0));
    vec4 f0Roughness = imageLoad(rayMaterialStateImage, rayMaterialStateCoord(1));
    vec4 materialValues = imageLoad(rayMaterialStateImage, rayMaterialStateCoord(2));

    mat.albedoValue = albedoValue;
    if ((ray.stateBits & rayCoverageHitBit) != 0u) mat.albedoValue.a = 1.0;
    mat.f0 = f0Roughness.rgb;
    mat.roughness = f0Roughness.a;
    mat.metallic = materialValues.x;
    mat.transmission = materialValues.y;
    mat.ior = materialValues.z;
    mat.emission = materialValues.w;
    return mat;
}

#endif
