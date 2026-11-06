#ifndef MEDIUM_BOUNDARY_TRACE_GLSL
#define MEDIUM_BOUNDARY_TRACE_GLSL
#include "util/medium_boundary_state.glsl"
#include "util/water_material.glsl"
#include "util/water_clip.glsl"
#include "util/ray_cone.glsl"
#extension GL_EXT_ray_query : require
#include "util/vertex.glsl"
#include "util/world_material_faces.glsl"
#include "util/medium_probe_hit.glsl"

void traceMediumBoundaries(vec3 origin, vec3 direction, float length, uint sourceGeometry, uint sourcePrimitive, vec3 pathDirection) {
    if ((shadowRay.queryPadding & 3u)==0u || (shadowRay.queryPadding & 3u)==2u) { observeMediumContact(2u); }
    else { observeMediumContact(7u); }
    rayQueryEXT query;
    // Production transmission geometry is non-opaque (ModelRules::needsAnyHit).
    // Opaque obstacles inside a medium are not boundaries of that medium.
    uint flags = gl_RayFlagsCullOpaqueEXT;
    if ((shadowRay.queryPadding & 3u) == 2u) flags |= gl_RayFlagsTerminateOnFirstHitEXT;
    rayQueryInitializeEXT(query,topLevelAS,flags,
        queryMask(queryTransport,true),origin,0.0,direction,length);
    while(rayQueryProceedEXT(query)) {
        if(rayQueryGetIntersectionTypeEXT(query,false)!=gl_RayQueryCandidateIntersectionTriangleEXT) continue;
        observeMediumContact(3u);
        uint geometry=getGeometryBufferIndex(rayQueryGetIntersectionInstanceCustomIndexEXT(query,false),
            rayQueryGetIntersectionGeometryIndexEXT(query,false));
        uint primitive=rayQueryGetIntersectionPrimitiveIndexEXT(query,false);
        // Mixed-facing BLASes also make ordinary opaque geometries non-opaque
        // for camera ownership. Their declared material role still excludes
        // them from medium queries; do not decode every such candidate.
        InstanceAppearance appearance=instanceAppearances.values[geometry];
        if ((appearance.materialFlags & (FACE_MEDIUM_BOUNDARY_BIT | FACE_SURFACE_LAYERS_BIT)) == 0u &&
            (appearance.flags & APPEARANCE_LOD) == 0u) continue;
        // The accepted source triangle cannot also be its opposite boundary.
        // Avoid decoding and sampling that inevitable self candidate again.
        if (geometry == sourceGeometry && primitive == sourcePrimitive) continue;
        vec2 uv=rayQueryGetIntersectionBarycentricsEXT(query,false);
        vec3 bary=vec3(1.0-uv.x-uv.y,uv);
        if(lodCovered(geometry,primitive,bary)) continue;
        uint i0,i1,i2;PositionVertex p0,p1,p2;MaterialVertex m0,m1,m2;
        loadTriangle(geometry,primitive,i0,i1,i2,p0,p1,p2,m0,m1,m2);
        observeMediumContact(4u);
        if(collectMediumBoundary(geometry,primitive,bary,rayQueryGetIntersectionFrontFaceEXT(query,false),
            rayQueryGetIntersectionTEXT(query,false),mat3(rayQueryGetIntersectionObjectToWorldEXT(query,false)),
            transpose(mat3(rayQueryGetIntersectionWorldToObjectEXT(query,false))),p0,p1,p2,m0,m1,m2,
            origin,direction,pathDirection,worldUBO.overlayTextureID,shadowRay)) rayQueryConfirmIntersectionEXT(query);
    }
}

void traceMediumBoundaries(vec3 origin, vec3 direction, float length) {
    traceMediumBoundaries(origin,direction,length,~0u,~0u,direction);
}

void initializeCameraMedium(inout MainRay ray, SkyUBO sky) {
    if (sky.cameraSubmersionType == 1u && excludedWaterPosition(ray.origin)) {
        rayInitializeMedium(ray,1.0);
        return;
    }
    if (sky.cameraSubmersionType == 1u && sky.cameraMediumSource.z >= 0.0) {
        // Water's optical colour is shared with applyWaterMaterial. The camera
        // uses the actual fluid atlas alpha, not a fog coefficient or zero sigma.
        float alpha = textureLod(textures[nonuniformEXT(int(sky.cameraMediumSource.z))], sky.cameraMediumSource.xy, 0.0).a * sky.cameraMediumSource.w;
        rayInitializeMedium(ray, vec4(mediumAbsorption(vec4(waterOpticalTint,alpha)),waterOpticalIor));
        return;
    }
    if (sky.cameraSubmersionType != 1u && sky.cameraMediumProbe == 0u) return;
    shadowRay.queryPadding = mediumProbeTag | (sky.cameraSubmersionType == 1u ? 3u : 1u);
    shadowRay.pad0 = 0u;
    shadowRay.radiance = vec3(0);
    shadowRay.throughput = vec3(1, INF_DISTANCE, 0);
    // A stable direction gives every pixel the same camera-medium definition.
    // Opaque objects inside the volume do not hide its containing boundary.
    traceMediumBoundaries(ray.origin, normalize(vec3(.237,.893,.381)), 32000.0);
    if (shadowRay.throughput.z < 0.0) {
        rayInitializeMedium(ray, vec4(shadowRay.radiance,shadowRay.throughput.x));
    }
}

