#version 460
#extension GL_EXT_ray_tracing : require
#extension GL_GOOGLE_include_directive : require
#extension GL_EXT_nonuniform_qualifier : require
#extension GL_EXT_buffer_reference2 : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

#include "util/source_radiance.glsl"
#include "util/ray_cone.glsl"
#include "util/ray.glsl"
#include "util/alpha_mode.glsl"
#include "util/color_space.glsl"
#include "util/surface_overlay.glsl"
#include "common/shared.hpp"
#include "common/chunk_lookup.glsl"

layout(set = 0, binding = 0) uniform sampler2D textures[];

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

layout(set = 2, binding = 0) uniform WorldUniform {
    WorldUBO worldUBO;
};

layout(set = 1, binding = 6) readonly buffer LastPositionBufferAddr {
    uint64_t addrs[];
}
lastPositionBufferAddrs;

layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer IndexBuffer {
    uint indices[];
}
indexBuffer;

#include "util/vertex.glsl"
#include "util/surface_layers.glsl"
#include "util/geometry_backface_debug.glsl"

layout(location = 0) rayPayloadInEXT MainRay mainRay;
hitAttributeEXT vec2 attribs;

#include "util/previous_scene_position.glsl"

void main() {
    uint instanceID = gl_InstanceCustomIndexEXT;
    uint geometryID = gl_GeometryIndexEXT;
    uint geometryBufferIndex = getGeometryBufferIndex(instanceID, geometryID);
    if (geometryBackfaceDebug(mainRay, instanceAppearances.values[geometryBufferIndex].materialFlags)) return;

    uint i0, i1, i2;
    PositionVertex p0, p1, p2;
    MaterialVertex m0, m1, m2;
    loadTriangle(geometryBufferIndex, gl_PrimitiveID, i0, i1, i2, p0, p1, p2, m0, m1, m2);
    if (!selectMainSurfaceLayer(mainRay,geometryBufferIndex,gl_PrimitiveID,
        vec3(1.0-attribs.x-attribs.y,attribs),gl_HitKindEXT==gl_HitKindFrontFacingTriangleEXT,
        gl_WorldRayOriginEXT,gl_WorldRayDirectionEXT,worldUBO.seed,gl_HitTEXT,
        mat3(gl_ObjectToWorldEXT),p0,p1,p2,m0,m1,m2)) { raySetStop(mainRay, true); return; }


    vec3 bary = vec3(1.0 - (attribs.x + attribs.y), attribs.x, attribs.y);

    vec4 colorLayer = hasColorLayer(m0.packedData) ?
                          (bary.x * m0.colorLayer + bary.y * m1.colorLayer + bary.z * m2.colorLayer) :
                          vec4(1.0);
    vec3 flywheelLocalPosition = bary.x * p0.pos + bary.y * p1.pos + bary.z * p2.pos;
    vec3 flywheelLocalNormal = normalize(bary.x * m0.norm + bary.y * m1.norm + bary.z * m2.norm);
    ivec2 flywheelLightUv = ivec2(round(bary.x * vec2(m0.lightUV)
        + bary.y * vec2(m1.lightUV) + bary.z * vec2(m2.lightUV)));
    applyFlywheelFragmentLighting(geometryBufferIndex, flywheelLocalPosition,
        flywheelLocalNormal, colorLayer, flywheelLightUv);

    vec4 albedo = vec4(1.0);
    float pbrEmission = 0.0;
    if (hasTexture(m0.packedData)) {
        vec2 uv = materialTextureUv(m0, bary.x * m0.textureUV + bary.y * m1.textureUV + bary.z * m2.textureUV);
        albedo = sampleMaterialTexture(m0, textures[nonuniformEXT(m0.textureID)], uv, 0.0, false);

        int specularTextureID = mapping.entries[m0.textureID].specular;
        if (specularTextureID >= 0) {
            vec4 specular = sampleTexture(textures[nonuniformEXT(specularTextureID)], uv, 0.0, false);
            int intEmission = int(round(specular.a * 255.0));
            if (intEmission != 255) { pbrEmission = intEmission / 254.0; }
        }
    }

    float radianceLayeredAlpha = hasColorLayerMix(m0.packedData) ? albedo.a : albedo.a * colorLayer.a;
    bool useOverlay = hasOverlay(m0.packedData);
    vec4 overlayColor = vec4(0.0, 0.0, 0.0, 1.0);
    if (useOverlay) {
        overlayColor = sampleTexture(textures[nonuniformEXT(worldUBO.overlayTextureID)],
            m0.overlayUV, 0, false);
    }
    vec3 tint = applySurfaceMaterialColor(albedo.rgb, colorLayer,
        hasColorLayerMix(m0.packedData), useOverlay, overlayColor, sourceDrawColour(geometryBufferIndex));
    vec4 shaded = vec4(tint, radianceLayeredAlpha);
    uint alphaMode = getAlphaMode(m0.packedData);
    float alpha = resolveSourceAlpha(albedo.a, hasColorLayerMix(m0.packedData) ? 1.0 : colorLayer.a,
        sourceDrawColour(geometryBufferIndex).a, alphaMode, getSourceAlphaRule(m0.packedData));
    vec3 shadedRgb = clamp(shaded.rgb, vec3(0.0), vec3(1.0));
    float albedoEmission =
        bary.x * m0.albedoEmission + bary.y * m1.albedoEmission + bary.z * m2.albedoEmission;

    mainRay.radiance += materialEmissionRadiance(shadedRgb, pbrEmission) * alpha * mainRay.throughput;
    mainRay.radiance += shadedRgb * alpha * albedoEmission * mainRay.throughput;
    if (alphaMode == ALPHA_MODE_ADDITIVE) {
        mainRay.radiance += shadedRgb * mainRay.throughput;
    } else if (alphaMode == ALPHA_MODE_SURFACE_LIGHTNING) {
        mainRay.radiance += shadedRgb * alpha * mainRay.throughput;
    } else if (alphaMode == ALPHA_MODE_SURFACE_GLINT) {
        mainRay.radiance += shadedRgb * shadedRgb * mainRay.throughput;
    } else if (alphaMode == ALPHA_MODE_SURFACE_CRUMBLING) {
        mainRay.throughput *= 2.0 * shadedRgb;
    } else if (alphaMode == ALPHA_MODE_SURFACE_TRANSLUCENT) {
        mainRay.radiance += shadedRgb * alpha * mainRay.throughput;
        mainRay.throughput *= vec3(1.0 - alpha);
    } else if (alphaMode == ALPHA_MODE_ORDERED_OPAQUE) {
        // Unlit overlay stroke: write the overlay colour and stop so nothing bleeds through,
        // matching the vanilla opaque outline pass.
        mainRay.radiance += shadedRgb * mainRay.throughput;
        mainRay.throughput = vec3(0.0);
    } else {
        mainRay.throughput *= vec3(1.0 - alpha);
    }

    vec3 worldPos = gl_WorldRayOriginEXT + gl_WorldRayDirectionEXT * gl_HitTEXT;
    vec3 geoNormalObj = normalize(cross(p1.pos - p0.pos, p2.pos - p0.pos));
    mat3 normalMatrix = mat3(gl_WorldToObject3x4EXT);
    vec3 normal = normalize(normalMatrix * geoNormalObj);
    if (dot(normal, -mainRay.direction) < 0.0) { normal = -normal; }
    float defaultF0 = 0.02;
    float sqrtF0 = sqrt(defaultF0);
    float ior = (1.0 + sqrtF0) / max(1.0 - sqrtF0, 1e-6);
    float payloadTransmission = alpha < 0.999999 || isAdditiveAlphaMode(alphaMode) ? 1.0 : 0.0;

    mainRay.normal = normal;
    rayStoreMaterial(mainRay, vec4(shadedRgb, alpha), vec3(defaultF0), 1.0, 0.0, payloadTransmission, ior, 0.0);
    raySetLobeType(mainRay, 0u);
    raySetNoisy(mainRay, false);
    mainRay.directLightRadiance = vec3(0.0);
    mainRay.hasPrevScenePos = 0u;
    if (rayBounce(mainRay) == 0u) {
        vec3 prevScenePos;
        if (loadPreviousScenePos(geometryBufferIndex, gl_PrimitiveID, bary, prevScenePos)) {
            mainRay.prevScenePos = prevScenePos;
            mainRay.hasPrevScenePos = 1u;
        }
    }

    mainRay.origin = worldPos + mainRay.direction * 0.001;
    mainRay.hitT = gl_HitTEXT;
    mainRay.coneWidth += gl_HitTEXT * mainRay.coneSpread;
    raySetContinue(mainRay, true);
    raySetStop(mainRay, false);
}
