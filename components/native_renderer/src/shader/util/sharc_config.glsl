#ifndef SHARC_CONFIG_GLSL
#define SHARC_CONFIG_GLSL
#if USE_SHARC
#define SHARC_ENABLE_GLSL 1
#define SHARC_ENABLE_64_BIT_ATOMICS 1
#include "extern/sharc/include/SharcGlsl.h"
#include "extern/sharc/include/SharcCommon.h"
struct SharcConfig {
    uvec2 hashEntriesAddress;
    uvec2 lockAddress;
    uvec2 accumulationAddress;
    uvec2 resolvedAddress;
    vec4 cameraPosition;
    vec4 cameraPositionPrev;
    float sceneScale;
    float radianceScale;
    uint accumulationFrameNum;
    uint staleFrameNumMax;
    uint capacity;
    uint frameIndex;
    uint enableAntiFireflyFilter;
    uint useLockBuffer;
    uint debugMode;
    uint updateDownsampleFactor;
    uvec2 auditAddress;
};

layout(set = 4, binding = 0) readonly buffer SharcConfigBuffer {
    SharcConfig sharcConfig;
};
layout(set = 4, binding = 1) readonly buffer SharcHashEntriesBuffer {
    uint64_t sharcHashEntriesRaw[];
};
layout(set = 4, binding = 2) readonly buffer SharcAccumulationBuffer {
    uvec4 sharcAccumulationRaw[];
};
layout(set = 4, binding = 3) readonly buffer SharcResolvedBuffer {
    uvec4 sharcResolvedRaw[];
};
layout(set = 4, binding = 4) readonly buffer SharcLockBuffer {
    uint sharcLockRaw[];
};
SharcParameters makeSharcParameters() {
    HashMapData hashMapData;
    hashMapData.capacity = sharcConfig.capacity;
    hashMapData.hashEntriesBuffer = RWStructuredBuffer_uint64_t(sharcConfig.hashEntriesAddress);
#    if !HASH_GRID_ENABLE_64_BIT_ATOMICS
    hashMapData.lockBuffer = RWStructuredBuffer_uint(sharcConfig.lockAddress);
#    endif

    HashGridParameters gridParameters;
    gridParameters.cameraPosition = sharcConfig.cameraPosition.xyz;
    gridParameters.logarithmBase = SHARC_GRID_LOGARITHM_BASE;
    gridParameters.sceneScale = sharcConfig.sceneScale;
    gridParameters.levelBias = SHARC_GRID_LEVEL_BIAS;

    SharcParameters sharcParameters;
    sharcParameters.gridParameters = gridParameters;
    sharcParameters.hashMapData = hashMapData;
    sharcParameters.radianceScale = sharcConfig.radianceScale;
    sharcParameters.enableAntiFireflyFilter = sharcConfig.enableAntiFireflyFilter != 0;
    sharcParameters.accumulationBuffer = RWStructuredBuffer_SharcAccumulationData(sharcConfig.accumulationAddress);
    sharcParameters.resolvedBuffer = RWStructuredBuffer_SharcPackedData(sharcConfig.resolvedAddress);
    return sharcParameters;
}

#endif
#endif