bool findContactBoundary(vec3 pathDirection, vec3 position, vec3 geometricNormal, float footprint, bool entering, uint geometry, uint primitive) {
    // Only inspect the numerical neighbourhood of this boundary. A real air
    // gap or another legitimate water interface is never skipped afterwards.
    float scale = max(abs(position.x),max(abs(position.y),abs(position.z)));
    float epsilon = max(0.000025, scale * 0.0000002384185791015625);
    shadowRay.queryPadding = mediumProbeTag | (entering ? 2u : 0u);
    shadowRay.pad0 = 0u;
    shadowRay.radiance = vec3(0);
    shadowRay.throughput = vec3(1,0,footprint);
    // Probe across the plane, even at grazing incidence. The optical event is
    // still classified with the original path direction, not this probe ray.
    traceMediumBoundaries(position+geometricNormal*epsilon,-geometricNormal,epsilon*2.0,geometry,primitive,pathDirection);
    return shadowRay.pad0 != 0u;
}

// The generic hit shaders return before BSDF sampling when a contact needs
// resolution. Resolve it here, then resume the same accepted primitive.
void traceImagingRay(uint flags, uint mask, uint offset, uint stride, uint miss,
    vec3 origin, float minimum, vec3 direction, float maximum, int payload) {
    float coneWidth = mainRay.coneWidth;
    uint incomingAux = mainRay.pad0;
#if RADIANCE_SER_MODE > 0 && !SHARC_UPDATE
    if (rayBounce(mainRay) == 0u && rayDirectImage(mainRay) && !rayReconstructionQuery(mainRay) &&
        !rayCoverageCapture(mainRay) && !rayPrioritySelection(mainRay) && rayMediumDepth(mainRay) == 0u) {
#if RADIANCE_SER_MODE >= 3
        hitObjectEXT hit;
#if RADIANCE_SER_MODE == 4
        hitObjectTraceReorderExecuteEXT(hit, topLevelAS, flags, mask, offset, stride, miss,
            origin, minimum, direction, maximum, 0);
#else
        hitObjectTraceRayEXT(hit, topLevelAS, flags, mask, offset, stride, miss, origin, minimum, direction, maximum, 0);
        hitObjectExecuteShaderEXT(hit, 0);
#endif
#else
        hitObjectNV hit;
        hitObjectTraceRayNV(hit, topLevelAS, flags, mask, offset, stride, miss, origin, minimum, direction, maximum, 0);
#if RADIANCE_SER_MODE == 2
        // Use the encoded hit only. No additional SBT, material or device-address
        // lookups cross the reorder point.
        reorderThreadNV(hit);
#endif
        hitObjectExecuteShaderNV(hit, 0);
#endif
    } else
#endif
    traceRayEXT(topLevelAS, flags, mask, offset, stride, miss, origin, minimum, direction, maximum, 0);
    if ((mainRay.boundaryFlags & mediumContactPending) == 0u) return;
    uint request=mainRay.boundaryFlags;
    uint geometry=(request >> 3u) & mediumContactGeometryMask;
    uint primitive=mainRay.hasPrevScenePos;
    bool entering=(request & 2u)!=0u;
    vec3 position=origin+direction*mainRay.boundaryEta;
    bool contact=findContactBoundary(direction,position,mainRay.geometricNormal,
        mainRay.coneWidth,entering,geometry,primitive);
    mainRay.boundaryFlags &= 7u;
    mainRay.boundaryEta=rayCurrentMedium(mainRay).w / max(entering ? mainRay.boundaryMedium.w : mainRay.enclosingMedium.w,1e-5);
    if (contact) {
        observeMediumContact(5u);
        rayResolveContact(mainRay,entering,vec4(shadowRay.radiance,shadowRay.throughput.x));
    }
    mainRay.boundaryFlags |= mediumContactResume | (geometry << 3u);
    mainRay.hasPrevScenePos=primitive;
    mainRay.coneWidth=coneWidth;
    mainRay.pad0=incomingAux;
    observeMediumContact(6u);
    traceRayEXT(topLevelAS, (flags & ~gl_RayFlagsOpaqueEXT) | gl_RayFlagsNoOpaqueEXT,
        mask, offset, stride, miss, origin, minimum, direction, maximum, 0);
}
#endif
