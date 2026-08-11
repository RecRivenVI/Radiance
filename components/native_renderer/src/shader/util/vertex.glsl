#ifndef VERTEX_GLSL
#define VERTEX_GLSL
#include "common/render_abi.hpp"
#extension GL_EXT_nonuniform_qualifier : require

#include "common/shared.hpp"
#include "common/constants.glsl"
#include "alpha_mode.glsl"
#include "source_draw_colour.glsl"
#include "primitive_history.glsl"
#include "material_decode.glsl"
#include "vertex_shading_normal.glsl"

const uint USE_COLOR_LAYER_BIT = PACKED_COLOR_BIT;
const uint USE_TEXTURE_BIT = PACKED_TEXTURE_BIT;
const uint USE_OVERLAY_BIT = PACKED_OVERLAY_BIT;
const uint USE_GLINT_BIT = PACKED_GLINT_BIT;
const uint USE_NORM_BIT = PACKED_NORMAL_BIT;
const uint USE_LIGHT_BIT = PACKED_LIGHT_BIT;
const uint COLOR_LAYER_MIX_BIT = PACKED_COLOR_MIX_BIT;
const uint ALPHA_MODE_SHIFT = PACKED_ALPHA_SHIFT;
const uint ALPHA_MODE_MASK = PACKED_ALPHA_MASK;
const uint COORDINATE_SHIFT = PACKED_COORDINATE_SHIFT;
const uint NO_HEIGHT_SURFACE_BIT = PACKED_NO_HEIGHT_BIT;
const uint GLINT_MODE_SHIFT = PACKED_GLINT_SHIFT;
const uint GLINT_MODE_MASK = PACKED_GLINT_MASK;
const uint GLINT_MODE_ITEM = GLINT_ITEM;
const float ITEM_GLINT_UV_SCALE = 50.0;

layout(set = 1, binding = 10) readonly buffer InstanceAppearanceBuffer {
    InstanceAppearance values[];
}
instanceAppearances;


vec4 sourceDrawColour(uint geometryIndex) {
    if (geometryIndex >= uint(instanceAppearances.values.length())) return vec4(1.0);
    InstanceAppearance appearance = instanceAppearances.values[geometryIndex];
    return resolveDrawColour(appearance.flags, appearance.colorMultiply);
}


vec4 sourceEyeColour(uint geometryIndex) {
    if (geometryIndex >= uint(instanceAppearances.values.length())) return vec4(1.0);
    InstanceAppearance appearance = instanceAppearances.values[geometryIndex];
    return resolveEyeColour(appearance.flags,appearance.colorReplace);
}
#ifndef CONST_ONLY
layout(set = 1, binding = 4) readonly buffer PositionBufferAddr {
    uint64_t addrs[];
}
positionBufferAddrs;

layout(set = 1, binding = 5) readonly buffer MaterialBufferAddr {
    uint64_t addrs[];
}
materialBufferAddrs;



layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer PositionBuffer {
    PositionVertex vertices[];
}
positionBuffer;

layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer MaterialBuffer {
    MaterialVertex vertices[];
}
materialBuffer;

uint getGeometryBufferIndex(uint instanceID, uint geometryID) {
    return blasOffsets.offsets[instanceID] + geometryID;
}

const uint FLYWHEEL_MATERIAL_USE_LIGHT = FLYWHEEL_LIGHT_BIT;
const uint FLYWHEEL_MATERIAL_AO = FLYWHEEL_AO_BIT;
const uint FLYWHEEL_EMBEDDED = APPEARANCE_EMBEDDED;
const uint FLYWHEEL_CONSTANT_AMBIENT_LIGHT = APPEARANCE_CONSTANT_AMBIENT_LIGHT;
float flywheelCardinalLight(InstanceAppearance appearance, uint mode, vec3 normal) {
    if (mode == 0u) return 1.0;
    vec3 squared = normal * normal;
    if (mode == 1u) {
        if ((appearance.flags & FLYWHEEL_CONSTANT_AMBIENT_LIGHT) != 0u) {
            return min(squared.x * 0.6 + squared.y * 0.9 + squared.z * 0.8, 1.0);
        }
        return min(squared.x * 0.6 + squared.y * 0.25 * (3.0 + normal.y)
            + squared.z * 0.8, 1.0);
    }
    float light0 = max(0.0, dot(appearance.entityLight0.xyz, normal));
    float light1 = max(0.0, dot(appearance.entityLight1.xyz, normal));
    return min(1.0, (light0 + light1) * 0.6 + 0.4);
}

vec2 flywheelCrumblingUv(vec3 position, vec3 normal) {
    vec3 alignment = abs(normal);
    if (alignment.y >= alignment.x && alignment.y >= alignment.z)
        return normal.y < 0.0 ? vec2(position.x, -position.z) : position.xz;
    if (alignment.z >= alignment.x)
        return normal.z < 0.0 ? vec2(-position.x, -position.y)
            : vec2(position.x, -position.y);
    return normal.x < 0.0 ? vec2(-position.z, -position.y)
        : vec2(position.z, -position.y);
}













