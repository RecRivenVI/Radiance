#ifndef LAYERED_MASK_GLSL
#define LAYERED_MASK_GLSL
float layeredDepthCoverage(float depth,float strength,float planeY,vec4 range) {
    if(depth>=1)return 0;
    float blockerY=range.x+depth*range.y;
    if(blockerY<planeY)return 0;
    return pow(clamp(1-(blockerY-planeY)/range.z,0,1),range.w)*strength;
}
#endif
