#ifndef PRIORITY_SURFACE_GLSL
#define PRIORITY_SURFACE_GLSL
#include "util/priority_coverage.glsl"
// Capture coverage separately from illumination. Shading then uses the ordinary
// world material hit group, with the same source texture, tint and PBR inputs.
bool capturePrioritySurface(inout MainRay ray, uint geometry, uint primitive, vec3 bary, float hitT) {
    if ((ray.stateBits & rayPriorityCaptureBit) == 0u) return false;
    uint i0, i1, i2;
    PositionVertex p0, p1, p2;
    MaterialVertex m0, m1, m2;
    loadTriangleCoverage(geometry, primitive, i0, i1, i2, p0, p1, p2, m0, m1, m2);
    vec4 layer = hasColorLayer(m0.packedData) ?
        bary.x*m0.colorLayer + bary.y*m1.colorLayer + bary.z*m2.colorLayer : vec4(1.0);
    vec4 texel = vec4(1.0);
    if (hasTexture(m0.packedData)) {
        vec2 uv = materialTextureUv(m0, bary.x*m0.textureUV + bary.y*m1.textureUV + bary.z*m2.textureUV);
        float radius = ray.coneWidth + hitT * ray.coneSpread;
        float lod = lodWithObjectCone(textures[nonuniformEXT(m0.textureID)], radius, mat3(gl_ObjectToWorldEXT),
            p0.pos, p1.pos, p2.pos, m0.textureUV, m1.textureUV, m2.textureUV);
        texel = sampleMaterialTexture(m0, textures[nonuniformEXT(m0.textureID)], uv, lod, false);
    }
    uint mode = getAlphaMode(m0.packedData);
    float sourceAlpha = isTextMode(mode) ? resolveTextTextureColor(texel, hasTexture(m0.packedData), mode).a : texel.a;
    float alpha = resolveSourceAlpha(sourceAlpha, hasColorLayerMix(m0.packedData) ? 1.0 : layer.a,
        sourceDrawColour(geometry).a, mode, getSourceAlphaRule(m0.packedData));
    ray.hitT = hitT;
    ray.throughput = vec3(resolvePriorityAlpha(alpha, mode));
    ray.radiance = vec3(0.0);
    raySetStop(ray, true);
    return true;
}
#endif