vec2 transformGlintUv(mat4 textureMat, vec2 uv, uint packedData) {
    // Vanilla uses setupGlintTexturing(8.0) for items and 0.16 for entities.
    // The UBO stores the entity matrix; this exact ratio recovers item glint.
    vec2 scaledUv = getGlintMode(packedData) == GLINT_MODE_ITEM
        ? uv * ITEM_GLINT_UV_SCALE
        : uv;
    return (textureMat * vec4(scaledUv, 0.0, 1.0)).xy;
}












void loadTriangleIndices(uint geometryBufferIndex, uint primitiveID, out uint i0, out uint i1, out uint i2) {
    IndexBuffer indexBuffer = IndexBuffer(indexBufferAddrs.addrs[geometryBufferIndex]);
    uint indexBaseID = 3u * primitiveID;
    i0 = indexBuffer.indices[indexBaseID];
    i1 = indexBuffer.indices[indexBaseID + 1u];
    i2 = indexBuffer.indices[indexBaseID + 2u];
}

void loadTrianglePositions(uint geometryBufferIndex,
                           uint i0,
                           uint i1,
                           uint i2,
                           out PositionVertex p0,
                           out PositionVertex p1,
                           out PositionVertex p2) {
    PositionBuffer positionBufferRef = PositionBuffer(positionBufferAddrs.addrs[geometryBufferIndex]);
    p0 = positionBufferRef.vertices[i0];
    p1 = positionBufferRef.vertices[i1];
    p2 = positionBufferRef.vertices[i2];
}

#include "util/voxy_material.glsl"

void loadTriangleMaterial(uint geometryBufferIndex,
                          uint i0,
                          uint i1,
                          uint i2,
                          out MaterialVertex m0,
                          out MaterialVertex m1,
                          out MaterialVertex m2) {
    if (isVoxyGeometry(geometryBufferIndex)) {
        m0 = voxyVertex(geometryBufferIndex, i0);
        m1 = voxyVertex(geometryBufferIndex, i1);
        m2 = voxyVertex(geometryBufferIndex, i2);
        return;
    }
    MaterialBuffer materialBufferRef = MaterialBuffer(materialBufferAddrs.addrs[geometryBufferIndex]);
    m0 = materialBufferRef.vertices[i0];
    m1 = materialBufferRef.vertices[i1];
    m2 = materialBufferRef.vertices[i2];
}

const uint FLYWHEEL_COLOR_MULTIPLY = APPEARANCE_COLOR_MULTIPLY;
const uint FLYWHEEL_COLOR_REPLACE = APPEARANCE_COLOR_REPLACE;
const uint FLYWHEEL_UV_OFFSET = APPEARANCE_UV_OFFSET;
const uint FLYWHEEL_FLUID_UV = APPEARANCE_FLUID_UV;
const uint FLYWHEEL_SHADOW_UV = APPEARANCE_SHADOW_UV;
const uint FLYWHEEL_OVERRIDE_OVERLAY = APPEARANCE_OVERRIDE_OVERLAY;
const uint FLYWHEEL_OVERRIDE_LIGHT = APPEARANCE_OVERRIDE_LIGHT;
const uint FLYWHEEL_OVERRIDE_TEXTURE = APPEARANCE_OVERRIDE_TEXTURE;
const uint FLYWHEEL_CRUMBLING = APPEARANCE_CRUMBLING;
const uint FLYWHEEL_NORMAL_CORRECTION = APPEARANCE_NORMAL_CORRECTION;

