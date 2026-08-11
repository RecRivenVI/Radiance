#ifndef END_PORTAL_GLSL
#define END_PORTAL_GLSL
// Minecraft's projected layers are additive in colour, but the surface is opaque.
// Keep this separate from energy-normalized spatial effects such as End Sea.
vec4 projectPosition(vec4 position) {
    vec4 projection = position * 0.5;
    projection.xy += projection.ww;
    projection.zw = position.zw;
    return projection;
}
vec3 sourceEndPortal(vec4 projection, int layers, uint sky, uint portal, float time, vec3 colours[16]) {
    vec3 colour = sky != 0xFFFFFFFFu ? textureProj(textures[nonuniformEXT(sky)], projection).rgb * colours[0] : vec3(0);
    const mat4 scaleTranslate = mat4(.5,0,0,.25, 0,.5,0,.25, 0,0,1,0, 0,0,0,1);
    for (int i=0;i<layers;++i) {
        if (portal == 0xFFFFFFFFu) continue;
        float layer=float(i+1);
        mat4 translate=mat4(1,0,0,17/layer, 0,1,0,(2+layer/1.5)*time*1.5, 0,0,1,0, 0,0,0,1);
        float angle=radians((layer*layer*4321+layer*9)*2);
        mat2 rotation=mat2(cos(angle),-sin(angle),sin(angle),cos(angle));
        mat2 scale=mat2((4.5-layer/4)*2);
        mat4 transform=mat4(scale*rotation)*translate*scaleTranslate;
        colour+=textureProj(textures[nonuniformEXT(portal)],projection*transform).rgb*colours[i];
    }
    return colour;
}
#endif
