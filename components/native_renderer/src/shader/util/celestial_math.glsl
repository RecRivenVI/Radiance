#ifndef CELESTIAL_MATH_GLSL
#define CELESTIAL_MATH_GLSL
vec3 celestialNormalize(vec3 direction, vec3 fallback) {
    float len2 = dot(direction, direction);
    if (len2 <= 1e-8 || any(isnan(direction)) || any(isinf(direction))) { return fallback; }
    return direction * inversesqrt(len2);
}

vec3 celestialApplySouthOffset(vec3 direction) {
#ifndef RADIANCE_CELESTIAL_TILT
#define RADIANCE_CELESTIAL_TILT 10.0
#endif
    float southOffsetSin = sin(radians(RADIANCE_CELESTIAL_TILT));
    float southOffsetCos = cos(radians(RADIANCE_CELESTIAL_TILT));
    vec3 rotatedDirection = vec3(direction.x,
                                 direction.y * southOffsetCos - direction.z * southOffsetSin,
                                 direction.y * southOffsetSin + direction.z * southOffsetCos);
    return celestialNormalize(rotatedDirection, vec3(0.0, 1.0, 0.0));
}

vec3 celestialSunFromSource(vec3 source) {
    return celestialApplySouthOffset(celestialNormalize(source, vec3(0.0, 1.0, 0.0)));
}
vec3 celestialMoonFromSource(vec3 source) { return -celestialSunFromSource(source); }
// Minecraft has no opaque planet under the sky. Extend the atmospheric column
// continuously into the lower hemisphere without integrating through solid Earth.
float celestialAtmosphereCosine(float mu) { return clamp(abs(mu), 0.0, 1.0); }
#endif
