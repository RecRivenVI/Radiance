#include "common/constants.glsl"
#include "util/end_portal.glsl"
vec3 computeEndPortalColor(vec4 projection,int iterations,uint sky,uint portal,float time) {
    return sourceEndPortal(projection,iterations,sky,portal,time,ADV_COLORS);
}
