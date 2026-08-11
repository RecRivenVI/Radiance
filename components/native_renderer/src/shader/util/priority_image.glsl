#ifndef PRIORITY_IMAGE_GLSL
#define PRIORITY_IMAGE_GLSL
#include "util/ray.glsl"
#include "util/random.glsl"
#ifndef PRIORITY_IMAGE_EXTERNAL_PAYLOAD
layout(location = 0) rayPayloadEXT MainRay mainRay;
#endif

void initPriorityQuery(vec3 origin, vec3 direction, uint state, uint seed) {
    mainRay.origin = origin;
    mainRay.direction = direction;
    mainRay.hitT = INF_DISTANCE;
    mainRay.seed = seed;
    mainRay.radiance = vec3(0);
    mainRay.coneWidth = 0.0;
    mainRay.throughput = vec3(1);
    mainRay.coneSpread = 2.0 / (abs(worldUBO.cameraProjMat[1][1]) * float(gl_LaunchSizeEXT.y));
    if (abs(worldUBO.cameraProjMat[3][3]) > 0.5) {
        mainRay.coneWidth = max(1.0 / (abs(worldUBO.cameraProjMat[0][0]) * float(gl_LaunchSizeEXT.x)),
                                1.0 / (abs(worldUBO.cameraProjMat[1][1]) * float(gl_LaunchSizeEXT.y)));
        mainRay.coneSpread = 0.0;
    }
    mainRay.prevScenePos = vec3(0);
    mainRay.hasPrevScenePos = 0u;
    mainRay.normal = vec3(0);
    mainRay.stateBits = state | rayDirectImageBit;
    mainRay.directLightRadiance = vec3(0);
    mainRay.pad0 = 0u;
}

struct PrioritySurfaceData {
    vec4 diffuse;
    vec3 specular;
    vec4 normalRoughness;
};
vec4 tracePriorityLayer(vec3 origin, vec3 direction, uint seed, out float hitT, out vec3 previous,
                       out bool hasPrevious, out PrioritySurfaceData firstSurface) {
    firstSurface.diffuse=vec4(0);
    firstSurface.specular=vec3(0);
    firstSurface.normalRoughness=vec4(0,0,0,1);
    initPriorityQuery(origin, direction, rayPriorityCaptureBit, seed);
    traceRayEXT(topLevelAS, gl_RayFlagsCullBackFacingTrianglesEXT, queryMask(queryPriority, true),
                1, 1, 2, origin, 0.0, direction, 1000.0, 0);
    hitT = mainRay.hitT;
    previous = mainRay.prevScenePos;
    hasPrevious = mainRay.hasPrevScenePos != 0u;
    if (hitT == INF_DISTANCE) return vec4(0);
    float alpha = clamp(mainRay.throughput.x, 0.0, 1.0);
    if (alpha <= 0.0) return vec4(0);

    initPriorityQuery(origin, direction, rayPriorityShadeBit, seed);
#ifdef ADV_NUM_RAY_BOUNCES
    const int limit = ADV_NUM_RAY_BOUNCES;
#else
    const int limit = VPT_NUM_RAY_BOUNCES;
#endif
    for (int bounce = 0; bounce < limit; ++bounce) {
        // Consume the preceding event before clearing its continuation bit.
        // Coverage and refraction preserve camera ownership, even in this pass.
        if (bounce != 0) rayAdvanceQuery(mainRay);
        raySetBounce(mainRay, uint(bounce));
        raySetStop(mainRay, false);
        raySetContinue(mainRay, false);
        uint mask = bounce == 0 ? queryMask(queryPriority, true) : queryMask(rayQueryPurpose(mainRay), worldUBO.isFirstPerson > 0);
        if (bounce != 0) {
            mainRay.stateBits &= ~rayPriorityShadeBit;
        }
        traceRayEXT(topLevelAS, queryTraversalFlags(rayQueryPurpose(mainRay), gl_RayFlagsCullBackFacingTrianglesEXT),
                    mask, 1, 1, 1, mainRay.origin, 0.0, mainRay.direction, 1000.0, 0);
        if (bounce == 0) {
            MaterialInfo material = rayLoadMaterial(mainRay);
            firstSurface.diffuse=vec4(material.albedoValue.rgb*material.albedoValue.a,material.metallic);
            firstSurface.specular=material.f0;
            firstSurface.normalRoughness=vec4(mainRay.normal,material.roughness);
        }
        if (rayShouldStop(mainRay)) break;
    }
    return vec4(mainRay.radiance, alpha);
}
#endif
