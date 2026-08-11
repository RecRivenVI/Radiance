#ifndef PRIMITIVE_HISTORY_GLSL
#define PRIMITIVE_HISTORY_GLSL
bool samePrimitiveHistory(uvec3 currentIndices, uint currentFlags, uvec3 previousIndices, uint previousFlags) {
    return all(equal(currentIndices, previousIndices)) && ((currentFlags ^ previousFlags) & 2u) == 0u;
}
#endif
