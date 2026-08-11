#ifndef ADV_CELESTIAL_GLSL
#define ADV_CELESTIAL_GLSL
#include "util/celestial_math.glsl"
vec3 celestialSunDirection() { return celestialSunFromSource(skyUBO.sunDirection); }
vec3 celestialMoonDirection() { return celestialMoonFromSource(skyUBO.sunDirection); }
#endif
