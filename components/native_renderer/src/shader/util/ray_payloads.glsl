#ifndef RAY_PAYLOAD_GLSL
#define RAY_PAYLOAD_GLSL

#include "common/mapping.hpp"

#ifndef RADIANCE_REFLECTION_LIMIT
#define RADIANCE_REFLECTION_LIMIT 4
#endif
#ifndef RADIANCE_REFRACTION_LIMIT
#define RADIANCE_REFRACTION_LIMIT 8
#endif

#define RADIANCE_MEDIUM_CAPACITY (RADIANCE_REFRACTION_LIMIT + 1)
#define RADIANCE_PATH_EVENTS (RADIANCE_REFLECTION_LIMIT + 2 * RADIANCE_REFRACTION_LIMIT + RADIANCE_MEDIUM_CAPACITY)
#define RADIANCE_FREE_CONTINUATIONS 256

struct MainRay {
    T_VEC3 origin;
    T_FLOAT hitT;

    T_VEC3 direction;
    T_UINT seed;

    T_VEC3 radiance;
    T_FLOAT coneWidth;

    T_VEC3 throughput;
    T_FLOAT coneSpread;

    T_VEC3 prevScenePos;
    T_UINT hasPrevScenePos;

    T_VEC3 normal;
    T_UINT stateBits;

    T_VEC3 geometricNormal;

    T_VEC3 directLightRadiance;
    T_UINT pad0;

    T_VEC4 currentMedium;
    T_VEC4 enclosingMedium;
    T_VEC4 boundaryMedium;
    T_VEC3 surfaceThroughput;
    T_FLOAT boundaryEta;
    T_UINT mediumState;
    T_UINT boundaryFlags;
    T_FLOAT previousBsdfPdf;
    T_VEC3 lastScatterOrigin;
    T_FLOAT scatterRoughness;
};

// The current medium lives in MainRay. Ancestors are private to the invocation.
struct MediumHistory {
    T_UINT bank;
    T_UINT topRepetitions;
};

struct MaterialInfo {
    T_VEC4 albedoValue;
    T_VEC3 f0;
    T_FLOAT roughness;
    T_FLOAT metallic;
    T_FLOAT transmission;
    T_FLOAT ior;
    T_FLOAT emission;
};

struct ShadowRay {
    T_VEC3 radiance;
    T_UINT queryPadding;
    T_VEC3 throughput;
    T_UINT pad0;
};

#endif
