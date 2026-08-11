#ifndef SURFACE_LAYER_SAMPLING_GLSL
#define SURFACE_LAYER_SAMPLING_GLSL
uint layerHash(uint x) { x ^= x>>16u; x *= 0x7feb352du; x ^= x>>15u; x *= 0x846ca68bu; return x^(x>>16u); }
float surfaceLayerRandom(vec3 origin, vec3 direction, uint frameSeed, uint geometry, uint primitive) {
    uint h=layerHash(frameSeed ^ layerHash(geometry) ^ layerHash(primitive+17u));
    uvec3 o=floatBitsToUint(origin), d=floatBitsToUint(direction);
    for (uint j=0u;j<3u;++j) h=layerHash(h ^ layerHash(o[j]) ^ layerHash(d[j]+j));
    return float(h>>8u)*(1.0/16777216.0);
}
bool chooseSurfaceLayer(inout float choice, float alpha) {
    if (choice<alpha) return true;
    if (alpha<1.0) choice=(choice-alpha)/(1.0-alpha);
    return false;
}
#endif
