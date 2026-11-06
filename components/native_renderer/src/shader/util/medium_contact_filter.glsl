#ifndef MEDIUM_CONTACT_FILTER_GLSL
#define MEDIUM_CONTACT_FILTER_GLSL
struct MediumContactFilter {
    uint64_t primitives;
    uint64_t neighbors;
    uint neighborCount;
    uint unknown;
};
#ifndef MEDIUM_CONTACT_SET
#define MEDIUM_CONTACT_SET 1
#define MEDIUM_CONTACT_BINDING 15
#endif
layout(set=MEDIUM_CONTACT_SET, binding=MEDIUM_CONTACT_BINDING, std430) readonly buffer MediumContactFilters {
    MediumContactFilter values[];
} mediumContactFilters;
layout(buffer_reference, std430, buffer_reference_align=4) readonly buffer MediumContactBits {
    uint values[];
};
layout(buffer_reference, std430, buffer_reference_align=16) readonly buffer MediumContactNeighbors {
    vec4 boxes[];
};
bool potentialMediumContact(uint geometry, uint primitive, vec3 localPosition, vec3 uncertainty) {
    MediumContactFilter proof = mediumContactFilters.values[geometry];
    if (proof.unknown != 0u) return true;
    if (proof.primitives != uint64_t(0) &&
        (MediumContactBits(proof.primitives).values[primitive >> 5u] & (1u << (primitive & 31u))) != 0u) return true;
    if (proof.neighborCount == 0u) return false;
    MediumContactNeighbors neighbors = MediumContactNeighbors(proof.neighbors);
    // Bounds reject work only; contact still requires the physical boundary probe.
    for (uint i=0u; i<proof.neighborCount; ++i) {
        vec3 lo=neighbors.boxes[2u*i].xyz, hi=neighbors.boxes[2u*i+1u].xyz;
        if (all(greaterThanEqual(localPosition+uncertainty,lo)) && all(lessThanEqual(localPosition-uncertainty,hi))) return true;
    }
    return false;
}
bool potentialMediumContact(uint geometry,uint primitive,vec3 position) {
    return potentialMediumContact(geometry,primitive,position,vec3(0));
}
#endif
