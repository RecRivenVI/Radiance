#ifndef VOXY_MATERIAL_GLSL
#define VOXY_MATERIAL_GLSL
#include "util/voxy_quad.glsl"
#include "util/color_space.glsl"
layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer VoxyQuads { uint64_t values[]; };
layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer VoxyModels { uint words[]; };
layout(std430, buffer_reference, buffer_reference_align = 4) readonly buffer VoxyColours { uint values[]; };
layout(std430, buffer_reference, buffer_reference_align = 16) readonly buffer VoxyCoverage { ivec4 values[]; };
layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer VoxyHeader {
    uint64_t models;
    uint64_t colours;
    uint64_t coverage;
    uint modelCount;
    uint colourCount;
    uint coverageMask;
    uint padding;
};
bool isVoxyGeometry(uint g) { return (instanceAppearances.values[g].flags & APPEARANCE_VOXY) != 0u; }
bool isVoxyMaterial(MaterialVertex m) { return (m.packedData & PACKED_VOXY_BIT) != 0u; }
bool materialIsWater(MaterialVertex m) { return isVoxyMaterial(m) && m.lightUV.x == 1; }
VoxyHeader voxyHeader(uint g) {
    InstanceAppearance a = instanceAppearances.values[g];
    return VoxyHeader(uint64_t(a.lightSectionStart) | (uint64_t(a.lightSectionCount) << 32));
}
MaterialVertex voxyVertex(uint g, uint vertex) {
    InstanceAppearance a = instanceAppearances.values[g];
    VoxyHeader header = voxyHeader(g);
    uint64_t q = VoxyQuads(materialBufferAddrs.addrs[g]).values[vertex / 4u];
    VoxyModels model = VoxyModels(header.models);
    uint record = voxyModelId(q) * 16u;
    uint face = voxyFace(q), f = model.words[record + face];
    uint tile = model.words[record + 12u];
    uint tint = model.words[record + 7u];
    if ((model.words[record + 6u] & 2u) != 0u) {
        uint index = tint + uint((q >> 46) & 511ul);
        tint = index < header.colourCount ? VoxyColours(header.colours).values[index] : 0xffffffffu;
    }
    bool translucent = (model.words[record + 6u] & 4u) != 0u;
    bool discardAlpha = ((f >> 22) & 1u) != 0u ||
        (((q >> 3) & 255ul) != 0ul && ((f >> 23) & 1u) != 0u);
    MaterialVertex m;
    m.norm = voxyAxes(face >> 1, vec3(0, 0, (face & 1u) == 0u ? -1 : 1));
    m.textureID = model.words[record + 9u];
    m.colorLayer = vec4(1);
    m.textureUV = voxyUvFromFace(q, f, vertex & 3u) / vec2(48, 32);
    m.overlayUV = ivec2(int(tint), int((f >> 24) & 3u));
    m.glintUV = vec2((tile & 15u) * 3u + (face >> 1), (tile >> 4) * 2u + (face & 1u)) / vec2(48, 32);
    m.glintTexture = uint(discardAlpha);
    m.albedoEmission = uintBitsToFloat(model.words[record + 10u]);
    m.lightUV = ivec2(model.words[record + 11u], model.words[record + 15u]);
    m.packedData = PACKED_TEXTURE_BIT | PACKED_NO_HEIGHT_BIT | PACKED_VOXY_BIT |
        ((translucent ? ALPHA_TRANSMISSION : ALPHA_CUTOUT_EPSILON) << PACKED_ALPHA_SHIFT);
    m.emissiveOverlayTextureID = 0u;
    return m;
}
vec2 materialTextureUv(MaterialVertex m, vec2 uv) {
    if (!isVoxyMaterial(m)) return uv;
    vec2 tile;
    return modf(uv * vec2(48, 32), tile) / vec2(48, 32) + m.glintUV;
}
vec4 sampleMaterialTexture(MaterialVertex m, sampler2D tex, vec2 uv, float lod, bool srgb) {
    if (!isVoxyMaterial(m)) return sampleTexture(tex, uv, lod, srgb);
    vec4 colour = textureLod(tex, uv, clamp(lod, 0.0, float(m.lightUV.y)));
    vec4 base = textureLod(tex, uv, 0.0);
    uint state = uint(m.overlayUV.y);
    if (state == 2u || (state == 1u && abs(base.r - base.g) < 0.02 && abs(base.g - base.b) < 0.02)) {
        uint tint = uint(m.overlayUV.x);
        colour *= vec4((tint >> 16) & 255u, (tint >> 8) & 255u, tint & 255u, tint >> 24) / 255.0;
    }
    if (getAlphaMode(m.packedData) != ALPHA_TRANSMISSION)
        colour.a = m.glintTexture != 0u && base.a <= 0.1 ? 0.0 : 1.0;
    else if (base.a == 0.0) colour.a = 0.0;
    return applySrgbToLinear(colour, srgb);
}
vec4 sampleMaterialTexture(MaterialVertex m, sampler2D tex, vec2 uv, bool srgb) {
    if (!isVoxyMaterial(m)) return sampleTexture(tex, uv, srgb);
    return sampleMaterialTexture(m, tex, uv, 0.0, srgb);
}
bool voxyCovered(uint g, uint primitive, vec3 bary) {
    if (!isVoxyGeometry(g)) return false;
    InstanceAppearance a = instanceAppearances.values[g];
    VoxyHeader header = voxyHeader(g);
    uint i0, i1, i2;
    loadTriangleIndices(g, primitive, i0, i1, i2);
    PositionBuffer p = PositionBuffer(positionBufferAddrs.addrs[g]);
    vec3 local = bary.x * p.vertices[i0].pos + bary.y * p.vertices[i1].pos + bary.z * p.vertices[i2].pos;
    uint64_t q = VoxyQuads(materialBufferAddrs.addrs[g]).values[primitive / 2u];
    vec3 base = vec3(uint((q >> 21) & 31ul), uint((q >> 16) & 31ul), uint((q >> 11) & 31ul));
    vec3 extent = voxyAxes(voxyFace(q) >> 1,
        vec3(uint((q >> 3) & 15ul) + 1u, uint((q >> 7) & 15ul) + 1u, 1));
    float scale = float(1u << uint(a.light.y));
    vec3 lower = base * scale, upper = (base + extent) * scale;
    // Source micro-expansion must not change voxel ownership. Upper bounds are half-open;
    // positive local coordinates permit an exact predecessor ULP, without a world-space epsilon.
    vec3 owned = clamp(local, lower, uintBitsToFloat(floatBitsToUint(upper) - uvec3(1)));
    ivec3 cell = ivec3(a.overlay, a.light.x) / 16 + ivec3(floor(owned / 16.0));
    uint h = (uint(cell.x) * 73856093u) ^ (uint(cell.y) * 19349663u) ^ (uint(cell.z) * 83492791u);
    for (uint probe = 0u; probe <= header.coverageMask; probe++) {
        ivec4 candidate = VoxyCoverage(header.coverage).values[(h + probe) & header.coverageMask];
        if (candidate.w == 0) return false;
        if (all(equal(candidate.xyz, cell))) return true;
    }
    return false;
}
bool acceptsVoxyFace(uint g, uint primitive, bool hardwareFront, bool directImage) {
    if (!isVoxyGeometry(g) || !directImage) return true;
    VoxyHeader header = voxyHeader(g);
    uint64_t q = VoxyQuads(materialBufferAddrs.addrs[g]).values[primitive / 2u];
    VoxyModels model = VoxyModels(header.models);
    uint record = voxyModelId(q) * 16u;
    if ((model.words[record + 6u] & 4u) != 0u || model.words[record + 14u] != 0u) return true;
    uint face = voxyFace(q);
    bool sourceFront = ((face & 1u) != 0u) != ((face >> 1) != 0u);
    return hardwareFront == sourceFront;
}
#endif
