#ifndef FINAL_POSITION_HPP
#define FINAL_POSITION_HPP
#ifdef __cplusplus
#    include <glm/glm.hpp>
#    include <cmath>
#    include <stdexcept>
namespace mcvr::geometry {
inline glm::vec3 finalPosition(glm::vec3 position, glm::vec3 normal, bool offset) {
    if (!offset) return position;
    const float length2 = glm::dot(normal, normal);
    if (!(length2 > 0.0f) || !std::isfinite(normal.x) || !std::isfinite(normal.y) || !std::isfinite(normal.z))
        throw std::invalid_argument("Undefined source normal offset");
    return position + 0.00001f * glm::normalize(normal);
}
} // namespace mcvr::geometry
#else
// Deferred admission validates enabled normals before GPU commands are recorded.
vec3 finalPosition(vec3 position, vec3 normal, bool offset) {
    return offset ? position + 0.00001 * normalize(normal) : position;
}
#endif
#endif
