struct ReconstructionSurface {
    vec4 normalRoughness;
    vec4 motionDepth;
    vec3 diffuse;
    vec3 specular;
};

ReconstructionSurface emptyReconstructionSurface() {
    return ReconstructionSurface(vec4(0), vec4(0), vec3(0), vec3(0));
}

ReconstructionSurface reconstructionSurfaceFromHit(MainRay hit, MaterialInfo material,
    vec3 position, vec3 incident, mat3 currentTransform, vec3 currentTranslation,
    mat3 previousTransform, vec3 previousTranslation, vec2 pixelCenter, vec3 cameraDelta) {
    vec3 virtualPosition = currentTransform * position + currentTranslation;
    vec3 previousPosition = hit.hasPrevScenePos != 0u ? hit.prevScenePos : position + cameraDelta;
    vec3 virtualPrevious = previousTransform * previousPosition + previousTranslation;
    vec2 motion = computeCameraMotionVector(lastWorldUBO.cameraProjMat * lastWorldUBO.cameraEffectedViewMat,
        pixelCenter, vec4(virtualPrevious, 1));
    float depth = -(worldUBO.cameraEffectedViewMat * vec4(virtualPosition, 1)).z;
    ReconstructionSurface guide;
    guide.normalRoughness = vec4(normalize(currentTransform * hit.normal), material.roughness);
    guide.motionDepth = vec4(motion, clamp(depth, 1e-3, 60000.0), 1.0);
    guide.diffuse = material.albedoValue.rgb * (1.0 - material.metallic) * (1.0 - material.transmission);
    guide.specular = reconstructionSpecularReflectance(material.f0, material.roughness,
        abs(dot(hit.normal, -incident)));
    return guide;
}

ReconstructionSurface reconstructionSurfaceFromSky(vec3 position, vec3 direction,
    mat3 currentTransform, vec3 currentTranslation, mat3 previousTransform, vec3 previousTranslation,
    vec2 pixelCenter, vec3 cameraDelta) {
    vec3 virtualPosition = currentTransform * position + currentTranslation;
    vec3 virtualPrevious = previousTransform * (position + cameraDelta) + previousTranslation;
    ReconstructionSurface guide;
    guide.normalRoughness = vec4(normalize(-currentTransform * direction), 0.02);
    guide.motionDepth = vec4(computeCameraMotionVector(lastWorldUBO.cameraProjMat * lastWorldUBO.cameraEffectedViewMat,
        pixelCenter, vec4(virtualPrevious, 1)),
        clamp(-(worldUBO.cameraEffectedViewMat * vec4(virtualPosition, 1)).z, 1e-3, 60000.0), 1);
    guide.diffuse = vec3(1);
    guide.specular = vec3(1);
    return guide;
}

