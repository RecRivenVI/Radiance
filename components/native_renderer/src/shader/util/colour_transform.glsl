#ifndef COLOUR_TRANSFORM_GLSL
#define COLOUR_TRANSFORM_GLSL
vec3 colourToHsv(vec3 c) {
    vec4 K=vec4(0,-1.0/3.0,2.0/3.0,-1);
    vec4 p=mix(vec4(c.bg,K.wz),vec4(c.gb,K.xy),step(c.b,c.g));
    vec4 q=mix(vec4(p.xyw,c.r),vec4(c.r,p.yzx),step(p.x,c.r));
    float d=q.x-min(q.w,q.y);
    return vec3(abs(q.z+(q.w-q.y)/(6*d+1e-10)),d/(q.x+1e-10),q.x);
}
vec3 colourFromHsv(vec3 c) {
    vec3 p=abs(fract(c.xxx+vec3(0,2.0/3.0,1.0/3.0))*6-3);
    return c.z*mix(vec3(1),clamp(p-1,0,1),c.y);
}
vec3 shiftedColour(vec3 hsv,float shift,float power) {
    return colourFromHsv(vec3(fract(hsv.x+shift+1),hsv.y,pow(max(hsv.z,0),power)));
}
vec3 transformHsvColour(vec3 tint,vec4 operation,vec4 subtract,float field,float fullness) {
    vec3 hsv=colourToHsv(max(tint,vec3(0)));
    float power=exp(-field);
    hsv=vec3(fract(hsv.x+operation.x+1),pow(hsv.y,operation.y*power),pow(hsv.z,operation.z*power));
    return max(vec3(0),colourFromHsv(hsv)-operation.w*fullness*(
        shiftedColour(hsv,subtract.x,subtract.z)+shiftedColour(hsv,subtract.y,subtract.z)));
}
#endif
