#ifndef COLOUR_FIELD_GLSL
#define COLOUR_FIELD_GLSL
// Source-generated fields are data, never an executable third-party shader.
uint colourFieldProgram(MaterialVertex m) { return m.glintTexture & 4095u; }
bool hasColourField(MaterialVertex m) {
    return !isLodMaterial(m) && !hasGlint(m.packedData) && !hasVertexShadingNormal(m.packedData) && (m.glintTexture & COLOUR_FIELD_MATERIAL_BIT) != 0u
        && (mapping.programs[colourFieldProgram(m)].settings.x == 3u || mapping.programs[colourFieldProgram(m)].settings.x == 4u);
}
vec2 colourFieldValue(MaterialVertex m) {
    int image = mapping.programs[colourFieldProgram(m)].textures.y;
    if(image < 0) return vec2(0,1);
    vec2 stepUv=vec2(1.0/float(textureSize(textures[nonuniformEXT(image)],0).x),0);
    return vec2(textureLod(textures[nonuniformEXT(image)],m.glintUV,0).r,textureLod(textures[nonuniformEXT(image)],m.glintUV+stepUv,0).r);
}
void interpolateColourField(inout MaterialVertex a, MaterialVertex b, MaterialVertex c, vec3 bary) {
    if (!hasColourField(a)) return;
    if (mapping.programs[colourFieldProgram(a)].settings.x == 4u) {
        int image = mapping.programs[colourFieldProgram(a)].textures.y;
        vec2 uv = clamp((a.glintUV*bary.x+b.glintUV*bary.y+c.glintUV*bary.z)*4.0,vec2(0),vec2(3.999999));
        ivec2 cell=ivec2(floor(uv)); vec2 f=fract(uv);
        uint base=(a.glintTexture>>12u & 524287u)*25u+uint(cell.y*5+cell.x);
        uint nodes[3]=uint[3](base,base+(f.x>=f.y?1u:6u),base+(f.x>=f.y?6u:5u));
        vec3 w=f.x>=f.y?vec3(1-f.x,f.x-f.y,f.y):vec3(1-f.y,f.x,f.y-f.x);
        vec2 value=vec2(0); int width=textureSize(textures[nonuniformEXT(image)],0).x;
        for(int i=0;i<3;i++) {
            int at=int(nodes[i])*2;
            value+=w[i]*vec2(texelFetch(textures[nonuniformEXT(image)],ivec2(at%width,at/width),0).r,
                texelFetch(textures[nonuniformEXT(image)],ivec2((at+1)%width,(at+1)/width),0).r);
        }
        a.glintUV=max(vec2(0),value);
    } else a.glintUV = max(vec2(0),
        bary.x*colourFieldValue(a)+bary.y*colourFieldValue(b)+bary.z*colourFieldValue(c));
}
#include "util/colour_transform.glsl"
vec3 applyColourField(MaterialVertex source,vec3 tint) {
    if (!hasColourField(source)) return tint;
    LayeredWorldMaterial program=mapping.programs[colourFieldProgram(source)];
    return transformHsvColour(tint,program.uv,program.motion,
        program.textures.y<0?0.0:source.glintUV.x,program.textures.y<0?1.0:source.glintUV.y);
}
#endif
