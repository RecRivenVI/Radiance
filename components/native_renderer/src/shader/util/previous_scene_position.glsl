#ifndef PREVIOUS_SCENE_POSITION_GLSL
#define PREVIOUS_SCENE_POSITION_GLSL
// Requires the current scene vertex/index/history descriptors from vertex.glsl.
bool loadPreviousSceneTriangle(uint geometryBufferIndex, uint primitiveID,
    uvec3 currentIndices, uint currentPolicy, out vec3 p0, out vec3 p1, out vec3 p2, out mat4 transform) {
    uint64_t lastPositionAddr = lastPositionBufferAddrs.addrs[geometryBufferIndex];
    uint64_t lastIndexAddr = lastIndexBufferAddrs.addrs[geometryBufferIndex];
    if (lastPositionAddr == 0 || lastIndexAddr == 0) return false;
    IndexBuffer indices = IndexBuffer(lastIndexAddr);
    uint base = 3u * primitiveID;
    uvec3 previous = uvec3(indices.indices[base], indices.indices[base + 1u], indices.indices[base + 2u]);
    PositionBuffer positions = PositionBuffer(lastPositionAddr);
    if (!samePrimitiveHistory(currentIndices, currentPolicy, previous, positions.vertices[previous.x].pad0)) return false;
    p0 = positions.vertices[previous.x].pos;
    p1 = positions.vertices[previous.y].pos;
    p2 = positions.vertices[previous.z].pos;
    transform = lastObjToWorldMats.mat[gl_InstanceCustomIndexEXT];
    return true;
}
bool loadPreviousScenePos(uint geometryBufferIndex, uint primitiveID, vec3 baryCoords, out vec3 prevScenePos) {
    if (lastPositionBufferAddrs.addrs[geometryBufferIndex] == 0 || lastIndexBufferAddrs.addrs[geometryBufferIndex] == 0) return false;
    uint i0, i1, i2;
    loadTriangleIndices(geometryBufferIndex, primitiveID, i0, i1, i2);
    PositionBuffer positions = PositionBuffer(positionBufferAddrs.addrs[geometryBufferIndex]);
    vec3 a, b, c;
    mat4 transform;
    if (!loadPreviousSceneTriangle(geometryBufferIndex, primitiveID, uvec3(i0, i1, i2),
        positions.vertices[i0].pad0, a, b, c, transform)) return false;
    vec3 local = baryCoords.x * a + baryCoords.y * b + baryCoords.z * c;
    prevScenePos = mat3(transform) * local + transform[3].xyz;
    return true;
}

#endif
