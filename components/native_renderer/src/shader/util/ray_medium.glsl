#ifndef RAY_MEDIUM_GLSL
#define RAY_MEDIUM_GLSL
const uint mediumProbeTag = 0x4D454440u;
bool isMediumProbe(ShadowRay ray) { return (ray.queryPadding & ~3u) == mediumProbeTag; }
const uint rayMediumInitializedBit = 1u << 26u;
const uint mediumDepthMask = 0x1ffu;
const uint reflectionShift = 9u;
const uint refractionShift = 17u;
const uint pathStepHighShift = 26u;

// A split preserves its first surface in one bank and traces sibling paths in
// another. Passing the path state between functions must not copy either bank.
struct MediumAncestor {
    vec4 optical;
    uint repetitions;
};
MediumAncestor mediumAncestors[3][RADIANCE_MEDIUM_CAPACITY - 1];

uint rayMediumDepth(MainRay ray) { return ray.mediumState & mediumDepthMask; }
MediumHistory forkMediumHistory(MainRay ray, MediumHistory history, uint bank) {
    uint depth = rayMediumDepth(ray);
    for (uint i = 1u; i < depth; ++i)
        mediumAncestors[bank][i - 1u] = mediumAncestors[history.bank][i - 1u];
    return MediumHistory(bank, history.topRepetitions);
}

void raySetMediumDepth(inout MainRay ray, uint depth) {
    ray.mediumState = (ray.mediumState & ~mediumDepthMask) | depth;
}
uint rayReflectionCount(MainRay ray) { return (ray.mediumState >> reflectionShift) & 0xffu; }
uint rayRefractionCount(MainRay ray) { return (ray.mediumState >> refractionShift) & 0x1ffu; }

