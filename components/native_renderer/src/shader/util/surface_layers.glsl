#ifndef SURFACE_LAYERS_GLSL
#define SURFACE_LAYERS_GLSL
#include "util/material_faces.glsl"
#include "util/ray_cone.glsl"
layout(std430, buffer_reference, buffer_reference_align=8) readonly buffer SurfaceLayerRanges {
    SurfaceLayerRange values[];
};
layout(std430, buffer_reference, buffer_reference_align=8) readonly buffer SurfaceLayerRecords {
    SurfaceLayerTriangle values[];
};
#include "util/surface_layer_sampling.glsl"
// Coverage sampling implements source-over in draw order, retaining the selected
// source's complete material. AH and CH use the same immutable ray identity.
bool selectSurfaceLayer(uint geometry, uint primitive, vec3 bary, bool hardwareFront, bool directImage,
    vec3 origin, vec3 direction, uint frameSeed, float coneRadius, mat3 objectToWorld,
    PositionVertex p0, PositionVertex p1, PositionVertex p2,
    inout MaterialVertex m0, inout MaterialVertex m1, inout MaterialVertex m2) {
    InstanceAppearance appearance=instanceAppearances.values[geometry];
    uint64_t address=(uint64_t(appearance.surfaceLayerAddressHi)<<32u)|uint64_t(appearance.surfaceLayerAddressLo);
    if (address==uint64_t(0)) return true;
    if (primitive>=appearance.surfaceLayerPrimitiveCount) return false;
    SurfaceLayerRange range=SurfaceLayerRanges(address).values[primitive];
    if (range.count==0u) return true;
    uint64_t records=(uint64_t(range.addressHi)<<32u)|uint64_t(range.addressLo);
    float choice=surfaceLayerRandom(origin,direction,frameSeed,geometry,primitive);
    MaterialVertex base[3]=MaterialVertex[3](m0,m1,m2);
    for (uint remaining=range.count;remaining>0u;--remaining) {
        SurfaceLayerTriangle source=SurfaceLayerRecords(records).values[remaining-1u];
        uint faces=source.faces | (appearance.materialFlags & FACE_HARDWARE_FRONT_FLIP_BIT);
        if (!acceptsQueryTriangleFace(faces,hardwareFront,source.primitivePolicy,directImage)) continue;
        MaterialVertex v[3]=source.vertices;
        for (uint j=0u;j<3u;++j) applyLocalSurface(appearance,v[j]);
        vec2 uv=bary.x*v[0].textureUV+bary.y*v[1].textureUV+bary.z*v[2].textureUV;
        float lod=coneRadius>0.0 ? lodWithObjectCone(textures[nonuniformEXT(v[0].textureID)],coneRadius,
            objectToWorld,p0.pos,p1.pos,p2.pos,v[0].textureUV,v[1].textureUV,v[2].textureUV) : 0.0;
        float ta=sampleTexture(textures[nonuniformEXT(v[0].textureID)],uv,lod,false).a;
        float va=bary.x*v[0].colorLayer.a+bary.y*v[1].colorLayer.a+bary.z*v[2].colorLayer.a;
        float alpha=resolveSourceAlpha(ta,va,1.0,getAlphaMode(v[0].packedData),getSourceAlphaRule(v[0].packedData));
        if (chooseSurfaceLayer(choice, alpha)) {
            for (uint j=0u;j<3u;++j) {
                v[j].packedData &= ~((PACKED_ALPHA_MASK<<PACKED_ALPHA_SHIFT)|(SOURCE_ALPHA_RULE_MASK<<PACKED_ALPHA_RULE_SHIFT));
                v[j].packedData |= ALPHA_OPAQUE<<PACKED_ALPHA_SHIFT;
                v[j].colorLayer.a=1.0;
                if (!hasGlint(v[j].packedData) && hasGlint(base[j].packedData)) {
                    v[j].packedData |= base[j].packedData & (PACKED_GLINT_BIT|(GLINT_MODE_MASK<<GLINT_MODE_SHIFT));
                    v[j].glintUV=base[j].glintUV; v[j].glintTexture=base[j].glintTexture;
                }
            }
            m0=v[0];m1=v[1];m2=v[2];
            return true;
        }
    }
    return false;
}
// Both hit stages derive facing and footprint from the same incoming MainRay.
// A closest-hit stage may also declare a ShadowRay for lighting; that does not
// make its incoming query a shadow query.
bool selectMainSurfaceLayer(MainRay ray, uint geometry, uint primitive, vec3 bary, bool hardwareFront,
    vec3 origin, vec3 direction, uint frameSeed, float hitT, mat3 objectToWorld,
    PositionVertex p0, PositionVertex p1, PositionVertex p2,
    inout MaterialVertex m0, inout MaterialVertex m1, inout MaterialVertex m2) {
    return selectSurfaceLayer(geometry,primitive,bary,hardwareFront,rayDirectImage(ray),origin,direction,
        frameSeed,ray.coneWidth+hitT*ray.coneSpread,objectToWorld,p0,p1,p2,m0,m1,m2);
}
#endif
