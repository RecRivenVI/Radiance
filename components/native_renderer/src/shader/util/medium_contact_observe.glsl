#ifndef MEDIUM_CONTACT_OBSERVE_GLSL
#define MEDIUM_CONTACT_OBSERVE_GLSL
#if USE_SHARC && MCVR_SHARC_AUDIT
#include "util/sharc_config.glsl"
layout(std430, buffer_reference, buffer_reference_align=4) buffer MediumContactCounters { uint values[]; };
void observeMediumContact(uint index) {
    if ((gl_LaunchIDEXT.x & 15u) != 0u || (gl_LaunchIDEXT.y & 15u) != 0u) return;
    uint64_t address=uint64_t(sharcConfig.auditAddress.x)|(uint64_t(sharcConfig.auditAddress.y)<<32);
    if(address!=uint64_t(0))atomicAdd(MediumContactCounters(address).values[8u+index],1u);
}
#else
#define observeMediumContact(index)
#endif
#endif
