#ifndef SHARC_QUERY_GLSL
#define SHARC_QUERY_GLSL
#include "util/sharc_query_math.glsl"
#if RADIANCE_SHARC_HIT_QUERY
#include "util/sharc_config.glsl"
bool finishCachedSurface(inout MainRay ray, vec3 position, dvec3 camera, float transmission) {
    if ((ray.stateBits & rayCacheQueryBit) == 0u || rayReceiveOnlyReceiver(ray) || transmission > 1e-6)
        return false;
    SharcParameters parameters = makeSharcParameters();
    vec3 worldPosition = vec3(dvec3(position) + camera);
    uint level = HashGridGetLevel(worldPosition, parameters.gridParameters);
    float voxelSize = HashGridGetVoxelSize(level, parameters.gridParameters);
    if (!sharcSegmentEligible(ray.hitT, voxelSize, ray.scatterRoughness, rayLobeType(ray))) return false;
    ray.stateBits |= rayCacheAttemptBit;
    SharcHitData hit;
    hit.positionWorld = worldPosition;
    hit.normalWorld = ray.normal;
    vec3 cached;
    if (!SharcGetCachedRadiance(parameters, hit, cached, false)) return false;
    // Cache includes this vertex's emission and direct light. Use the arriving
    // segment weight (including medium absorption), before evaluating its BSDF.
    rayAcceptCachedRadiance(ray, cached);
    return true;
}
#else
bool finishCachedSurface(inout MainRay ray, vec3 position, dvec3 camera, float transmission) { return false; }
#endif
#endif
