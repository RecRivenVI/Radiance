#ifndef LAYERED_COORDINATES_GLSL
#define LAYERED_COORDINATES_GLSL
vec3 layeredOriginScene(LayeredWorldMaterial material,dvec3 camera) {
    return vec3(dvec3(material.origin.xyz)+dvec3(material.motion.z,0,material.motion.w)-camera);
}
vec2 layeredMaskUv(LayeredWorldMaterial material,vec3 scenePosition,dvec3 camera) {
    return (scenePosition.xz-layeredOriginScene(material,camera).xz)/(material.origin.w*2)+.5;
}
vec2 layeredPlaneUv(LayeredWorldMaterial material,vec3 scenePosition,dvec3 camera,float time) {
    dvec2 uv=(dvec2(scenePosition.xz)+camera.xz)*dvec2(material.uv.xy)
        +dvec2(material.uv.zw)+dvec2(time*material.motion.xy);
    // Reduce before conversion only for the actual sampler address mode. Clamped images
    // must retain their edge behavior, including negative coordinates.
    if((material.settings.w&128u)!=0u)uv-=floor(uv);
    else if((material.settings.w&256u)!=0u)uv-=floor(uv/2.0)*2.0;
    return vec2(uv);
}
#endif
