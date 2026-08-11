#version 460
#extension GL_EXT_ray_tracing : require
#extension GL_GOOGLE_include_directive : require
#if RADIANCE_SHARC_HIT_QUERY
#extension GL_EXT_control_flow_attributes : require
#extension GL_EXT_buffer_reference_uvec2 : require
#extension GL_EXT_shader_atomic_int64 : require
#extension GL_EXT_shader_explicit_arithmetic_types_float16 : require
#extension GL_EXT_shader_16bit_storage : enable
#define USE_SHARC 1
#define SHARC_QUERY 1
#endif
#extension GL_EXT_nonuniform_qualifier : require
#extension GL_EXT_buffer_reference2 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

#include "util/surface_overlay.glsl"
#include "common/shared.hpp"
#include "common/chunk_lookup.glsl"
#include "util/disney.glsl"
#include "util/dielectric.glsl"
#include "util/water_material.glsl"
#include "util/alpha_mode.glsl"
#include "common/fft_water.glsl"
#include "util/height_map.glsl"
#include "common/parallax_trace.glsl"
#include "util/random.glsl"
#include "util/ray_cone.glsl"
#include "util/ray.glsl"
#include "util/sharc_query.glsl"
#include "util/material_faces.glsl"
#include "util/sampling_helpers.glsl"
#include "util/util.glsl"
#include "util/text_mode.glsl"

layout(set = 0, binding = 0) uniform sampler2D textures[];

#include "util/emissive_overlay.glsl"
layout(set = 5, binding = 0) uniform sampler2D transLUT;
layout(set = 5, binding = 2) uniform samplerCube skyFull;

layout(set = 1, binding = 0) uniform accelerationStructureEXT topLevelAS;

layout(set = 1, binding = 1) readonly buffer BLASOffsets {
    uint offsets[];
}
blasOffsets;

layout(set = 1, binding = 2) readonly buffer IndexBufferAddr {
    uint64_t addrs[];
}
indexBufferAddrs;

layout(set = 1, binding = 3) readonly buffer LastIndexBufferAddr {
    uint64_t addrs[];
}
lastIndexBufferAddrs;

layout(set = 1, binding = 8) readonly buffer LastObjToWorldMat {
    mat4 mat[];
}
lastObjToWorldMats;

layout(set = 1, binding = 7) readonly buffer TextureMappingBuffer {
    TextureMapping mapping;
};

layout(set = 1, binding = 6) readonly buffer LastPositionBufferAddr {
    uint64_t addrs[];
}
lastPositionBufferAddrs;

layout(set = 2, binding = 0) uniform WorldUniform {
    WorldUBO worldUBO;
};

layout(set = 2, binding = 2) uniform SkyUniform {
    SkyUBO skyUBO;
};

#include "common/volumetric_cloud.glsl"

layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer IndexBuffer {
    uint indices[];
}
indexBuffer;

#include "util/vertex.glsl"
#include "util/source_material_maps.glsl"
#include "util/source_radiance.glsl"
#include "util/surface_layers.glsl"
#include "util/geometry_backface_debug.glsl"
#include "util/priority_surface.glsl"
#include "util/glint_material.glsl"
#include "common/constants.glsl"
#include "common/parallax_condition.glsl"

layout(location = 0) rayPayloadInEXT MainRay mainRay;
layout(location = 1) rayPayloadEXT ShadowRay shadowRay;
hitAttributeEXT vec2 attribs;

struct SampledSurface {
    float coverageAlpha;
    vec2 uv;
    float depth;
    bool edgeWall;
    vec3 worldPos;
    vec3 geometricNormal;
    vec3 shadingNormal;
    vec4 albedoValue;
    vec4 specularValue;
    vec4 normalValue;
    vec3 tint;
    vec3 glintRadiance;
    LabPBRMat mat;
};

bool isFiniteFloat(float value) {
    return !isnan(value) && !isinf(value);
}

bool isFiniteVec2(vec2 value) {
    return !any(isnan(value)) && !any(isinf(value));
}

bool isFiniteVec3(vec3 value) {
    return !any(isnan(value)) && !any(isinf(value));
}

bool isValidSampledSurface(SampledSurface surface) {
    return isFiniteVec2(surface.uv) && isFiniteFloat(surface.depth) && isFiniteVec3(surface.worldPos) &&
           isFiniteVec3(surface.geometricNormal) && isFiniteVec3(surface.shadingNormal) &&
           isFiniteFloat(surface.mat.transmission) && isFiniteFloat(surface.mat.roughness) &&
           isFiniteFloat(surface.mat.ior) && isFiniteFloat(surface.mat.emission) &&
           dot(surface.geometricNormal, surface.geometricNormal) > 1e-10 &&
           dot(surface.shadingNormal, surface.shadingNormal) > 1e-10;
}

bool directLightSurfaceEligible(SampledSurface surface) {
    return isValidSampledSurface(surface) &&
           surface.mat.emission <= ADV_DIRECT_LIGHT_EMISSIVE_SURFACE_EPSILON &&
           surface.mat.roughness >= ADV_DIRECT_LIGHT_MIN_SURFACE_ROUGHNESS;
}

