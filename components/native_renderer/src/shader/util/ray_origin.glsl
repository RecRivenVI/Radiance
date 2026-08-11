#ifndef RAY_ORIGIN_GLSL
#define RAY_ORIGIN_GLSL
// Move off the departing geometric interface, not through a whole material.
// The error allowance scales with float precision in camera-relative coordinates;
// it does not grant an exemption to another triangle or another water boundary.
vec3 offsetQueryOrigin(vec3 position, vec3 geometricNormal, vec3 direction) {
    vec3 normal = dot(direction, geometricNormal) >= 0.0 ? geometricNormal : -geometricNormal;
    float magnitude = max(abs(position.x), max(abs(position.y), abs(position.z)));
    float error = max(0.0000001, magnitude * 0.00000095367431640625);
    vec3 shifted = position + normal * error;
    for (int axis = 0; axis < 3; ++axis) {
        if (normal[axis] == 0.0) continue;
        if (shifted[axis] == 0.0) {
            shifted[axis] = uintBitsToFloat(normal[axis] > 0.0 ? 1u : 0x80000001u);
            continue;
        }
        uint bits = floatBitsToUint(shifted[axis]);
        bits += (shifted[axis] >= 0.0) == (normal[axis] > 0.0) ? 1u : 0xffffffffu;
        shifted[axis] = uintBitsToFloat(bits);
    }
    return shifted;
}
#endif
