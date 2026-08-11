#version 460
#extension GL_GOOGLE_include_directive : require
#include "shader/util/display_colour.glsl"
layout(set=0,binding=0) uniform sampler2D composed;
layout(push_constant) uniform Display { vec4 parameters; } pc;
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 colour;
void main() {
    vec4 source=texture(composed,texCoord);
    vec3 value=max(source.rgb,vec3(0.0));
    if (pc.parameters.x>0.5) {
        vec3 nits=clamp(displaySrgbDecode(value)*pc.parameters.y,0.0,pc.parameters.z);
        value=pc.parameters.x<1.5 ? displayPqEncode(displayRec709To2020(nits)) : nits/80.0;
    } else if (pc.parameters.w>0.5) value=displaySrgbDecode(value);
    colour=vec4(value,source.a);
}