void buildSurfaceBasis(vec3 dPdu,
                       vec3 dPdv,
                       vec3 geometricNormal,
                       out vec3 tangent,
                       out vec3 bitangent) {
    tangent = normalizeF(dPdu - geometricNormal * dot(geometricNormal, dPdu),
                         abs(geometricNormal.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0));
    bitangent = normalizeF(cross(geometricNormal, tangent), dPdv);
    tangent = normalizeF(cross(bitangent, geometricNormal), tangent);
}

#include "util/surface_geometry.glsl"

#include "util/colour_field.glsl"
void sampleSurfaceState(MaterialVertex source, bool useTexture,
                        uint textureID,
                        TextureMapEntry textureMap,
                        vec2 atlasUvMin,
                        vec2 atlasUvMax,
                        vec2 uv,
                        float lod,
                        uint alphaMode,
                        vec4 drawColour,
                        uint sourceAlphaRule,
                        bool colorLayerMix,
                        vec4 colorLayerValue,
                        vec3 colorLayer,
                        vec3 glint,
                        bool useOverlay,
                        ivec2 overlayUV,
                        vec3 dPduWorld,
                        vec3 dPdvWorld,
                        vec3 baseGeoNormal,
                        vec3 sourceShadingNormal,
                        bool useSourceShadingNormal,
                        bool hasHeightMap,
                        float maxDepthWorld,
                        HeightMapHit localHit,
                        vec3 worldPos,
                        vec3 viewDir,
                        bool isWaterMaterial,
                        bool isFftWaterSurface,
                        out SampledSurface surface) {
    surface.coverageAlpha = isCoverageAlphaMode(alphaMode) ?
        resolveSourceAlpha(1.0, colorLayerMix ? 1.0 : colorLayerValue.a, drawColour.a, alphaMode, sourceAlphaRule) : 1.0;
    vec4 albedoValue = vec4(1.0);
    vec4 specularValue = vec4(0.0);
    vec4 normalValue = vec4(0.5, 0.5, 1.0, 0.0);
    bool useFlatEdgeBand = false;
    bool textSurface = isTextMode(alphaMode);
    bool usePbr = !textSurface && !isLodMaterial(source);
    if (useTexture) {
        textureMap = sourceMaterialMaps(textureMap, uv, lod);
        albedoValue = sampleMaterialTexture(source, textures[nonuniformEXT(textureID)], uv, lod, false);
        if (textSurface) {
            albedoValue = resolveTextTextureColor(albedoValue, true, alphaMode);
            albedoValue.a = 1.0;
        } else {
            albedoValue.a = resolveSourceAlpha(albedoValue.a, colorLayerMix ? 1.0 : colorLayerValue.a,
                drawColour.a, alphaMode, sourceAlphaRule);
            if (isCoverageAlphaMode(alphaMode)) surface.coverageAlpha = albedoValue.a;
            if (isCoverageAlphaMode(alphaMode) || isAdditiveAlphaMode(alphaMode)) {
                albedoValue.a = 1.0;
            }
        }
        specularValue = textureMap.specular >= 0 && usePbr ?
                            sampleTexture(textures[nonuniformEXT(textureMap.specular)], uv, lod, false) :
                            vec4(0.0);
        normalValue = textureMap.normal >= 0 && usePbr ?
                          samplePBRTexture(textures[nonuniformEXT(textureMap.normal)], uv, atlasUvMin, atlasUvMax, lod,
                                           ADV_PBR_SAMPLING_MODE) :
                          vec4(0.5, 0.5, 1.0, 0.0);
        if (hasHeightMap && textureMap.normal >= 0 && usePbr) {
            ivec2 heightMapSize = textureSize(textures[nonuniformEXT(textureMap.normal)], 0);
            useFlatEdgeBand = isEdgeUV(uv, atlasUvMin, atlasUvMax, heightMapSize);
        }
    }
    if (textSurface) {
        normalValue = vec4(0.5, 0.5, 1.0, 0.0);
    }

    vec4 overlayColour = useOverlay ? sampleTexture(textures[nonuniformEXT(worldUBO.overlayTextureID)], overlayUV, 0, false) : vec4(0.0);
    vec3 tint = applySurfaceMaterialColor(albedoValue.rgb, vec4(colorLayer, colorLayerValue.a),
        !textSurface && colorLayerMix, useOverlay, overlayColour, drawColour);

    tint = applyColourField(source, tint);
    albedoValue = vec4(tint, albedoValue.a);
    LabPBRMat mat = convertLabPBRMaterial(albedoValue, specularValue, normalValue,
                                          isTransmissionAlphaMode(alphaMode));
    // An absent specular map is not an authored fully rough glass material.
    // Preserve every supplied PBR map; the ordinary dielectric fallback is smooth glass.
    if (mat.transmission > EPS && textureMap.specular < 0) {
        mat.roughness = 0.01;
        mat.f0 = vec3(0.04);
        mat.ior = 1.5;
    }
    if (isWaterMaterial && !localHit.sideWall) applyWaterMaterial(albedoValue, tint, mat);
    vec3 glintRadiance = applyGlintMaterialLayer(mat, glint);

    vec3 geometricNormal = localHit.sideWall ? localHit.geometricNormal : baseGeoNormal;
    vec3 shadingNormal = localHit.sideWall ? geometricNormal : sourceShadingNormal;
    if (isFftWaterSurface && !localHit.sideWall) {
        vec3 absWorldPos = worldPos + vec3(worldUBO.cameraPos.xyz);
        vec3 waterCoordNormal = baseGeoNormal.y >= 0.0 ? baseGeoNormal : -baseGeoNormal;
        vec2 waterCoord = fftWaterSurfaceCoord(absWorldPos, dPduWorld, dPdvWorld, waterCoordNormal);
        FftWaterSample waterSample = sampleFftWater(waterCoord, worldUBO.gameTime);
        vec3 tangent, bitangent;
        fftWaterStableBasis(baseGeoNormal, tangent, bitangent);
        vec3 localWaterNormal = waterSample.localNormal;
        mat.roughness = clamp(0.005 + 0.018 * min(length(localWaterNormal.xy), 0.55), 0.005, 0.026);
        shadingNormal = applyNormalMapToBasis(localWaterNormal, tangent, bitangent, baseGeoNormal, geometricNormal, viewDir);
    } else {
        if (ADV_PBR_SAMPLING_MODE != 0u && hasHeightMap && !localHit.sideWall && textureMap.normal >= 0 &&
            usePbr &&
            !useFlatEdgeBand) {
            geometricNormal = sampleNormal(textures[nonuniformEXT(textureMap.normal)], uv, atlasUvMin, atlasUvMax,
                                           dPduWorld, dPdvWorld, baseGeoNormal, 0, ADV_PBR_SAMPLING_MODE,
                                           maxDepthWorld, viewDir);
        }

        if (!localHit.sideWall && usePbr) {
            if (!useSourceShadingNormal) shadingNormal = geometricNormal;
            else {
                if (dot(shadingNormal, geometricNormal) < 0.0) shadingNormal = -shadingNormal;
                if (dot(shadingNormal, geometricNormal) <= 1e-5) shadingNormal = geometricNormal;
            }
            vec3 tangent, bitangent;
            buildSurfaceBasis(dPduWorld, dPdvWorld, shadingNormal, tangent, bitangent);
            shadingNormal = applyNormalMapToBasis(mat.normal, tangent, bitangent, shadingNormal, geometricNormal, viewDir);
        }
    }

    surface.uv = uv;
    surface.depth = localHit.depth;
    surface.edgeWall = localHit.edgeWall;
    surface.worldPos = worldPos;
    surface.geometricNormal = geometricNormal;
    surface.shadingNormal = shadingNormal;
    surface.albedoValue = albedoValue;
    surface.specularValue = specularValue;
    surface.normalValue = normalValue;
    surface.tint = tint;
    surface.glintRadiance = glintRadiance;
    surface.mat = mat;
}



