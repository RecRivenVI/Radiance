#include "core/render/scene_space.hpp"
#include <glm/gtc/matrix_transform.hpp>
#include <vulkan/vulkan_core.h>
#include <iostream>
#include <stdexcept>

namespace {
void require(bool condition, const char *message) {
    if (!condition) throw std::runtime_error(message);
}

VkTransformMatrixKHR legacy(const glm::mat4 &matrix) {
    return {{{matrix[0][0], matrix[1][0], matrix[2][0], matrix[3][0]},
             {matrix[0][1], matrix[1][1], matrix[2][1], matrix[3][1]},
             {matrix[0][2], matrix[1][2], matrix[2][2], matrix[3][2]}}};
}

void equal(const VkTransformMatrixKHR &a, const VkTransformMatrixKHR &b) {
    for (int row = 0; row < 3; ++row)
        for (int column = 0; column < 4; ++column)
            require(a.matrix[row][column] == b.matrix[row][column], "Placement changed a serialized component");
}
} // namespace

int main() {
    try {
        size_t comparisons = 0;
        for (float sign : {-1.f, 1.f}) {
            for (double base : {0., 29'999'980., -29'999'980.}) {
                for (int step = 0; step < 32; ++step) {
                    const auto pose = glm::translate(glm::mat4(1), glm::vec3(.125f, -.375f, .875f)) *
                                      glm::rotate(glm::mat4(1), step * .13f, glm::normalize(glm::vec3(1, 2, 3))) *
                                      glm::scale(glm::mat4(1), glm::vec3(sign * 2, .5f, 3));
                    const glm::dvec3 origin(base, 64, -base), camera(base + .13125, 63.7, -base - .6125);
                    const auto inverseView = glm::inverse(glm::rotate(glm::mat4(1), step * .07f, glm::vec3(0, 1, 0)) *
                                                          glm::translate(glm::mat4(1), glm::vec3(.2f, .3f, .4f)));
                    auto world = pose;
                    world[3] += glm::vec4(glm::vec3(origin - camera), 0);
                    for (int coordinate : {0, 1, 2}) {
                        const auto actual =
                            mcvr::space::entityTransform(coordinate, pose, origin, camera, &inverseView);
                        const auto expected = coordinate == 0 ? world :
                                              coordinate == 1 ? inverseView :
                                                                glm::translate(glm::mat4(1), glm::vec3(inverseView[3]));
                        equal(mcvr::space::affineRows<VkTransformMatrixKHR>(actual), legacy(expected));
                        equal(mcvr::space::affineRows<VkTransformMatrixKHR>(
                                  mcvr::space::entityTransform(coordinate, pose, origin, camera, nullptr)),
                              legacy(world));
                        require(std::isfinite(glm::determinant(glm::mat3(actual))), "Non-finite affine");
                        ++comparisons;
                    }
                    // Entity and persistent rigid producers must place identical local geometry identically.
                    equal(mcvr::space::affineRows<VkTransformMatrixKHR>(world),
                          mcvr::space::affineRows<VkTransformMatrixKHR>(
                              mcvr::space::worldOriginFloatLocal(pose, origin, camera)));
                    auto stable = pose;
                    stable[3] = glm::vec4(glm::vec3(origin + glm::dvec3(pose[3]) - camera), pose[3].w);
                    equal(mcvr::space::affineRows<VkTransformMatrixKHR>(stable),
                          mcvr::space::affineRows<VkTransformMatrixKHR>(
                              mcvr::space::stableOriginToScene(origin, pose, camera)));
                    glm::dmat4 chunk(pose);
                    chunk[3] += glm::dvec4(origin, 0);
                    auto previousChunk = chunk;
                    previousChunk[3][0] -= camera.x;
                    previousChunk[3][1] -= camera.y;
                    previousChunk[3][2] -= camera.z;
                    equal(mcvr::space::affineRows<VkTransformMatrixKHR>(previousChunk),
                          mcvr::space::affineRows<VkTransformMatrixKHR>(mcvr::space::worldToScene(chunk, camera)));
                    const glm::mat3 explicitNormal = glm::mat3(inverseView) * glm::mat3(1.3f);
                    const auto correction = mcvr::space::explicitNormalCorrection(pose, explicitNormal);
                    const auto transformed = glm::transpose(glm::inverse(glm::mat3(pose))) * correction;
                    for (int c = 0; c < 3; ++c)
                        require(glm::length(transformed[c] - explicitNormal[c]) < 2e-6f,
                                "Explicit normal was transformed twice");
                    require((glm::determinant(glm::mat3(world)) < 0) == (sign < 0), "Mirror parity lost");
                }
            }
        }
        std::cout
            << comparisons
            << " legacy/new coordinate comparisons; rigid/entity, stable-origin, chunk, normal and parity invariants pass\n";
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
