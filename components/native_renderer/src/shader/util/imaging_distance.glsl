#ifndef IMAGING_DISTANCE_GLSL
#define IMAGING_DISTANCE_GLSL
// One random radius for the entire imaging chain. Independent per-face alpha would
// make stacks of distant walls opaque again and add any-hit work to opaque geometry.
float imagingRadius(float start, float end, float sampleValue) {
    // Invert F(t) = 1 - (1 - t)^2 so surviving geometry has coverage
    // (1 - t)^2: its decline slows toward the outer boundary.
    float progress = 1.0 - sqrt(1.0 - clamp(sampleValue, 0.0, 1.0));
    return mix(start, end, progress);
}

float imagingSphereExit(vec3 origin, vec3 direction, vec3 center, float radius) {
    vec3 delta = origin - center;
    float c = dot(delta, delta) - radius * radius;
    if (c >= 0.0) return 0.0;
    float b = dot(delta, direction);
    return max(0.0, -b + sqrt(max(b * b - c, 0.0)));
}

float imagingDistanceLimit(bool directImage, vec3 origin, vec3 direction, vec3 center,
                           float start, float end, float sampleValue, float transportLimit) {
    if (!directImage || end <= start || start <= 0.0) return transportLimit;
    return imagingSphereExit(origin, direction, center, imagingRadius(start, end, sampleValue));
}
#endif