void applyFlywheelAppearanceBase(uint geometryBufferIndex,
                                 PositionVertex positions[3],
                                 inout MaterialVertex materials[3]) {
    InstanceAppearance appearance = instanceAppearances.values[geometryBufferIndex];
    for (int i = 0; i < 3; ++i) {
        if ((appearance.flags & FLYWHEEL_NORMAL_CORRECTION) != 0u) {
            materials[i].norm = mat3(appearance.normalCorrection0.xyz,
                appearance.normalCorrection1.xyz, appearance.normalCorrection2.xyz) * materials[i].norm;
        }
        if ((appearance.flags & FLYWHEEL_COLOR_MULTIPLY) != 0u)
            materials[i].colorLayer *= appearance.colorMultiply;
        if ((appearance.flags & FLYWHEEL_COLOR_REPLACE) != 0u)
            materials[i].colorLayer = appearance.colorReplace;
        if ((appearance.flags & FLYWHEEL_UV_OFFSET) != 0u)
            materials[i].textureUV += appearance.uv.xy;
        if ((appearance.flags & FLYWHEEL_FLUID_UV) != 0u)
            materials[i].textureUV.y = appearance.uv.w
                + positions[i].pos.y * appearance.fluidProgress * appearance.uv.z;
        if ((appearance.flags & FLYWHEEL_SHADOW_UV) != 0u) {
            vec2 shadowPosition = positions[i].pos.xz * appearance.uv.xy + appearance.uv.zw;
            materials[i].textureUV = shadowPosition * 0.5 / appearance.shadow.x + 0.5;
            materials[i].colorLayer.a = appearance.shadow.y;
        }
        if ((appearance.flags & FLYWHEEL_OVERRIDE_OVERLAY) != 0u
            && (appearance.materialFlags & 1u) != 0u) {
            materials[i].overlayUV = appearance.overlay;
            materials[i].packedData |= USE_OVERLAY_BIT;
        }
        if ((appearance.flags & FLYWHEEL_OVERRIDE_LIGHT) != 0u
            && (appearance.materialFlags & FLYWHEEL_MATERIAL_USE_LIGHT) != 0u) {
            materials[i].lightUV = max(materials[i].lightUV, appearance.light);
            materials[i].packedData |= USE_LIGHT_BIT;
        }
        if ((appearance.flags & FLYWHEEL_OVERRIDE_TEXTURE) != 0u) {
            materials[i].textureID = appearance.textureOverride;
            materials[i].packedData |= USE_TEXTURE_BIT;
        }
    }
}

void applyFlywheelFragmentLighting(uint geometryBufferIndex, vec3 localPosition,
                                   vec3 localNormal, inout vec4 color,
                                   inout ivec2 lightUv) {
    // Path tracing supplies all level-based and directional lighting. The vanilla lightmap,
    // the cardinal face-shade formula and dynamically sampled Flywheel/Sable light are
    // intentionally not applied, so ray-traced surfaces only receive physical illumination.
    InstanceAppearance appearance = instanceAppearances.values[geometryBufferIndex];
    if ((appearance.flags & FLYWHEEL_CRUMBLING) != 0u
        && appearance.textureOverride != 0u) {
        vec3 sourceNormal = localNormal;
        if ((appearance.flags & FLYWHEEL_NORMAL_CORRECTION) != 0u) {
            mat3 correction = mat3(appearance.normalCorrection0.xyz,
                appearance.normalCorrection1.xyz, appearance.normalCorrection2.xyz);
            sourceNormal = inverse(correction) * sourceNormal;
        }
        vec3 crumblingPosition = (appearance.crumblingTransform * vec4(localPosition, 1.0)).xyz;
        vec3 crumblingNormal = normalize(mat3(appearance.crumblingNormal0.xyz,
            appearance.crumblingNormal1.xyz, appearance.crumblingNormal2.xyz) * sourceNormal);
        vec2 crumblingUv = fract(flywheelCrumblingUv(crumblingPosition, crumblingNormal));
        vec3 crumbling = textureLod(
            textures[nonuniformEXT(appearance.textureOverride)], crumblingUv, 0.0).rgb;
        color.rgb *= 2.0 * crumbling;
    }
}

void loadTriangleCoverage(uint geometryBufferIndex, uint primitiveID,
                          out uint i0, out uint i1, out uint i2,
                          out PositionVertex p0, out PositionVertex p1, out PositionVertex p2,
                          out MaterialVertex m0, out MaterialVertex m1, out MaterialVertex m2) {
    loadTriangleIndices(geometryBufferIndex, primitiveID, i0, i1, i2);
    loadTrianglePositions(geometryBufferIndex, i0, i1, i2, p0, p1, p2);
    loadTriangleMaterial(geometryBufferIndex, i0, i1, i2, m0, m1, m2);
    PositionVertex positions[3] = PositionVertex[3](p0, p1, p2);
    MaterialVertex materials[3] = MaterialVertex[3](m0, m1, m2);
    applyFlywheelAppearanceBase(geometryBufferIndex, positions, materials);
    m0 = materials[0]; m1 = materials[1]; m2 = materials[2];
}

void loadTriangle(uint geometryBufferIndex,
                  uint primitiveID,
                  out uint i0,
                  out uint i1,
                  out uint i2,
                  out PositionVertex p0,
                  out PositionVertex p1,
                  out PositionVertex p2,
                  out MaterialVertex m0,
                  out MaterialVertex m1,
                  out MaterialVertex m2) {
    loadTriangleIndices(geometryBufferIndex, primitiveID, i0, i1, i2);
    loadTrianglePositions(geometryBufferIndex, i0, i1, i2, p0, p1, p2);
    loadTriangleMaterial(geometryBufferIndex, i0, i1, i2, m0, m1, m2);

    MaterialVertex materials[3] = MaterialVertex[3](m0, m1, m2);
    PositionVertex positions[3] = PositionVertex[3](p0, p1, p2);
    applyFlywheelAppearanceBase(geometryBufferIndex, positions, materials);
    m0 = materials[0]; m1 = materials[1]; m2 = materials[2];
}
#endif

#endif