vec3 heightMapWorldPosAtUvDepth(vec2 uv,
                                float depth,
                                vec2 referenceUv,
                                vec3 referenceWorldPos,
                                vec3 dPduWorld,
                                vec3 dPdvWorld,
                                vec3 baseGeoNormal) {
    return basePlaneWorldPosAtUv(uv, referenceUv, referenceWorldPos, dPduWorld, dPdvWorld) -
           baseGeoNormal * depth;
}

bool traceLocalHeightIntersectionAndExit(int normalTextureID,
                                         vec2 atlasUvMin,
                                         vec2 atlasUvMax,
                                         vec3 dPduWorld,
                                         vec3 dPdvWorld,
                                         vec3 baseGeoNormal,
                                         float maxDepthWorld,
                                         SampledSurface surface,
                                         vec3 worldDir,
                                         int maxTraceSteps,
                                         out HeightMapHit hit,
                                         out vec2 exitUv,
                                         out float exitDepth,
                                         out float exitDistance) {
    initRestirParallaxMiss(surface.uv, surface.depth, baseGeoNormal, hit);
    exitUv = surface.uv;
    exitDepth = surface.depth;
    exitDistance = 0.0;
    if (normalTextureID < 0 || maxDepthWorld <= heightMapMinWorldDepth) { return false; }
    ivec2 heightMapSize = textureSize(textures[nonuniformEXT(normalTextureID)], 0);
    if (heightMapSize.x <= 0 || heightMapSize.y <= 0) { return false; }
    vec2 traceAtlasUvMin = heightMapInnerMinUV(atlasUvMin, atlasUvMax, heightMapSize);
    vec2 traceAtlasUvMax = heightMapInnerMaxUV(atlasUvMin, atlasUvMax, heightMapSize);

    float startBias = 2.0 * heightMapTraceBias;
    vec2 rateUV = directionToRateUv(worldDir, dPduWorld, dPdvWorld);
    float depthRate = dot(worldDir, -baseGeoNormal);
    vec2 uv = surface.uv + rateUV * startBias;
    float depth = surface.depth + depthRate * startBias;
    if (dot(surface.geometricNormal, baseGeoNormal) > 0.5 && depthRate < 0.0) {
        depth = min(depth, surface.depth - startBias);
    }

    bool fallbackToBilinear =
        ADV_PBR_SAMPLING_MODE == 0u && shouldFallbackNearestSecondaryToBilinear(worldDir, baseGeoNormal);
    bool localHit;
    if (depthRate < -1e-6) {
        float maxTraceDistance = max(depth / -depthRate, 0.0);
        localHit = fallbackToBilinear ?
                       traceBilinearHeightMap(textures[nonuniformEXT(normalTextureID)], traceAtlasUvMin, traceAtlasUvMax, uv,
                                              depth, worldDir, dPduWorld, dPdvWorld, baseGeoNormal, maxDepthWorld,
                                              maxTraceDistance, hit) :
                       traceHeightMap(textures[nonuniformEXT(normalTextureID)], traceAtlasUvMin, traceAtlasUvMax, uv, depth,
                                      worldDir, dPduWorld, dPdvWorld, baseGeoNormal, maxDepthWorld,
                                      maxTraceDistance, ADV_PBR_SAMPLING_MODE, hit);
    } else {
        localHit = fallbackToBilinear ?
                       traceRestirBilinearHeightMapCapped(textures[nonuniformEXT(normalTextureID)], traceAtlasUvMin,
                                                          traceAtlasUvMax, uv, depth, worldDir, dPduWorld, dPdvWorld,
                                                          baseGeoNormal, maxDepthWorld, maxTraceSteps, hit) :
                       traceRestirHeightMapCapped(textures[nonuniformEXT(normalTextureID)], traceAtlasUvMin, traceAtlasUvMax,
                                                  uv, depth, worldDir, dPduWorld, dPdvWorld, baseGeoNormal,
                                                  maxDepthWorld, ADV_PBR_SAMPLING_MODE, maxTraceSteps, hit);
    }
    if (localHit) { return true; }

    if (depthRate >= -1e-6) { return true; }

    float exitT = max(depth / -depthRate, 0.0);
    exitUv = clamp(uv + rateUV * exitT, traceAtlasUvMin, traceAtlasUvMax);
    exitDepth = 0.0;
    exitDistance = exitT;
    return false;
}