ReconstructionSurface traceReconstructionSurface(MainRay source, MediumHistory sourceHistory,
    MaterialInfo firstMaterial, vec3 firstPosition, vec3 firstIncoming, bool reflection,
    vec3 cameraOrigin, vec3 cameraDirection, vec2 pixelCenter, vec3 cameraDelta, vec3 firstPreviousNormal, int isHand) {
    ReconstructionSurface guide = emptyReconstructionSurface();
    mat3 currentTransform = mat3(1), previousTransform = mat3(1);
    vec3 currentTranslation = vec3(0), previousTranslation = vec3(0);
    // Previous scene positions are already relative to the previous camera.
    vec3 previousOrigin = cameraOrigin;
    vec3 position = firstPosition;
    vec3 incident = normalize(firstIncoming);
    mainRay = source;
    MediumHistory history = forkMediumHistory(source, sourceHistory, 2u);
    MaterialInfo material = firstMaterial;

    // This only explores the existing delta prefix, not additional light bounces.
    // Both branches get continuous guides even when radiance samples one lobe.
    for (int step = 0; step <= RADIANCE_PATH_EVENTS; ++step) {
        if (step > 0 && (material.transmission <= EPS || material.roughness > 0.08)) {
            return reconstructionSurfaceFromHit(mainRay, material, position, incident,
                currentTransform, currentTranslation, previousTransform, previousTranslation, pixelCenter, cameraDelta);
        }
        if (step == RADIANCE_PATH_EVENTS) break;

        vec3 normal = normalize(mainRay.normal);
        vec3 previousPosition = mainRay.hasPrevScenePos != 0u ? mainRay.prevScenePos : position + cameraDelta;
        vec4 previousNormalData = imageLoad(rayMaterialStateImage, rayMaterialStateCoord(3));
        vec3 previousNormal = step == 0 ? firstPreviousNormal :
            previousNormalData.w > 0.5 ? normalize(previousNormalData.xyz) : normal;
        vec3 previousIncoming = normalize(previousPosition - previousOrigin);
        float eta = mainRay.boundaryEta;
        bool reflectHere = step == 0 ? reflection : DielectricFresnel(abs(dot(incident, normal)), eta) >= 0.5;
        vec3 outgoing = reflectHere ? reflect(incident, normal) : refract(incident, normal, eta);
        vec3 previousOutgoing = reflectHere ? reflect(previousIncoming, previousNormal) :
            refract(previousIncoming, previousNormal, eta);
        // A branch that does not exist (TIR) must not supply a fabricated guide.
        if (dot(outgoing, outgoing) < 1e-8 || dot(previousOutgoing, previousOutgoing) < 1e-8 ||
            !dielectricDirectionOnSide(outgoing, mainRay.geometricNormal, reflectHere)) return guide;
        outgoing = normalize(outgoing);
        previousOutgoing = normalize(previousOutgoing);
        reconstructionUnfold(currentTransform, currentTranslation,
            reflectHere ? reconstructionReflection(normal) : reconstructionRotation(outgoing, incident), position);
        reconstructionUnfold(previousTransform, previousTranslation,
            reflectHere ? reconstructionReflection(previousNormal) : reconstructionRotation(previousOutgoing, previousIncoming),
            previousPosition);
        previousOrigin = previousPosition;
        mainRay.origin = offsetQueryOrigin(position, mainRay.geometricNormal, outgoing);
        mainRay.direction = outgoing;
        raySetLobeType(mainRay, reflectHere ? 1u : 2u);
        if (!rayAdvanceQuery(mainRay, history)) return guide;
        resetMainRay(mainRay, step + 1);
        mainRay.stateBits |= rayReconstructionQueryBit;
        mainRay.stateBits &= ~(rayPrimaryEmissionCaptureBit | rayCaptureSurfaceBit | rayPriorityCaptureBit |
            rayPriorityShadeBit | rayCoverageCaptureBit);
        mainRay.throughput = vec3(1);
        mainRay.radiance = vec3(0);
        mainRay.seed = xxhash32(uvec3(gl_LaunchIDEXT.xy, worldUBO.samplingSeed ^ uint(step)));
        rayStoreMaterial(mainRay, vec4(1), vec3(0.04), 1.0, 0.0, 0.0, 1.0, 0.0);
        imageStore(rayMaterialStateImage, rayMaterialStateCoord(3), vec4(0));
        float len;
        uint mask;
        prepareSecondary(step + 1, rayQueryPurpose(mainRay), isHand, rayReceiveOnlyReceiver(mainRay), len, mask);
        vec3 tracedOrigin = mainRay.origin;
        traceImagingRay( queryTraversalFlags(rayQueryPurpose(mainRay), VPT_RAY_FLAGS), mask,
            1, 1, VPT_WORLD_NO_VOLUMETRIC_MISS_INDEX, mainRay.origin, 0.0, outgoing,
            imagingTraceLimit(mainRay, len), 0);
        if (rayShouldContinue(mainRay)) return guide; // keep the original guide for non-surface continuation effects
        if (mainRay.hitT == INF_DISTANCE) {
            return reconstructionSurfaceFromSky(tracedOrigin + outgoing * 32000.0, outgoing,
                currentTransform, currentTranslation, previousTransform, previousTranslation, pixelCenter, cameraDelta);
        }
        position = tracedOrigin + outgoing * mainRay.hitT;
        incident = outgoing;
        material = rayLoadMaterial(mainRay);
    }
    return guide;
}
