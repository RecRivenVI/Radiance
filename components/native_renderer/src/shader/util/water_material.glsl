#ifndef WATER_MATERIAL_GLSL
#define WATER_MATERIAL_GLSL
#include "util/labpbr.glsl"

const vec3 waterOpticalTint = vec3(0.95, 0.98, 1.0);
const float waterOpticalIor = 1.333;

void applyWaterMaterial(inout vec4 albedoValue, inout vec3 tint, inout LabPBRMat mat) {
    // A baked raster-water colour is not the optical colour of the water medium.
    // Keep source alpha: it also describes the proxy's valid surface coverage.
    tint = waterOpticalTint;
    albedoValue.rgb = tint;
    mat.albedo = tint;
    mat.f0 = vec3(0.02);
    mat.roughness = 0.01;
    mat.metallic = 0.0;
    mat.transmission = 1.0;
    mat.ior = waterOpticalIor;
}
#endif
