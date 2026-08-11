#ifndef SHARC_QUERY_MATH_GLSL
#define SHARC_QUERY_MATH_GLSL
bool sharcSegmentEligible(float distance, float voxel, float roughness, uint lobe) {
    if (distance <= voxel * sqrt(3.0)) return false;
    if (lobe == 0u) return true;
    float alpha = min(roughness, 0.99);
    alpha *= alpha;
    float alpha2 = alpha * alpha;
    float footprint = distance * sqrt(0.5 * alpha2 / max(1.0 - alpha2, 1e-6));
    return footprint > voxel;
}
// Remove only the arriving segment attenuation. Cache values belong to the
// surface, while the accumulated sample weights belong to the path to it.
vec3 sharcLocalValue(vec3 weighted, vec3 arriving) {
    return vec3(arriving.x > 0.0 ? weighted.x / arriving.x : 0.0,
                arriving.y > 0.0 ? weighted.y / arriving.y : 0.0,
                arriving.z > 0.0 ? weighted.z / arriving.z : 0.0);
}
#endif
