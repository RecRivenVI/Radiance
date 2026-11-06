#ifndef SCENE_SAMPLE_KEY_GLSL
#define SCENE_SAMPLE_KEY_GLSL

// Row allocation is independent of the sequence used for stochastic coverage.
// Legacy layouts and older snapshots bind a single invalid key.
layout(set = 1, binding = 16, std430) readonly buffer SceneSampleKeyBuffer {
    uvec2 values[];
} sceneSampleKeys;

uint sceneSamplingGeometry(uint physicalGeometry) {
    uint slot = gl_InstanceCustomIndexEXT;
    if (slot >= uint(sceneSampleKeys.values.length())) return physicalGeometry;
    uint base = sceneSampleKeys.values[slot].y;
    return base == 0xFFFFFFFFu ? physicalGeometry : base + gl_GeometryIndexEXT;
}

uint sceneSamplingInstance(uint physicalInstance) {
    if (physicalInstance >= uint(sceneSampleKeys.values.length())) return physicalInstance;
    uint key = sceneSampleKeys.values[physicalInstance].x;
    return key == 0xFFFFFFFFu ? physicalInstance : key;
}

#endif
