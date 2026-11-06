#ifndef MEDIUM_PROBE_HIT_GLSL
#define MEDIUM_PROBE_HIT_GLSL
#include "common/primitive_policy.hpp"
#include "util/source_material_maps.glsl"
#include "util/surface_overlay.glsl"
#include "util/water_material.glsl"

// Inline boundary-information queries remain separate from shadow transmission.
bool collectMediumBoundary(uint geometry, uint primitive, vec3 bary, bool hardwareFront,
    float distance, mat3 objectToWorld, mat3 normalMatrix, PositionVertex p0, PositionVertex p1, PositionVertex p2, MaterialVertex m0,
    MaterialVertex m1, MaterialVertex m2, vec3 queryOrigin, vec3 queryDirection, vec3 pathDirection, uint overlayTexture, inout ShadowRay query) {
    uint mode = query.queryPadding & 3u;
    if ((p0.pad0 & sameMaterialSheet) != 0u &&
        !acceptsWorldMaterialFace(instanceAppearances.values[geometry].materialFlags,hardwareFront,geometry,primitive)) return false;
    uint packed = m0.packedData;
    if (!isTransmissionAlphaMode(getAlphaMode(packed)) && !materialIsWater(m0)) return false;
    vec2 uv = materialTextureUv(m0, bary.x*m0.textureUV + bary.y*m1.textureUV + bary.z*m2.textureUV);
    float lod = (mode == 0u || mode == 2u) && hasTexture(packed) ?
        lodWithObjectCone(textures[nonuniformEXT(m0.textureID)],query.throughput.z,objectToWorld,
            p0.pos,p1.pos,p2.pos,m0.textureUV,m1.textureUV,m2.textureUV) : 0.0;
    TextureMapEntry maps = hasTexture(packed) ? mapping.entries[m0.textureID] : TextureMapEntry(-1,-1,-1);
    uvec4 mapFlags = uvec4(0);
    if (maps.flag >= 0) {
        mapFlags = uvec4(round(sampleTexture(textures[nonuniformEXT(maps.flag)],uv,ceil(lod),false)*255.0));
        maps = sourceMaterialMapsWithPresence(maps,mapFlags.g);
    }
    bool water = materialIsWater(m0) || (mapFlags.r & 1u) != 0u;
    vec3 outward = water || isLodMaterial(m0) ? normalMatrix*(bary.x*m0.norm + bary.y*m1.norm + bary.z*m2.norm) : vec3(0);
    bool entering = surfaceEntersMedium(pathDirection,
        instanceAppearances.values[geometry].materialFlags,hardwareFront,outward);
    // Reject the hidden twin, not all paired sheets: a different medium (notably
    // a two-sided fluid face) can itself be the legitimate contact neighbour.
    if ((mode == 0u || mode == 2u) && entering != (mode == 0u)) return false;
    if (mode == 3u && !water) return false;
    if (water && excludedWaterPosition(queryOrigin + queryDirection*distance)) return false;
    vec4 colour = hasColorLayer(packed) ? bary.x*m0.colorLayer + bary.y*m1.colorLayer + bary.z*m2.colorLayer : vec4(1);
    vec4 albedo = hasTexture(packed) ? sampleMaterialTexture(m0, textures[nonuniformEXT(m0.textureID)], uv, lod, false) : vec4(1);
    albedo.a = resolveSourceAlpha(albedo.a, hasColorLayerMix(packed) ? 1.0 : colour.a,
        sourceDrawColour(geometry).a, getAlphaMode(packed), getSourceAlphaRule(packed));
    if (albedo.a < 0.05) return false;
    float ior=waterOpticalIor;
    vec3 sigma;
    if(water) {
        // The shared water optics replace RGB/specular/overlay reception. Only
        // source alpha is needed; do not decode discarded PBR attributes.
        if(mode==2u) {query.pad0=1u;return true;}
        sigma=mediumAbsorption(vec4(waterOpticalTint,albedo.a));
    } else {
        vec4 specular = maps.specular >= 0 && !isLodMaterial(m0) ? sampleTexture(textures[nonuniformEXT(maps.specular)],uv,lod,false) : vec4(0);
        LabPBRMat material = convertLabPBRMaterial(albedo,specular,vec4(0),isTransmissionAlphaMode(getAlphaMode(packed)));
        if(material.transmission<=1e-6)return false;
        // An entry-B hit only asks whether a co-located exit-A exists. A's
        // colour/IOR are already in the incoming stack, so decoding them again
        // cannot contribute to the selected event.
        if(mode==2u) {query.pad0=1u;return true;}
        ior=maps.specular<0?1.5:material.ior;
        vec4 overlay = hasOverlay(packed) ? sampleTexture(textures[nonuniformEXT(overlayTexture)],m0.overlayUV,0,false) : vec4(0);
        albedo.rgb = applySurfaceMaterialColor(albedo.rgb,colour,hasColorLayerMix(packed),hasOverlay(packed),overlay,sourceDrawColour(geometry));
        sigma=mediumAbsorption(albedo);
    }
    if (mode == 2u || mode == 0u) {
        query.pad0 = 1u;
        if (mode == 0u) { query.radiance = sigma; query.throughput.x = ior; }
    } else {
        float closest = query.throughput.y;
        if (distance > closest + 1e-6 || (abs(distance-closest)<=1e-6 && entering)) return false;
        query.radiance = sigma;
        query.throughput = vec3(ior,distance,entering ? 1.0 : -1.0);
    }
    return true;
}
#endif
