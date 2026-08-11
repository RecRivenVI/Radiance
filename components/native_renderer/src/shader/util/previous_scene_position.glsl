#ifndef PREVIOUS_SCENE_POSITION_GLSL
#define PREVIOUS_SCENE_POSITION_GLSL
// Requires the current scene vertex/index/history descriptors from vertex.glsl.
bool loadPreviousScenePos(uint geometryBufferIndex, uint primitiveID, vec3 baryCoords, out vec3 prevScenePos) {
    uint64_t lastPositionAddr = lastPositionBufferAddrs.addrs[geometryBufferIndex];
    uint64_t lastIndexAddr = lastIndexBufferAddrs.addrs[geometryBufferIndex];
    if (lastPositionAddr == 0 || lastIndexAddr == 0) { return false; }

    IndexBuffer lastIndexBuffer = IndexBuffer(lastIndexAddr);
    uint indexBaseID = 3u * primitiveID;
    uint i0 = lastIndexBuffer.indices[indexBaseID];
    uint i1 = lastIndexBuffer.indices[indexBaseID + 1u];
    uint i2 = lastIndexBuffer.indices[indexBaseID + 2u];

    uint current0,current1,current2;
    loadTriangleIndices(geometryBufferIndex,primitiveID,current0,current1,current2);
    PositionBuffer currentPositionBuffer=PositionBuffer(positionBufferAddrs.addrs[geometryBufferIndex]);
    PositionBuffer lastPositionBuffer = PositionBuffer(lastPositionAddr);
    if (!samePrimitiveHistory(uvec3(current0,current1,current2),currentPositionBuffer.vertices[current0].pad0,
        uvec3(i0,i1,i2),lastPositionBuffer.vertices[i0].pad0)) return false;
    vec3 p0 = lastPositionBuffer.vertices[i0].pos;
    vec3 p1 = lastPositionBuffer.vertices[i1].pos;
    vec3 p2 = lastPositionBuffer.vertices[i2].pos;
    vec3 prevLocalPos = baryCoords.x * p0 + baryCoords.y * p1 + baryCoords.z * p2;
    mat4 lastModelMat = lastObjToWorldMats.mat[gl_InstanceCustomIndexEXT];
    prevScenePos = mat3(lastModelMat) * prevLocalPos + lastModelMat[3].xyz;
    return true;
}
#endif
