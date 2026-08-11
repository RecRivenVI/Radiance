#ifndef RAY_MEDIUM_GLSL
#define RAY_MEDIUM_GLSL
const uint rayMediumInitializedBit = 1u << 26u;

void rayInitializeMedium(inout MainRay ray, float ior) {
    ray.stateBits |= rayMediumInitializedBit;
    ray.mediumDepth = ior > 1.0001 ? 1u : 0u;
    ray.currentMedium = vec4(0.0, 0.0, 0.0, ior);
    ray.enclosingMedium = vec4(0.0, 0.0, 0.0, 1.0);
    ray.previousBsdfPdf = 0.0;
    ray.scatterRoughness = 0.0;
    ray.lastScatterOrigin = ray.origin;
    ray.boundaryFlags = 0u;
    ray.boundaryEta = 1.0;
    ray.boundaryMedium = vec4(0.0, 0.0, 0.0, 1.0);
    ray.surfaceThroughput = ray.throughput;
}

vec4 rayCurrentMedium(MainRay ray) {
    if ((ray.stateBits & rayMediumInitializedBit) == 0u || ray.mediumDepth == 0u)
        return vec4(0.0, 0.0, 0.0, 1.0);
    return ray.currentMedium;
}

// Boundary evaluation does not change the stack. Reflection and transmission
// siblings copy the same incoming state and commit only their selected event.
void rayPrepareMedium(inout MainRay ray, float ior, vec3 absorption, bool entering, float distance) {
    if ((ray.stateBits & rayMediumInitializedBit) == 0u) rayInitializeMedium(ray, 1.0);
    vec4 current = rayCurrentMedium(ray);
    float targetIor = entering ? ior : ray.enclosingMedium.w;
    if (!entering && ray.mediumDepth==1u && abs(current.w-ior)<1e-4) {
        current.xyz=absorption;ray.currentMedium.xyz=absorption;
    }
    // A camera can start inside glass without crossing its entry boundary.
    if (!entering && ray.mediumDepth == 0u) {
        current = vec4(absorption, ior);
        ray.currentMedium = current;
        ray.enclosingMedium = vec4(0,0,0,1);
        ray.mediumDepth = 1u;
    }
    ray.throughput *= exp(-max(current.xyz, vec3(0.0)) * max(distance, 0.0));
    ray.surfaceThroughput = ray.throughput;
    ray.boundaryMedium = vec4(absorption, ior);
    ray.boundaryEta = current.w / max(targetIor, 1e-5);
    ray.boundaryFlags = entering ? 3u : 1u;
}

void rayCommitMedium(inout MainRay ray, inout MediumHistory history) {
    if ((ray.stateBits & rayMediumInitializedBit) == 0u || (ray.boundaryFlags & 1u) == 0u) return;
    // Stack indexing belongs to the ray-generation path, never to a hit shader.
    // Keep its two relevant values explicit across traceRay; committing the chosen
    // transmission is the only operation that changes the enclosing history.
    if (ray.mediumDepth != 0u) history.layers[ray.mediumDepth - 1u] = ray.currentMedium;
    if ((ray.boundaryFlags & 2u) != 0u) {
        if (ray.mediumDepth < uint(RADIANCE_MEDIUM_CAPACITY))
            history.layers[ray.mediumDepth++] = ray.boundaryMedium;
    } else if (ray.mediumDepth != 0u) {
        --ray.mediumDepth;
    }
    ray.currentMedium = ray.mediumDepth != 0u ? history.layers[ray.mediumDepth - 1u] : vec4(0,0,0,1);
    ray.enclosingMedium = ray.mediumDepth > 1u ? history.layers[ray.mediumDepth - 2u] : vec4(0,0,0,1);
    ray.boundaryFlags = 0u;
}

void rayPrepareOpaque(inout MainRay ray, float distance) {
    vec4 medium = rayCurrentMedium(ray);
    ray.throughput *= exp(-max(medium.xyz, vec3(0.0)) * max(distance, 0.0));
    ray.surfaceThroughput = ray.throughput;
    ray.boundaryFlags = 0u;
}

void rayPrepareSurfaceMedium(inout MainRay ray, float transmission, float ior, vec4 albedo,
                             uint faces, bool hardwareFront, vec3 waterOutward, float distance) {
    if (transmission <= 1e-6) { rayPrepareOpaque(ray, distance); return; }
    bool entering = hardwareFront != ((faces & FACE_HARDWARE_FRONT_FLIP_BIT) != 0u) !=
        ((faces & FACE_CLOCKWISE_BIT) != 0u);
    if (dot(waterOutward, waterOutward) > 1e-8)
        entering = dot(ray.direction, waterOutward) < 0.0;
    vec3 absorption = max(-log(max(albedo.rgb, vec3(1e-6))), vec3(0.0)) * albedo.a;
    rayPrepareMedium(ray, ior, absorption, entering, distance);
}

float rayEncodeBoundary(MainRay ray) {
    return (ray.boundaryFlags & 1u) == 0u ? 0.0 :
        ((ray.boundaryFlags & 2u) != 0u ? ray.boundaryEta : -ray.boundaryEta);
}

void rayCopyMedium(inout MainRay destination, MainRay source) {
    destination.stateBits |= rayMediumInitializedBit;
    destination.mediumDepth = source.mediumDepth;
    destination.currentMedium = source.currentMedium;
    destination.enclosingMedium = source.enclosingMedium;
}

void rayRestoreBoundary(inout MainRay ray, MaterialInfo material, float encoded) {
    ray.boundaryFlags = encoded == 0.0 ? 0u : (encoded > 0.0 ? 3u : 1u);
    ray.boundaryEta = encoded == 0.0 ? 1.0 : abs(encoded);
    vec3 absorption = max(-log(max(material.albedoValue.rgb, vec3(1e-6))), vec3(0.0)) * material.albedoValue.a;
    ray.boundaryMedium = vec4(absorption, material.ior);
}
#endif
