#ifndef LAYERED_TEXTURE_GLSL
#define LAYERED_TEXTURE_GLSL
#include "util/layered_mask.glsl"
#include "util/layered_coordinates.glsl"
vec2 layeredImageUv(LayeredWorldMaterial material, uint slot, vec2 uv) {
    return (material.settings.w & (4u << slot)) != 0u ? vec2(uv.x, 1.0-uv.y) : uv;
}
vec3 layeredProjection(vec2 uv,int layers,int base,int detail,float time,vec3 colours[16]) {
    vec3 result=base>=0?textureLod(textures[nonuniformEXT(base)],uv,0).rgb*colours[0]:vec3(0);
    vec3 total=colours[0];
    for(int i=0;i<layers;i++) {
        float layer=float(i+1), angle=radians((layer*layer*4321+layer*9)*2);
        mat2 rotation=mat2(cos(angle),-sin(angle),sin(angle),cos(angle));
        vec2 at=rotation*((uv*.5+.25)+vec2(17/layer,(2+layer/1.5)*time*1.5))*(4.5-layer/4)*2;
        vec3 weight=colours[i%16];total+=weight;
        if(detail>=0)result+=textureLod(textures[nonuniformEXT(detail)],at,0).rgb*weight;
    }
    // Total energy is explicit; adding procedural samples changes detail, not brightness.
    return result/max(dot(total,vec3(.2126,.7152,.0722)),1e-6);
}
vec4 evaluateLayeredPlane(LayeredWorldMaterial material,vec3 scenePosition,dvec3 camera,vec4 tint,float time) {
    if(material.settings.x!=2u)return vec4(0);
    vec2 uv=layeredPlaneUv(material,scenePosition,camera,time);
    uv.x += tint.r+tint.g;
    vec2 maskUv=layeredMaskUv(material,scenePosition,camera);
    if(any(lessThan(maskUv,vec2(0)))||any(greaterThan(maskUv,vec2(1))))return vec4(0);
    if(material.textures.x<0||material.textures.z<0||material.textures.w<0)return vec4(0);
    float depth=textureLod(textures[nonuniformEXT(material.textures.z)],layeredImageUv(material,2u,maskUv),0).r;
    float strength=textureLod(textures[nonuniformEXT(material.textures.w)],layeredImageUv(material,3u,maskUv),0).a;
    float coverage=layeredDepthCoverage(depth,strength,float(double(scenePosition.y)+camera.y),material.mask);
    vec4 texel=textureLod(textures[nonuniformEXT(material.textures.x)],layeredImageUv(material,0u,uv),0);
    return vec4((texel.rgb+material.surface.w)*tint.rgb,
        clamp((texel.a+material.surface.w)*tint.a*coverage*material.surface.z,0,1));
}
#endif