#include "util/lod_light.glsl"
vec3 sampleSurfaceDirectionalLight(SampledSurface surface,
                                   vec3 viewDir,
                                   vec2 referenceUv,
                                   vec3 referenceWorldPos,
                                   vec2 atlasUvMin,
                                   vec2 atlasUvMax,
                                   vec3 dPduWorld,
                                   vec3 dPdvWorld,
                                   vec3 baseGeoNormal,
                                   bool traceLocalHeight,
                                   int normalTextureID,
                                   float maxDepthWorld,
                                   bool isFftWaterSurface,
                                   bool hasWaterEntry) {
    if (worldUBO.skyType != 1) { return vec3(0.0); }
    if (!directLightSurfaceEligible(surface)) { return vec3(0.0); }

    bool isOpaqueSurface = surface.mat.transmission <= EPS;
    vec3 lightDir = celestialSunDirection();
    if (lightDir.y < 0.0) { lightDir = -lightDir; }

    vec3 sampledLightDir = SampleVMF(mainRay.seed, lightDir, 3000.0);
    if (!isFiniteVec3(sampledLightDir)) { return vec3(0.0); }
    float sampledLightNoL = dot(sampledLightDir, surface.geometricNormal);
    if (isOpaqueSurface && sampledLightNoL <= 0.0) { return vec3(0.0); }

    float lightPdf;
    vec3 lightBRDF = DisneyEval(surface.mat, viewDir, surface.shadingNormal, sampledLightDir, lightPdf);
    if (!isFiniteFloat(lightPdf) || !isFiniteVec3(lightBRDF) || lightPdf <= 1e-6) { return vec3(0.0); }

    vec2 shadowOriginUv = surface.uv;
    float shadowOriginDepth = surface.depth;
    float shadowOriginDistance = 0.0;
    vec3 shadowOriginPos = surface.worldPos;
    vec3 shadowOriginGeomNormal = surface.geometricNormal;
    if (traceLocalHeight) {
        HeightMapHit shadowSelfHit;
        vec2 exitUv;
        float exitDepth;
        float exitDistance;
        if (traceLocalHeightIntersectionAndExit(normalTextureID, atlasUvMin, atlasUvMax, dPduWorld, dPdvWorld,
                                                baseGeoNormal, maxDepthWorld, surface, sampledLightDir,
                                                ADV_PARALLAX_SECONDARY_MAX_STEPS, shadowSelfHit, exitUv,
                                                exitDepth, exitDistance)) {
            return vec3(0.0);
        }
        if (!surface.edgeWall) {
            shadowOriginUv = exitUv;
            shadowOriginDepth = exitDepth;
            shadowOriginDistance = exitDistance;
            shadowOriginPos =
                heightMapWorldPosAtUvDepth(shadowOriginUv, shadowOriginDepth, referenceUv, referenceWorldPos,
                                           dPduWorld, dPdvWorld, baseGeoNormal);
            shadowOriginGeomNormal = baseGeoNormal;
        }
    }

    shadowRay.radiance = vec3(0.0);
    shadowRay.throughput = vec3(1.0);
    shadowRay.queryPadding = 0u;
    shadowRay.pad0 = 0u;

    vec3 shadowOrigin = shadowOriginPos - sampledLightDir * 0.0002;
    if (!surface.edgeWall) {
        vec3 visibilityNormal = dot(sampledLightDir, shadowOriginGeomNormal) >= 0.0 ? shadowOriginGeomNormal :
                                                                                  -shadowOriginGeomNormal;
        shadowOrigin = shadowOriginPos + visibilityNormal * 0.0002;
    }
    float shadowLength = max(1000.0 - shadowOriginDistance, 0.0001);
    if (!isFiniteVec3(shadowOrigin) || !isFiniteFloat(shadowLength)) { return vec3(0.0); }
    uint shadowMask = receiverQueryMask(queryTransport, true, rayReceiveOnlyReceiver(mainRay)) & ~queryCloud;
    if (ADV_CLOUD_MODE != 2u) { shadowMask |= CLOUD_MASK; }
    traceRayEXT(topLevelAS, 0u, shadowMask, 0, 0, 0, shadowOrigin, 0.0001, sampledLightDir,
                shadowLength, 1);

    float progress = skyUBO.rainGradient;
    vec3 lightRadiance = shadowRay.radiance * mainRay.throughput * lightBRDF;
    if (ADV_CLOUD_MODE == 2u) {
        vec3 absoluteShadowOriginPos = shadowOriginPos + vec3(worldUBO.cameraPos.xyz);
        float cloudVisibility = volumetricCloudLightVisibility(absoluteShadowOriginPos, lightDir,
                                                               max(ADV_VOLUMETRIC_CLOUD_LIGHT_STEPS, 1), 0.175);
        lightRadiance *= cloudVisibility;
    }
    bool applyUnderwaterCaustic = !isFftWaterSurface && (skyUBO.cameraSubmersionType == 1 || hasWaterEntry);
    if (applyUnderwaterCaustic) {
        vec3 absWorldPos = surface.worldPos + vec3(worldUBO.cameraPos.xyz);
        float waterDepth = distance(surface.worldPos, mainRay.origin);
        lightRadiance *= sampleFftWaterCaustic(absWorldPos.xz, worldUBO.gameTime, sampledLightDir, waterDepth);
    }
    return mix(lightRadiance, vec3(0.0), progress);
}

