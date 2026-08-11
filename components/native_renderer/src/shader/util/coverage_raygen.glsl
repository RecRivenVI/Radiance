#if RADIANCE_COVERAGE_MODE == 1
#ifndef COVERAGE_TRACE_LAYER
#define COVERAGE_TRACE_LAYER(origin, incident) \
    traceRayEXT(topLevelAS, queryTraversalFlags(rayQueryPurpose(mainRay), COVERAGE_RAY_FLAGS), \
                queryMask(rayQueryPurpose(mainRay), worldUBO.isFirstPerson > 0), \
                1, 1, COVERAGE_MISS_INDEX, origin, 0.0, incident, 1000.0, 0)
#endif
// Every foreground runs the existing PT lighting path. The straight sibling keeps
// the original query ownership and its own immutable state, not the reflected ray.
vec3 traceCoverageBackground(MainRay source, vec3 position, vec3 direction, int isHand) {
    MainRay layerRay = source;
    layerRay.origin = offsetQueryOrigin(position, direction, direction);
    layerRay.direction = direction;
    layerRay.throughput = vec3(1.0);
    layerRay.radiance = vec3(0.0);
    raySetCaptureSurface(layerRay, false);
    vec3 radiance = vec3(0.0);
    float remaining = 1.0;
    for (int layer = 0; layer <= coverageDeterministicLayers; ++layer) {
        resetMainRay(layerRay, 0);
        // The final iteration is the original unbiased estimator, not a layer cutoff.
        raySetCoverageCapture(layerRay, layer < coverageDeterministicLayers);
        vec3 origin = layerRay.origin;
        vec3 incident = layerRay.direction;
        mainRay = layerRay;
        mainRay.stateBits = uiPrimaryTraceState(mainRay.stateBits, 0u);
        COVERAGE_TRACE_LAYER(origin, incident);
        mainRay.stateBits &= ~rayUiPrimaryBit;
        layerRay = mainRay;
        float alpha = rayLoadCoverage(layerRay);
        MainRay straightSource = layerRay;
        vec3 hitPosition = origin + incident * layerRay.hitT;
        if (alpha > 0.0 && layerRay.hitT != INF_DISTANCE) {
            completeCoverageLighting(layerRay, isHand);
        }
        accumulateCoverage(radiance, remaining, layerRay.radiance, alpha);
        if (remaining == 0.0) break;

        layerRay = straightSource;
        layerRay.origin = offsetQueryOrigin(hitPosition, incident, incident);
        layerRay.direction = incident;
        layerRay.radiance = vec3(0.0);
        layerRay.throughput = vec3(1.0);
        layerRay.seed = xxhash32(uvec3(straightSource.seed, uint(layer), 0x6A09E667u));
    }
    return radiance;
}
#endif
