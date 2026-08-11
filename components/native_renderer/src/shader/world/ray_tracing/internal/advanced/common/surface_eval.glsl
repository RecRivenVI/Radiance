#include "util/colour_field.glsl"
#include "util/source_material_maps.glsl"
#include "util/water_material.glsl"
#include "util/surface_overlay.glsl"
#include "common/parallax_trace.glsl"
#include "common/constants.glsl"
#include "common/parallax_condition.glsl"
#include "util/glint_material.glsl"
#include "util/text_mode.glsl"

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
    vec3 emissiveOverlayRadiance;
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
    if (mat.transmission > EPS && textureMap.specular < 0) {
        mat.roughness = 0.01;
        mat.f0 = vec3(0.04);
        mat.ior = 1.5;
    }
    vec3 glintRadiance = applyGlintMaterialLayer(mat, glint);

    if (isWaterMaterial && !localHit.sideWall) {
        applyWaterMaterial(albedoValue, tint, mat);
    }
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
    surface.emissiveOverlayRadiance = vec3(0.0);
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

bool isFlaggedWaterSurface(TextureMapEntry textureMap, vec2 uv, float lod) {
    if (textureMap.flag < 0) { return false; }
    ivec4 flags = ivec4(round(sampleTexture(textures[nonuniformEXT(textureMap.flag)], uv, ceil(lod), false) * 255.0));
    return (flags.r & 0x1) > 0;
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
    ivec2 heightMapSize = textureSize(textures[nonuniformEXT(normalTextureID)], 0);
    if (normalTextureID < 0 || maxDepthWorld <= heightMapMinWorldDepth) { return false; }
    if (heightMapSize.x <= 0 || heightMapSize.y <= 0) { return false; }
    vec2 traceAtlasUvMin = heightMapInnerMinUV(atlasUvMin, atlasUvMax, heightMapSize);
    vec2 traceAtlasUvMax = heightMapInnerMaxUV(atlasUvMin, atlasUvMax, heightMapSize);
    vec2 traceSurfaceUv = surface.edgeWall ? clamp(surface.uv, traceAtlasUvMin, traceAtlasUvMax) : surface.uv;

    initRestirParallaxMiss(traceSurfaceUv, surface.depth, baseGeoNormal, hit);
    exitUv = traceSurfaceUv;
    exitDepth = surface.depth;
    exitDistance = 0.0;

    float startBias = 2.0 * heightMapTraceBias;
    vec2 rateUV = directionToRateUv(worldDir, dPduWorld, dPdvWorld);
    float depthRate = dot(worldDir, -baseGeoNormal);
    vec2 uv = traceSurfaceUv + rateUV * startBias;
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