#include "util/previous_scene_position.glsl"

void main() {
    uint instanceID = gl_InstanceCustomIndexEXT;
    uint geometryID = gl_GeometryIndexEXT;
    uint geometryBufferIndex = getGeometryBufferIndex(instanceID, geometryID);
    vec3 priorityBary = vec3(1.0-attribs.x-attribs.y, attribs.x, attribs.y);
    if (capturePrioritySurface(mainRay, geometryBufferIndex, gl_PrimitiveID, priorityBary, gl_HitTEXT)) {
        vec3 previous;
        if (loadPreviousScenePos(geometryBufferIndex, gl_PrimitiveID, priorityBary, previous)) {
            mainRay.prevScenePos = previous;
            mainRay.hasPrevScenePos = 1u;
        }
        return;
    }
    if (geometryBackfaceDebug(mainRay, instanceAppearances.values[geometryBufferIndex].materialFlags)) return;

    uint i0, i1, i2;
    MaterialVertex m0, m1, m2;
    PositionVertex p0, p1, p2;
    loadTriangle(geometryBufferIndex, gl_PrimitiveID, i0, i1, i2,
        p0, p1, p2, m0, m1, m2);
    if (!selectMainSurfaceLayer(mainRay,geometryBufferIndex,gl_PrimitiveID,
        vec3(1.0-attribs.x-attribs.y,attribs),gl_HitKindEXT==gl_HitKindFrontFacingTriangleEXT,
        gl_WorldRayOriginEXT,gl_WorldRayDirectionEXT,worldUBO.seed,gl_HitTEXT,
        mat3(gl_ObjectToWorldEXT),p0,p1,p2,m0,m1,m2)) { raySetStop(mainRay, true); return; }


    vec3 baryCoords = vec3(1.0 - (attribs.x + attribs.y), attribs.x, attribs.y);
    interpolateColourField(m0,m1,m2,baryCoords);
    vec3 planeHitWorldPos = gl_WorldRayOriginEXT + gl_WorldRayDirectionEXT * gl_HitTEXT;
    vec3 baseViewDir = -mainRay.direction;

    uint packedData = m0.packedData;
    bool useColorLayer = hasColorLayer(packedData);
    bool colorLayerMix = hasColorLayerMix(packedData);
    bool useTexture = hasTexture(packedData);
    bool useGlint = hasGlint(packedData);
    bool useOverlay = hasOverlay(packedData);
    vec4 colorLayerValue = useColorLayer ?
                               baryCoords.x * m0.colorLayer + baryCoords.y * m1.colorLayer + baryCoords.z * m2.colorLayer :
                               vec4(1.0);
    vec3 flywheelLocalPosition = baryCoords.x * p0.pos + baryCoords.y * p1.pos + baryCoords.z * p2.pos;
    vec3 flywheelLocalNormal = normalize(baryCoords.x * m0.norm + baryCoords.y * m1.norm
        + baryCoords.z * m2.norm);
    ivec2 flywheelLightUv = ivec2(round(baryCoords.x * vec2(m0.lightUV)
        + baryCoords.y * vec2(m1.lightUV) + baryCoords.z * vec2(m2.lightUV)));
    applyFlywheelFragmentLighting(geometryBufferIndex, flywheelLocalPosition,
        flywheelLocalNormal, colorLayerValue, flywheelLightUv);
    vec3 colorLayer = colorLayerValue.rgb;
    uint bounce = rayBounce(mainRay);

    float albedoEmission =
        baryCoords.x * m0.albedoEmission + baryCoords.y * m1.albedoEmission + baryCoords.z * m2.albedoEmission;
    uint textureID = m0.textureID;
    uint alphaMode = getAlphaMode(packedData);
    bool textSurface = isTextMode(alphaMode);
    uint coordinate = getCoordinate(packedData);
    raySetReceiveOnlyReceiver(mainRay, coordinate == 1u);

    vec2 textureUV = vec2(0.0);
    TextureMapEntry textureMap = TextureMapEntry(-1, -1, -1);
    vec2 atlasUvMin = vec2(0.0);
    vec2 atlasUvMax = vec2(0.0);
    float lod = 0.0;
    vec3 dposdu = vec3(1.0, 0.0, 0.0);
    vec3 dposdv = vec3(0.0, 1.0, 0.0);
    bool hasHeightMapSurface = false;
    float maxDepthWorld = 0.0;
    mat3 objectToWorld = mat3(gl_ObjectToWorldEXT);
    mat3 normalMatrix = mat3(gl_WorldToObject3x4EXT);
    vec3 baseGeoNormal =
        normalizeF(normalMatrix * cross(p1.pos - p0.pos, p2.pos - p0.pos), vec3(0.0, 1.0, 0.0));
    vec3 planeGeoNormal = baseGeoNormal;
    vec3 dPduWorld = objectToWorld * dposdu;
    vec3 dPdvWorld = objectToWorld * dposdv;
    bool isWaterMaterial = materialIsWater(m0);
    bool hasFftWaterSurface = false;
    bool parallaxEnabled = false;
    bool useRealisticWaterSurface = ADV_WATER_SURFACE_MODE == 1u;

    if (useTexture) {
        textureMap = mapping.entries[textureID];
        textureUV = materialTextureUv(m0, baryCoords.x * m0.textureUV + baryCoords.y * m1.textureUV + baryCoords.z * m2.textureUV);
        atlasUvMin = min(m0.textureUV, min(m1.textureUV, m2.textureUV));
        atlasUvMax = max(m0.textureUV, max(m1.textureUV, m2.textureUV));

        float coneRadiusWorld = mainRay.coneWidth + gl_HitTEXT * mainRay.coneSpread;
        computedposduDv(p0.pos, p1.pos, p2.pos, m0.textureUV, m1.textureUV, m2.textureUV, dposdu, dposdv);
        lod = lodWithObjectCone(textures[nonuniformEXT(textureID)], coneRadiusWorld, mat3(gl_ObjectToWorldEXT),
            p0.pos, p1.pos, p2.pos, m0.textureUV, m1.textureUV, m2.textureUV);
        dPduWorld = objectToWorld * dposdu;
        dPdvWorld = objectToWorld * dposdv;
        planeGeoNormal = normalizeF(cross(dPduWorld, dPdvWorld), baseGeoNormal);
        baseGeoNormal = planeGeoNormal;
        if (dot(baseGeoNormal, baseViewDir) < 0.0) { baseGeoNormal = -baseGeoNormal; }

        if (useRealisticWaterSurface && textureMap.flag >= 0) {
            ivec4 flags = ivec4(round(sampleTexture(textures[nonuniformEXT(textureMap.flag)], textureUV, ceil(lod), false) * 255.0));
            isWaterMaterial = isWaterMaterial || (flags.r & 0x1) > 0;
        }
        hasFftWaterSurface = (useRealisticWaterSurface && isWaterMaterial && abs(planeGeoNormal.y) > 0.75);

        textureMap = sourceMaterialMaps(textureMap, textureUV, lod);

        if (parallaxEnabled && textureMap.normal >= 0 && coordinate != 1u && !textSurface) {
            maxDepthWorld = heightMapMaxDepthWorld(atlasUvMin, atlasUvMax, dPduWorld, dPdvWorld);
            hasHeightMapSurface =
                maxDepthWorld > heightMapMinWorldDepth && dot(baseViewDir, baseGeoNormal) > ADV_PARALLAX_MIN_VIEW_DOT;
        }
    } else {
        dPduWorld = objectToWorld * dposdu;
        dPdvWorld = objectToWorld * dposdv;
    }
    if (dot(baseGeoNormal, baseViewDir) < 0.0) { baseGeoNormal = -baseGeoNormal; }
    vec3 sourceShadingNormal = vertexBrdfNormal(packedData, m0.norm, m1.norm, m2.norm,
                                               baryCoords, normalMatrix, baseGeoNormal);
    bool traceLocalHeight = hasHeightMapSurface && shouldTraceRestirParallax(lod, planeHitWorldPos) && !hasFftWaterSurface;

    HeightMapHit initialHit;
    initialHit.hit = false;
    initialHit.sideWall = false;
    initialHit.edgeWall = false;
    initialHit.t = 0.0;
    initialHit.uv = textureUV;
    initialHit.depth = 0.0;
    initialHit.geometricNormal = baseGeoNormal;
    if (traceLocalHeight) {
        HeightMapHit tracedInitialHit;
        if (traceRestirHeightMapCapped(textures[nonuniformEXT(textureMap.normal)], atlasUvMin, atlasUvMax, textureUV,
                                       0.0, gl_WorldRayDirectionEXT, dPduWorld, dPdvWorld, baseGeoNormal,
                                       maxDepthWorld, ADV_PBR_SAMPLING_MODE, ADV_PARALLAX_PRIMARY_MAX_STEPS,
                                       tracedInitialHit)) {
            initialHit = tracedInitialHit;
        }
    }

    vec3 hitWorldPos = planeHitWorldPos + gl_WorldRayDirectionEXT * initialHit.t;
    float actualHitT = gl_HitTEXT + initialHit.t;

    mainRay.hitT = actualHitT;
    mainRay.coneWidth += actualHitT * mainRay.coneSpread;
    mainRay.directLightRadiance = vec3(0.0);
    mainRay.hasPrevScenePos = 0u;
    if (bounce == 0u) {
        vec3 prevScenePos;
        if (loadPreviousScenePos(geometryBufferIndex, gl_PrimitiveID, baryCoords, prevScenePos)) {
            mainRay.prevScenePos = prevScenePos;
            mainRay.hasPrevScenePos = 1u;
        }
    }

    vec3 glint = vec3(0.0);
    if (useGlint) {
        vec2 glintUV = baryCoords.x * m0.glintUV + baryCoords.y * m1.glintUV + baryCoords.z * m2.glintUV;
        glintUV = transformGlintUv(worldUBO.textureMat, glintUV, m0.packedData);
        glint = sampleTexture(textures[nonuniformEXT(m0.glintTexture)], glintUV, false).rgb;
    }
    glint *= glint;

    vec3 viewDir = -gl_WorldRayDirectionEXT;

    SampledSurface surface;
    sampleSurfaceState(m0, useTexture, textureID, textureMap, atlasUvMin, atlasUvMax, initialHit.uv, lod, alphaMode, sourceDrawColour(geometryBufferIndex), getSourceAlphaRule(packedData),
                       colorLayerMix, colorLayerValue, colorLayer, glint, useOverlay, m0.overlayUV, dPduWorld, dPdvWorld,
                       baseGeoNormal, sourceShadingNormal, hasVertexShadingNormal(packedData),
                       hasHeightMapSurface, maxDepthWorld, initialHit, hitWorldPos, viewDir,
                       isWaterMaterial, hasFftWaterSurface, surface);

    bool incomingWaterEntry = rayLoadAux(mainRay).y > 0.5;
    mainRay.normal = surface.shadingNormal;
    rayStoreMaterial(mainRay, surface.albedoValue, surface.mat.f0, surface.mat.roughness, surface.mat.metallic,
                     surface.mat.transmission, surface.mat.ior, surface.mat.emission);
    rayStoreCoverage(mainRay, surface.coverageAlpha, isCoverageAlphaMode(alphaMode) && surface.mat.transmission <= EPS);
    rayStoreAux(mainRay, hasFftWaterSurface ? vec2(ADV_FFT_WATER_ORIGIN_BIAS, 1.0) : vec2(0.0));

    rayPrepareSurfaceMedium(mainRay, surface.mat.transmission, surface.mat.ior, surface.albedoValue,
        instanceAppearances.values[geometryBufferIndex].materialFlags,
        gl_HitKindEXT == gl_HitKindFrontFacingTriangleEXT,
        (isWaterMaterial || isLodMaterial(m0)) ? normalMatrix * (baryCoords.x * m0.norm + baryCoords.y * m1.norm + baryCoords.z * m2.norm) : vec3(0.0), actualHitT);

    if (finishCachedSurface(mainRay, surface.worldPos, worldUBO.cameraPos.xyz, surface.mat.transmission)) return;
    mainRay.scatterRoughness = surface.mat.roughness;

    SampledSurface currentSurface = surface;
    vec3 emissiveOverlay = sampleEmissiveOverlay(eyeOverlayTextureForQuery(m0.emissiveOverlayTextureID, instanceAppearances.values[geometryBufferIndex].materialFlags, gl_HitKindEXT == gl_HitKindFrontFacingTriangleEXT, rayDirectImage(mainRay)), initialHit.uv, lod, sourceEyeColour(geometryBufferIndex));
    vec3 currentViewDir = viewDir;
    bool storedLobeType = false;
    for (int localBounce = 0; localBounce < 1; ++localBounce) {
        vec3 emissionRadiance = materialEmissionRadiance(currentSurface.tint, currentSurface.mat.emission) * mainRay.throughput;
        emissionRadiance += currentSurface.tint * albedoEmission * mainRay.throughput * lodEmissionWeight(geometryBufferIndex,gl_PrimitiveID,currentSurface.worldPos);
        emissionRadiance += emissiveOverlay * mainRay.throughput;
        emissionRadiance += currentSurface.glintRadiance * mainRay.throughput;
        mainRay.radiance += emissionRadiance;
        mainRay.radiance += sampleLodLight(currentSurface.mat,currentSurface.worldPos,currentSurface.shadingNormal,currentViewDir);

        vec3 directLight =
            sampleSurfaceDirectionalLight(currentSurface, currentViewDir, textureUV, planeHitWorldPos, atlasUvMin,
                                          atlasUvMax, dPduWorld, dPdvWorld, baseGeoNormal,
                                          traceLocalHeight, textureMap.normal, maxDepthWorld,
                                          hasFftWaterSurface, incomingWaterEntry);
        if (localBounce == 0) { mainRay.directLightRadiance = directLight; }
        mainRay.radiance += directLight;

        // The caller has no remaining segment. Keep this surface's lighting but
        // avoid an unused BSDF draw, direction and medium-boundary continuation.
        if (rayTerminal(mainRay)) { raySetStop(mainRay, true); return; }

        bool isOpaqueSurface = currentSurface.mat.transmission <= EPS;
        vec3 sampleDir;
        float pdf;
        uint lobeType;
        vec3 bsdf;
        if (currentSurface.mat.transmission > EPS) {
            bsdf = sampleDielectric(currentSurface.mat, -currentViewDir, currentSurface.shadingNormal,
                mainRay.boundaryEta, mainRay.seed, sampleDir, pdf, lobeType);
        } else {
            bsdf = DisneySample(currentSurface.mat, currentViewDir, currentSurface.shadingNormal, sampleDir, pdf,
                                mainRay.seed, lobeType);
        }

        if (!storedLobeType) {
            raySetLobeType(mainRay, lobeType);
            storedLobeType = true;
        }
        raySetNoisy(mainRay, true);

        if (!isFiniteFloat(pdf) || !isFiniteVec3(sampleDir) || !isFiniteVec3(bsdf) || pdf <= 1e-6 ||
            max(bsdf.r, max(bsdf.g, bsdf.b)) <= 1e-6) {
            raySetStop(mainRay, true);
            return;
        }

        if (isOpaqueSurface && dot(sampleDir, currentSurface.geometricNormal) <= 0.0) {
            raySetStop(mainRay, true);
            return;
        }

        mainRay.lastScatterOrigin = currentSurface.worldPos;
        mainRay.previousBsdfPdf = currentSurface.mat.transmission > EPS ? -1.0 : pdf;
        mainRay.throughput *= bsdf / max(pdf, 1e-4);

        if (!traceLocalHeight) {
            if (hasFftWaterSurface) {
                vec3 exitNormal = dot(sampleDir, currentSurface.geometricNormal) >= 0.0 ? currentSurface.geometricNormal :
                                                                                           -currentSurface.geometricNormal;
                mainRay.origin = offsetQueryOrigin(planeHitWorldPos, baseGeoNormal, sampleDir);
            } else if (hasHeightMapSurface) {
                vec3 exitBasePos =
                    basePlaneWorldPosAtUv(currentSurface.uv, textureUV, planeHitWorldPos, dPduWorld, dPdvWorld);
                vec3 exitNormal = dot(sampleDir, baseGeoNormal) >= 0.0 ? baseGeoNormal : -baseGeoNormal;
                mainRay.origin = exitBasePos + exitNormal * 0.0002;
            } else {
                vec3 exitNormal = dot(sampleDir, currentSurface.geometricNormal) >= 0.0 ? currentSurface.geometricNormal :
                                                                                           -currentSurface.geometricNormal;
                mainRay.origin = currentSurface.worldPos + exitNormal * 0.0002;
            }
            mainRay.direction = sampleDir;
            raySetStop(mainRay, false);
            return;
        }

        HeightMapHit localBounceHit;
        vec2 exitUv;
        float exitDepth;
        float exitDistance;
        bool localBlocked = traceLocalHeightIntersectionAndExit(
            textureMap.normal, atlasUvMin, atlasUvMax, dPduWorld, dPdvWorld, baseGeoNormal, maxDepthWorld,
            currentSurface, sampleDir, ADV_PARALLAX_SECONDARY_MAX_STEPS, localBounceHit, exitUv, exitDepth,
            exitDistance);
        if (!localBlocked) {
            vec3 exitBasePos =
                heightMapWorldPosAtUvDepth(exitUv, exitDepth, textureUV, planeHitWorldPos, dPduWorld, dPdvWorld,
                                           baseGeoNormal);
            vec3 exitNormal = dot(sampleDir, baseGeoNormal) >= 0.0 ? baseGeoNormal : -baseGeoNormal;
            mainRay.origin = exitBasePos + exitNormal * 0.0002;
            mainRay.direction = sampleDir;
            raySetStop(mainRay, false);
            return;
        }

        if (!localBounceHit.hit || (localBounceHit.sideWall && localBounceHit.edgeWall)) {
            raySetStop(mainRay, true);
            return;
        }

        vec3 nextWorldPos = currentSurface.worldPos + sampleDir * localBounceHit.t;
        sampleSurfaceState(m0, useTexture, textureID, textureMap, atlasUvMin, atlasUvMax, localBounceHit.uv, lod, alphaMode, sourceDrawColour(geometryBufferIndex), getSourceAlphaRule(packedData),
                       colorLayerMix, colorLayerValue, colorLayer, glint, useOverlay, m0.overlayUV, dPduWorld,
                           dPdvWorld,
                           baseGeoNormal, sourceShadingNormal, hasVertexShadingNormal(packedData),
                           hasHeightMapSurface, maxDepthWorld, localBounceHit, nextWorldPos, -sampleDir,
                           isWaterMaterial, hasFftWaterSurface, currentSurface);
        currentViewDir = -sampleDir;
    }

    raySetStop(mainRay, true);
}
