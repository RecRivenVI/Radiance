#ifndef CELESTIAL_PROFILE_GLSL
#define CELESTIAL_PROFILE_GLSL
#extension GL_EXT_nonuniform_qualifier : require
#include "util/random.glsl"
#include "util/weather_optics.glsl"
#if defined(VPT_SUN_RADIANCE)
#define CELESTIAL_SUN_ENERGY VPT_SUN_RADIANCE
#define CELESTIAL_MOON_ENERGY VPT_MOON_RADIANCE
#else
#define CELESTIAL_SUN_ENERGY ADV_SUN_RADIANCE
#define CELESTIAL_MOON_ENERGY ADV_MOON_RADIANCE
#endif
// SUN/MOON_RADIANCE are reference beam irradiance, not texture radiance.
// Profile integration is source-owned and refreshed on texture reload.
void celestialBasis(bool sun, out vec3 axis, out vec3 right, out vec3 up) {
    axis = sun ? celestialSunDirection() : celestialMoonDirection();
    right = celestialApplySouthOffset(vec3(0,0,1));
    up = normalize(cross(right,axis));
}
bool celestialUv(vec3 direction, bool sun, out vec2 uv, out float pdf) {
    vec3 axis,right,up;celestialBasis(sun,axis,right,up);
    float z=dot(direction,axis), extent=tan(sun?.03:.05);
    if(z<=0){pdf=0;return false;}
    vec2 q=vec2(dot(direction,right),dot(direction,up))/z;
    if(any(greaterThan(abs(q),vec2(extent)))){pdf=0;return false;}
    uv=q/extent*.5+.5;
    pdf=pow(1+dot(q,q),1.5)/(4*extent*extent);
    return true;
}
vec3 celestialTextureRadiance(vec3 direction,bool sun,out float pdf) {
    vec2 uv;
    if(!celestialUv(direction,sun,uv,pdf))return vec3(0);
    uint id=sun?skyUBO.sunTextureID:skyUBO.moonTextureID;
    if(id==0xFFFFFFFFu)return vec3(0);
    vec2 tileCount=sun?vec2(1):vec2(4,2);
    vec2 tile=sun?vec2(0):vec2(skyUBO.moonPhase%4u,skyUBO.moonPhase/4u%2u);
    ivec2 size=textureSize(textures[nonuniformEXT(id)],0);
    vec2 halfTexel=.5/vec2(size);
    vec2 at=clamp((tile+uv)/tileCount,tile/tileCount+halfTexel,(tile+1)/tileCount-halfTexel);
    vec4 value=textureLod(textures[nonuniformEXT(id)],at,0);
    float integral=sun?skyUBO.celestialProfile.x:skyUBO.celestialProfile.y;
    return integral>1e-10 ? weatherDirectStrength(skyUBO.rainGradient, skyUBO.thunderGradient)*value.rgb*value.a*(sun?CELESTIAL_SUN_ENERGY:CELESTIAL_MOON_ENERGY)/integral:vec3(0);
}
vec3 sampleCelestialDirection(inout uint seed,out float pdf) {
    bool sun=celestialSunDirection().y>=0;
    vec3 axis,right,up;celestialBasis(sun,axis,right,up);
    float extent=tan(sun?.03:.05);
    vec2 q=(vec2(rand(seed),rand(seed))*2-1)*extent;
    vec3 direction=normalize(axis+right*q.x+up*q.y);
    pdf=pow(1+dot(q,q),1.5)/(4*extent*extent);
    return direction;
}
float celestialDirectionPdf(vec3 direction) {
    vec2 uv;float pdf;
    return celestialUv(direction,celestialSunDirection().y>=0,uv,pdf)?pdf:0;
}
float celestialMis(float pdfA,float pdfB){return pdfA*pdfA/max(pdfA*pdfA+pdfB*pdfB,1e-20);}
#endif
