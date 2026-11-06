#ifndef PRIORITY_IMAGE_GLSL
#define PRIORITY_IMAGE_GLSL
#include "util/ray.glsl"
#include "util/random.glsl"
#include "util/imaging_trace.glsl"
#ifndef PRIORITY_IMAGE_EXTERNAL_PAYLOAD
layout(location = 0) rayPayloadEXT MainRay mainRay;
layout(location = 1) rayPayloadEXT ShadowRay shadowRay;
layout(set=0,binding=0) uniform sampler2D textures[];
layout(set=1,binding=1) readonly buffer BLASOffsets { uint offsets[]; } blasOffsets;
layout(set=1,binding=2) readonly buffer IndexBufferAddr { uint64_t addrs[]; } indexBufferAddrs;
layout(set=1,binding=7) readonly buffer TextureMappingBuffer { TextureMapping mapping; };
layout(std430,buffer_reference,buffer_reference_align=8) readonly buffer IndexBuffer { uint indices[]; } indexBuffer;
#include "util/medium_boundary_trace.glsl"
#define PRIORITY_TRACE_LIGHTING(flags,mask,offset,stride,miss,origin,minimum,direction,maximum,payload) traceImagingRay(flags,mask,offset,stride,miss,origin,minimum,direction,maximum,payload)
#else
// The compute contract supplies the traversal primitive and payload itself.
#define PRIORITY_TRACE_LIGHTING(flags,mask,offset,stride,miss,origin,minimum,direction,maximum,payload) traceRayEXT(topLevelAS,flags,mask,offset,stride,miss,origin,minimum,direction,maximum,payload)
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
    rayInitializeMedium(mainRay, 1.0);
    mainRay.directLightRadiance = vec3(0);
    mainRay.pad0 = 0u;
    mainRay.previousBsdfPdf = 0.0;
    mainRay.lastScatterOrigin = origin;
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
                1, 1, 2, origin, 0.0, direction, imagingTraceLimit(mainRay, 1000.0), 0);
    hitT = mainRay.hitT;
    previous = mainRay.prevScenePos;
    hasPrevious = mainRay.hasPrevScenePos != 0u;
    if (hitT == INF_DISTANCE) return vec4(0);
    float alpha = clamp(mainRay.throughput.x, 0.0, 1.0);
    if (alpha <= 0.0) return vec4(0);

    initPriorityQuery(origin, direction, rayPriorityShadeBit, seed);
    MediumHistory mediumHistory = MediumHistory(0u, 0u);
    int continuations = 0;
    bool priorityPending=true;
    for (int bounce = 0; bounce <= RADIANCE_PATH_EVENTS; ++bounce) {
        // Consume the preceding event before clearing its continuation bit.
        // Coverage and refraction preserve camera ownership, even in this pass.
        if (bounce != 0 && !rayAdvanceQuery(mainRay, mediumHistory)) break;
        raySetBounce(mainRay, uint(bounce));
        raySetTerminal(mainRay, bounce == RADIANCE_PATH_EVENTS);
        raySetStop(mainRay, false);
        raySetContinue(mainRay, false);
        uint mask = priorityPending ? queryMask(queryPriority, true) : queryMask(rayQueryPurpose(mainRay), worldUBO.isFirstPerson > 0);
        if (!priorityPending) {
            mainRay.stateBits &= ~rayPriorityShadeBit;
        }
        vec3 tracedOrigin=mainRay.origin, tracedDirection=mainRay.direction;
        PRIORITY_TRACE_LIGHTING(queryTraversalFlags(rayQueryPurpose(mainRay), gl_RayFlagsCullBackFacingTrianglesEXT),
                    mask, 1, 1, 1, mainRay.origin, 0.0, mainRay.direction, imagingTraceLimit(mainRay, 1000.0), 0);
        if (priorityPending) {
            MaterialInfo material = rayLoadMaterial(mainRay);
            firstSurface.diffuse=vec4(material.albedoValue.rgb*material.albedoValue.a,material.metallic);
            firstSurface.specular=material.f0;
            firstSurface.normalRoughness=vec4(mainRay.normal,material.roughness);
        }
        priorityPending=false;
        if (rayShouldContinue(mainRay)) {
            if (++continuations >= RADIANCE_FREE_CONTINUATIONS || !rayContinuationProgress(mainRay,tracedOrigin,tracedDirection)) {raySetStop(mainRay,true);break;}
            --bounce;
            continue;
        }
        if (rayShouldStop(mainRay)) break;
    }
    return vec4(mainRay.radiance, alpha);
}
#endif