void rayInitializeMedium(inout MainRay ray, float ior) {
    ray.geometricNormal = vec3(0);
    ray.stateBits |= rayMediumInitializedBit;
    ray.mediumState = ior > 1.0001 ? 1u : 0u;
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

void rayInitializeMedium(inout MainRay ray, vec4 opticalMedium) {
    rayInitializeMedium(ray, opticalMedium.w);
    ray.currentMedium = opticalMedium;
}

vec4 rayCurrentMedium(MainRay ray) {
    if ((ray.stateBits & rayMediumInitializedBit) == 0u || rayMediumDepth(ray) == 0u)
        return vec4(0.0, 0.0, 0.0, 1.0);
    return ray.currentMedium;
}

// Boundary evaluation does not change the stack. Reflection and transmission
// siblings copy the same incoming state and commit only their selected event.
void rayPrepareMedium(inout MainRay ray, float ior, vec3 absorption, bool entering, float distance) {
    if ((ray.stateBits & rayMediumInitializedBit) == 0u) rayInitializeMedium(ray, 1.0);
    vec4 current = rayCurrentMedium(ray);
    float targetIor = entering ? ior : ray.enclosingMedium.w;
    uint depth = rayMediumDepth(ray);
    if (!entering && depth == 1u && abs(current.w-ior)<1e-4) {
        current.xyz=absorption;ray.currentMedium.xyz=absorption;
    }
    // A camera can start inside glass without crossing its entry boundary.
    if (!entering && depth == 0u) {
        current = vec4(absorption, ior);
        ray.currentMedium = current;
        ray.enclosingMedium = vec4(0,0,0,1);
        raySetMediumDepth(ray, 1u);
    }
    ray.throughput *= exp(-max(current.xyz, vec3(0.0)) * max(distance, 0.0));
    ray.surfaceThroughput = ray.throughput;
    ray.boundaryMedium = vec4(absorption, ior);
    ray.boundaryEta = current.w / max(targetIor, 1e-5);
    ray.boundaryFlags = entering ? 3u : 1u;
}

void rayResolveContact(inout MainRay ray, bool entering, vec4 other) {
    if (!entering) ray.boundaryMedium = other;
    ray.boundaryEta = rayCurrentMedium(ray).w / max(ray.boundaryMedium.w,1e-5);
    ray.boundaryFlags = 7u;
}

bool rayCommitMedium(inout MainRay ray, inout MediumHistory history) {
    if ((ray.stateBits & rayMediumInitializedBit) == 0u || (ray.boundaryFlags & 1u) == 0u) return true;
    uint depth = rayMediumDepth(ray);
    bool replace = (ray.boundaryFlags & 4u) != 0u && depth != 0u;
    bool enter = (ray.boundaryFlags & 2u) != 0u;
    vec4 target = enter ? ray.boundaryMedium : ray.enclosingMedium;
    const vec4 air = vec4(0,0,0,1);
    bool sameMedium = target == rayCurrentMedium(ray);
    bool changesMedium = target != air && !sameMedium;
    if (changesMedium && rayRefractionCount(ray) >= uint(RADIANCE_REFRACTION_LIMIT)) return false;
    // Nested identical media need their geometric exit count, but they don't
    // consume the optical budget or another distinct layer.
    if (replace && !sameMedium && history.topRepetitions != 0u) {
        if (depth >= uint(RADIANCE_MEDIUM_CAPACITY)) return false;
        --history.topRepetitions;
        replace = false;
    } else if (enter && !replace && sameMedium) {
        if (depth != 0u) {
            ++history.topRepetitions;
            ray.enclosingMedium = ray.currentMedium;
        }
        ray.boundaryFlags = 0u;
        return true;
    }
    if (!replace && enter && depth >= uint(RADIANCE_MEDIUM_CAPACITY)) return false;
    // Stack indexing belongs to the ray-generation path, never to a hit shader.
    // Keep its two relevant values explicit across traceRay; committing the chosen
    // transmission is the only operation that changes the enclosing history.
    if (replace) {
        ray.currentMedium = target;
    } else if (enter) {
        if (depth != 0u)
            mediumAncestors[history.bank][depth - 1u] = MediumAncestor(ray.currentMedium, history.topRepetitions);
        ++depth;
        ray.currentMedium = target;
        history.topRepetitions = 0u;
    } else if (depth != 0u) {
        if (history.topRepetitions != 0u) --history.topRepetitions;
        else {
            --depth;
            if (depth != 0u) {
                MediumAncestor ancestor = mediumAncestors[history.bank][depth - 1u];
                ray.currentMedium = ancestor.optical;
                history.topRepetitions = ancestor.repetitions;
            } else {
                ray.currentMedium = air;
            }
        }
    }
    raySetMediumDepth(ray, depth);
    if (changesMedium) ray.mediumState += 1u << refractionShift;
    ray.enclosingMedium = history.topRepetitions != 0u ? ray.currentMedium :
        depth > 1u ? mediumAncestors[history.bank][depth - 2u].optical : air;
    ray.boundaryFlags = 0u;
    return true;
}

void rayPrepareOpaque(inout MainRay ray, float distance) {
    vec4 medium = rayCurrentMedium(ray);
    ray.throughput *= exp(-max(medium.xyz, vec3(0.0)) * max(distance, 0.0));
    ray.surfaceThroughput = ray.throughput;
    ray.boundaryFlags = 0u;
}

vec3 mediumAbsorption(vec4 albedo) {
    return max(-log(max(albedo.rgb, vec3(1e-6))), vec3(0.0)) * albedo.a;
}

bool surfaceEntersMedium(vec3 direction, uint faces, bool hardwareFront, vec3 outward) {
    bool front = hardwareFront != ((faces & FACE_HARDWARE_FRONT_FLIP_BIT) != 0u) !=
        ((faces & FACE_CLOCKWISE_BIT) != 0u);
    return dot(outward, outward) > 1e-8 ? dot(direction, outward) < 0.0 : front;
}

void rayPrepareSurfaceMedium(inout MainRay ray, float transmission, float ior, vec4 albedo,
                             uint faces, bool hardwareFront, vec3 waterOutward, float distance) {
    if (transmission <= 1e-6) { rayPrepareOpaque(ray, distance); return; }
    bool entering = surfaceEntersMedium(ray.direction, faces, hardwareFront, waterOutward);
    vec3 absorption = mediumAbsorption(albedo);
    rayPrepareMedium(ray, ior, absorption, entering, distance);
}

void rayCopyMedium(inout MainRay destination, MainRay source) {
    destination.stateBits |= rayMediumInitializedBit;
    destination.mediumState = source.mediumState;
    destination.currentMedium = source.currentMedium;
    destination.enclosingMedium = source.enclosingMedium;
}

// The split prelude crosses at most one boundary after camera initialization.
// Deeper paths retain their complete private history instead of serializing it.
void rayRestoreMediumState(inout MainRay ray, inout MediumHistory history, vec4 current, vec4 enclosing, vec4 geometryFlags, vec4 target) {
    uint flags = uint(round(geometryFlags.w));
    uint depth = flags / 8u;
    raySetMediumDepth(ray, depth);
    ray.boundaryFlags = flags & 7u;
    ray.currentMedium = current;
    ray.enclosingMedium = enclosing;
    ray.geometricNormal = geometryFlags.xyz;
    ray.boundaryMedium = target;
    float nextIor = (ray.boundaryFlags & 2u) != 0u ? target.w : enclosing.w;
    ray.boundaryEta = (ray.boundaryFlags & 1u) != 0u ? rayCurrentMedium(ray).w / max(nextIor,1e-5) : 1.0;
    if (depth > 1u && history.topRepetitions == 0u)
        mediumAncestors[history.bank][depth - 2u].optical = enclosing;
}
#endif
