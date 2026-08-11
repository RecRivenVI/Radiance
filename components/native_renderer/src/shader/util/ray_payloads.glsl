#ifndef RAY_PAYLOAD_GLSL
#define RAY_PAYLOAD_GLSL

#include "common/mapping.hpp"

// A path cannot enter more media than it has surface bounces. Size the stack
// with the algorithm instead of imposing a smaller, silent nesting limit.
#if defined(VPT_NUM_RAY_BOUNCES)
#define RADIANCE_MEDIUM_CAPACITY (VPT_NUM_RAY_BOUNCES + 1)
#elif defined(ADV_NUM_RAY_BOUNCES)
#define RADIANCE_MEDIUM_CAPACITY (ADV_NUM_RAY_BOUNCES + 1)
#else
#define RADIANCE_MEDIUM_CAPACITY 8
#endif

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

    T_VEC3 directLightRadiance;
    T_UINT pad0;

    T_VEC4 currentMedium;
    T_VEC4 enclosingMedium;
    T_VEC4 boundaryMedium;
    T_VEC3 surfaceThroughput;
    T_FLOAT boundaryEta;
    T_UINT mediumDepth;
    T_UINT boundaryFlags;
    T_FLOAT previousBsdfPdf;
    T_VEC3 lastScatterOrigin;
    T_FLOAT scatterRoughness;
};

// Private path history stays in the ray generator. Hit shaders exchange only the
// current/enclosing medium and selected boundary event through MainRay.
struct MediumHistory {
    T_VEC4 layers[RADIANCE_MEDIUM_CAPACITY];
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
