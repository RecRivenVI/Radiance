#ifndef MEDIUM_BOUNDARY_STATE_GLSL
#define MEDIUM_BOUNDARY_STATE_GLSL
// Boundary requests reuse fields whose hit result is not yet published. The
// resume trace recomputes hit history; no sampled surface crosses the payload.
const uint mediumContactPending = 0x80000000u;
const uint mediumContactResume = 0x40000000u;
const uint mediumContactGeometryMask = 0x07ffffffu;

#include "util/medium_contact_filter.glsl"
#include "util/medium_contact_observe.glsl"

bool prepareImagingMedium(inout MainRay ray, float transmission, float ior, vec4 albedo,
    uint faces, bool hardwareFront, vec3 outward, vec3 position, vec3 geometricNormal,
    float distance, uint geometry, uint primitive, vec3 localPosition, vec3 localUncertainty, vec3 planeNormal, float planeDistance) {
    if ((ray.boundaryFlags & mediumContactResume) != 0u) {
        ray.boundaryFlags &= 7u;
        ray.geometricNormal = geometricNormal;
        return false;
    }
    uint incomingDepth = rayMediumDepth(ray);
    rayPrepareSurfaceMedium(ray,transmission,ior,albedo,faces,hardwareFront,outward,distance);
    ray.geometricNormal = geometricNormal;
    if (transmission <= 1e-6 || incomingDepth == 0u) return false;
    observeMediumContact(0u);
    if (!potentialMediumContact(geometry, primitive, localPosition, localUncertainty)) {
        observeMediumContact(1u);
        return false;
    }
    ray.boundaryFlags |= mediumContactPending | (geometry << 3u);
    ray.boundaryEta = planeDistance;
    ray.hasPrevScenePos = primitive;
    ray.geometricNormal = planeNormal;
    return true;
}
#endif
