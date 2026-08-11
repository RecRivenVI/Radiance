#pragma once

#include <glm/glm.hpp>
#include "common/render_abi.hpp"
#include "core/render/world_mesh_contract.hpp"
#include <stdexcept>

namespace mcvr::space {
enum Coordinates : int {
    WORLD = mcvr::abi::COORDINATE_WORLD,
    CAMERA = mcvr::abi::COORDINATE_CAMERA,
    CAMERA_SHIFT = mcvr::abi::COORDINATE_CAMERA_SHIFT
};

// These operations describe already captured positions. They never reapply the
// producer's PoseStack, normalize a geometric basis, or compensate mirror parity.
inline glm::mat4 worldOriginFloatLocal(glm::mat4 local, glm::dvec3 origin, glm::dvec3 camera) {
    const auto delta = WorldMeshContract::worldTranslation(origin.x, origin.y, origin.z, camera.x, camera.y, camera.z);
    local[3] += glm::vec4(delta[0], delta[1], delta[2], 0);
    return local;
}

inline glm::mat4 entityTransform(
    int coordinate, const glm::mat4 &local, glm::dvec3 origin, glm::dvec3 camera, const glm::mat4 *inverseView) {
    if (coordinate == WORLD || !inverseView) return worldOriginFloatLocal(local, origin, camera);
    if (coordinate == CAMERA) return *inverseView;
    if (coordinate == CAMERA_SHIFT) {
        glm::mat4 result(1);
        result[3] = glm::vec4(glm::vec3((*inverseView)[3]), 1);
        return result;
    }
    throw std::invalid_argument("Unknown scene coordinate space");
}

// Stable origins deliberately combine translation in double before converting.
inline glm::mat4 stableOriginToScene(glm::dvec3 origin, glm::mat4 local, glm::dvec3 camera) {
    local[3] = glm::vec4(glm::vec3(origin + glm::dvec3(local[3]) - camera), local[3].w);
    return local;
}

inline glm::dmat4 worldToScene(glm::dmat4 world, glm::dvec3 camera) {
    world[3][0] -= camera.x;
    world[3][1] -= camera.y;
    world[3][2] -= camera.z;
    return world;
}

template <class Output, class T>
inline Output affineRows(const glm::mat<4, 4, T> &matrix) {
    return {{{static_cast<float>(matrix[0][0]), static_cast<float>(matrix[1][0]), static_cast<float>(matrix[2][0]),
              static_cast<float>(matrix[3][0])},
             {static_cast<float>(matrix[0][1]), static_cast<float>(matrix[1][1]), static_cast<float>(matrix[2][1]),
              static_cast<float>(matrix[3][1])},
             {static_cast<float>(matrix[0][2]), static_cast<float>(matrix[1][2]), static_cast<float>(matrix[2][2]),
              static_cast<float>(matrix[3][2])}}};
}

inline glm::mat3 explicitNormalCorrection(const glm::mat4 &objectPose, const glm::mat3 &normalPose) {
    return glm::transpose(glm::mat3(objectPose)) * normalPose;
}
} // namespace mcvr::space
