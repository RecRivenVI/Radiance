#ifndef LOD_MATERIAL_GLSL
#define LOD_MATERIAL_GLSL
#include "util/voxy_quad.glsl"
#include "util/color_space.glsl"
layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer LodQuads { LodQuad values[]; };
layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer LodModels { LodModel values[]; };
layout(std430, buffer_reference, buffer_reference_align = 4) readonly buffer LodColours { uint values[]; };
layout(std430, buffer_reference, buffer_reference_align = 16) readonly buffer LodCoverage { ivec4 values[]; };
layout(std430, buffer_reference, buffer_reference_align = 8) readonly buffer LodHeader {
    uint64_t models;
    uint64_t colours;
    uint64_t coverage;
    uint modelCount;
    uint colourCount;
    uint coverageMask;
    uint padding;
    uint64_t lightGroups;
    uint64_t lightIndices;
    uint lightGroupCount;
    uint lightCount;
};
bool isLodGeometry(uint g) { return (instanceAppearances.values[g].flags & APPEARANCE_LOD) != 0u; }
bool isLodMaterial(MaterialVertex m) { return (m.packedData & PACKED_LOD_BIT) != 0u; }
bool materialIsWater(MaterialVertex m) { return isLodMaterial(m) && m.lightUV.x == 1; }
LodHeader lodHeader(uint g) {
    InstanceAppearance a = instanceAppearances.values[g];
    return LodHeader(uint64_t(a.lightSectionStart) | (uint64_t(a.lightSectionCount) << 32));
}
MaterialVertex lodModelVertex(LodHeader header, LodQuad q, uint corner) {
    LodModel model = LodModels(header.models).values[lodModelId(q)];
    uint face=lodFace(q);LodFace f=model.faces[face];uint flags=floatBitsToUint(f.plane.w);
    uint tint=model.material.y;
    if((model.material.z&1u)!=0u){uint index=tint+q.tint;tint=index<header.colourCount?LodColours(header.colours).values[index]:0xffffffffu;}
    bool discardAlpha=(flags&2u)!=0u || (any(notEqual(lodExtent(q),vec2(1)))&&(flags&4u)!=0u);
    MaterialVertex m;
    m.norm = lodAxes(face >> 1, vec3(0, 0, (face & 1u) == 0u ? -1 : 1));
    m.textureID = model.material.x;
    m.colorLayer = vec4(uintBitsToFloat(model.surface.z),uintBitsToFloat(model.surface.w),1,1);
    m.textureUV = lodUv(q, f, corner) * vec2(uintBitsToFloat(model.surface.z),uintBitsToFloat(model.surface.w));
    m.overlayUV = ivec2(int(tint), int((flags>>3)&3u));
    m.glintUV=f.plane.yz;
    m.glintTexture = uint(discardAlpha);
    m.albedoEmission = uintBitsToFloat(model.material.w);
    m.lightUV = ivec2(model.surface.xy);
    m.packedData = PACKED_TEXTURE_BIT | PACKED_NO_HEIGHT_BIT | PACKED_LOD_BIT |
        (((model.material.z >> LOD_ALPHA_SHIFT) & LOD_ALPHA_MASK) << PACKED_ALPHA_SHIFT);
    m.emissiveOverlayTextureID = 0u;
    return m;
}
#ifndef CONST_ONLY
MaterialVertex lodVertex(uint g, uint vertex) {
    return lodModelVertex(lodHeader(g), LodQuads(materialBufferAddrs.addrs[g]).values[vertex / 4u], vertex & 3u);
}
vec2 lodCacheKey(uint g, uint primitive) {
    LodQuad q = LodQuads(materialBufferAddrs.addrs[g]).values[primitive / 2u];
    return vec2(lodModelId(q), lodFace(q) | (q.tint << 3u) | (q.extent != 65537u ? 4096u : 0u));
}
#endif
MaterialVertex lodCachedVertex(uint g, vec2 key) {
    LodQuad q = LodQuad(0u, (uint(key.y)&4096u)!=0u ? 65538u:65537u, uint(key.x) | ((uint(key.y) & 7u) << 24u), (uint(key.y) >> 3u)&511u);
    return lodModelVertex(lodHeader(g), q, 0u);
}
vec2 materialTextureUv(MaterialVertex m, vec2 uv) {
    if (!isLodMaterial(m)) return uv;
    vec2 tile;
    return modf(uv / m.colorLayer.xy, tile) * m.colorLayer.xy + m.glintUV;
}
vec4 sampleMaterialTexture(MaterialVertex m, sampler2D tex, vec2 uv, float lod, bool srgb) {
    if (!isLodMaterial(m)) return sampleTexture(tex, uv, lod, srgb);
    vec4 colour = textureLod(tex, uv, clamp(lod, 0.0, float(m.lightUV.y)));
    vec4 base = textureLod(tex, uv, 0.0);
    uint state = uint(m.overlayUV.y);
    if (state == 2u || (state == 1u && abs(base.r - base.g) < 0.02 && abs(base.g - base.b) < 0.02)) {
        uint tint = uint(m.overlayUV.x);
        colour *= vec4((tint >> 16) & 255u, (tint >> 8) & 255u, tint & 255u, tint >> 24) / 255.0;
    }
    uint alpha = getAlphaMode(m.packedData);
    if (alpha != ALPHA_TRANSMISSION && alpha != ALPHA_COVERAGE)
        colour.a = m.glintTexture != 0u && base.a <= 0.1 ? 0.0 : 1.0;
    else if (base.a == 0.0) colour.a = 0.0;
    return applySrgbToLinear(colour, srgb);
}
vec4 sampleMaterialTexture(MaterialVertex m, sampler2D tex, vec2 uv, bool srgb) {
    if (!isLodMaterial(m)) return sampleTexture(tex, uv, srgb);
    return sampleMaterialTexture(m, tex, uv, 0.0, srgb);
}
bool lodPointCovered(LodHeader header, ivec4 originLevel, LodQuad q, vec3 local) {
    vec3 base = lodBase(q);
    vec3 extent = lodAxes(lodFace(q) >> 1, vec3(lodExtent(q),1));
    float scale = float(1u << uint(originLevel.w));
    vec3 lower = base * scale, upper = (base + extent) * scale;
    vec3 owned = clamp(local, lower, uintBitsToFloat(floatBitsToUint(upper) - uvec3(1)));
    ivec3 cell = originLevel.xyz / 16 + ivec3(floor(owned / 16.0));
    uint h = (uint(cell.x) * 73856093u) ^ (uint(cell.y) * 19349663u) ^ (uint(cell.z) * 83492791u);
    for (uint probe = 0u; probe <= header.coverageMask; probe++) {
        ivec4 candidate = LodCoverage(header.coverage).values[(h + probe) & header.coverageMask];
        if (candidate.w == 0) return false;
        if (all(equal(candidate.xyz, cell))) return true;
    }
    return false;
}
#ifndef CONST_ONLY
bool lodCovered(uint g, uint primitive, vec3 bary) {
    if (!isLodGeometry(g)) return false;
    InstanceAppearance a = instanceAppearances.values[g];
    uint i0, i1, i2; loadTriangleIndices(g, primitive, i0, i1, i2);
    PositionBuffer p = PositionBuffer(positionBufferAddrs.addrs[g]);
    vec3 local = bary.x*p.vertices[i0].pos + bary.y*p.vertices[i1].pos + bary.z*p.vertices[i2].pos;
    return lodPointCovered(lodHeader(g), ivec4(a.overlay, a.light), LodQuads(materialBufferAddrs.addrs[g]).values[primitive/2u], local);
}
bool acceptsLodFace(uint g, uint primitive, bool hardwareFront, bool directImage) {
    if (!isLodGeometry(g) || !directImage) return true;
    LodHeader header = lodHeader(g);
    LodQuad q = LodQuads(materialBufferAddrs.addrs[g]).values[primitive / 2u];
    LodModel model = LodModels(header.models).values[lodModelId(q)];
    if((model.material.z&6u)!=0u || ((model.material.z>>LOD_ALPHA_SHIFT)&LOD_ALPHA_MASK)==ALPHA_TRANSMISSION)return true;
    uint face = lodFace(q);
    bool sourceFront = ((face & 1u) != 0u) != ((face >> 1) != 0u);
    return hardwareFront == sourceFront;
}
#endif
#endif
