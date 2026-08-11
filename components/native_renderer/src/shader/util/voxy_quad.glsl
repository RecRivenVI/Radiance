#ifndef VOXY_QUAD_GLSL
#define VOXY_QUAD_GLSL

// Interoperability layout for Voxy's 64-bit CPU mesher output and 64-byte model record.
// No OpenGL storage, baked lightmap, or original high-detail vertices are consumed.
struct VoxyModel { uint words[16]; };
uint voxyFace(uint64_t q) { return uint(q & 7ul); }
uint voxyModelId(uint64_t q) { return uint((q >> 26) & 65535ul); }
vec3 voxyAxes(uint axis, vec3 p) {
    return axis == 0u ? p.xzy : (axis == 1u ? p : p.zxy);
}
vec4 voxyBounds(uint f) {
    vec4 bounds = vec4(f & 15u, ((f >> 4) & 15u) + 1u,
                      (f >> 8) & 15u, ((f >> 12) & 15u) + 1u) / 16.0;
    bounds.xz -= vec2(0.00005);
    bounds.yw -= bounds.xz;
    return bounds;
}
vec2 voxyUvFromFace(uint64_t q, uint faceWord, uint corner) {
    vec4 b = voxyBounds(faceWord);
    vec2 size = b.yw + vec2(uint((q >> 3) & 15ul), uint((q >> 7) & 15ul));
    return b.xz + size * vec2((corner >> 1) & 1u, corner & 1u);
}
vec2 voxyUv(uint64_t q, VoxyModel model, uint corner) {
    return voxyUvFromFace(q, model.words[voxyFace(q)], corner);
}
vec3 voxyPosition(uint64_t q, VoxyModel model, uint corner, uint level) {
    uint face = voxyFace(q), axis = face >> 1;
    uint f = model.words[face];
    uint d = (f >> 16) & 63u;
    d += uint(d == 63u);
    float depth = float(d) / 64.0;
    depth = (face & 1u) == 0u ? depth : 1.0 - depth;
    vec3 base = vec3(uint((q >> 21) & 31ul), uint((q >> 16) & 31ul), uint((q >> 11) & 31ul));
    return (base + voxyAxes(axis, vec3(voxyUv(q, model, corner), depth))) * float(1u << level);
}
#endif
