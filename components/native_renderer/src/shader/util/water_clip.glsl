#ifndef WATER_CLIP_GLSL
#define WATER_CLIP_GLSL
#ifndef WATER_EXCLUSION_SET
#define WATER_EXCLUSION_SET 1
#define WATER_EXCLUSION_BINDING 12
#endif
layout(set = WATER_EXCLUSION_SET, binding = WATER_EXCLUSION_BINDING, std430) readonly buffer WaterExclusionData { vec4 records[]; } waterExclusion;
bool excludedWaterPosition(vec3 worldPosition) {
    uint count = floatBitsToUint(waterExclusion.records[0].x);
    uint offset = 1u;
    for (uint volume = 0u; volume < count; ++volume) {
        uint planes = floatBitsToUint(waterExclusion.records[offset].x);
        mat4 worldToLocal = mat4(waterExclusion.records[offset+1u], waterExclusion.records[offset+2u],
                                waterExclusion.records[offset+3u], waterExclusion.records[offset+4u]);
        vec4 localPosition = worldToLocal * vec4(worldPosition, 1.0);
        bool inside = true;
        for (uint plane = 0u; plane < planes; ++plane)
            inside = inside && dot(waterExclusion.records[offset+5u+plane], localPosition) <= 0.00002;
        if (inside) return true;
        offset += 5u + planes;
    }
    return false;
}
#endif
