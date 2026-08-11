#ifndef RADIANCE_DISPLAY_COLOUR_GLSL
#define RADIANCE_DISPLAY_COLOUR_GLSL
vec3 displaySrgbDecode(vec3 v) {
    v = max(v, vec3(0.0));
    return mix(pow((v + 0.055) / 1.055, vec3(2.4)), v / 12.92, lessThanEqual(v, vec3(0.04045)));
}
vec3 displaySrgbEncode(vec3 v) {
    v = max(v, vec3(0.0));
    return mix(1.055 * pow(v, vec3(1.0 / 2.4)) - 0.055, 12.92 * v, lessThanEqual(v, vec3(0.0031308)));
}
vec3 displayPqEncode(vec3 nits) {
    const float m1 = 2610.0 / 16384.0, m2 = 2523.0 / 32.0;
    const float c1 = 3424.0 / 4096.0, c2 = 2413.0 / 128.0, c3 = 2392.0 / 128.0;
    vec3 p = pow(clamp(nits / 10000.0, 0.0, 1.0), vec3(m1));
    return pow((c1 + c2 * p) / (1.0 + c3 * p), vec3(m2));
}
vec3 displayRec709To2020(vec3 v) {
    return mat3(0.627404, 0.069097, 0.0163916,
                0.329283, 0.919540, 0.0880132,
                0.0433136, 0.0113612, 0.895595) * v;
}
vec3 displayHdrShoulder(vec3 colour, float peak) {
    colour = max(colour, vec3(0.0));
    float black = min(colour.r, min(colour.g, colour.b));
    colour -= black < 0.08 ? black - 6.25 * black * black : 0.04;
    float value = max(colour.r, max(colour.g, colour.b));
    const float start = 0.76;
    if (value <= start) return colour;
    float range = max(peak - start, 0.01);
    float compressed = start + range * (value - start) / (value - start + range);
    return colour * (compressed / max(value, 1e-6));
}
#endif
