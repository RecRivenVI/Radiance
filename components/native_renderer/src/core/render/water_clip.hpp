#pragma once
#include <glm/glm.hpp>
#include <algorithm>
#include <bit>
#include <cmath>
#include <map>
#include <span>
#include <stdexcept>
#include <vector>

namespace mcvr::water {
// The source adapter extends its depth cap to the actual hull floor before
// submitting this finite control volume. It is never a world surface.
inline std::vector<glm::vec4> clipPlanes(std::span<const glm::vec3> positions, std::span<const uint32_t> indices) {
    if (positions.empty() || indices.size() % 3 != 0) throw std::invalid_argument("Invalid water exclusion mesh");
    glm::vec3 center(0);
    for (auto p : positions) {
        if (!std::isfinite(p.x) || !std::isfinite(p.y) || !std::isfinite(p.z))
            throw std::invalid_argument("Non-finite water exclusion vertex");
        center += p / float(positions.size());
    }
    std::vector<glm::vec4> result;
    std::vector<glm::vec3> corners;
    std::vector<uint32_t> welded;
    for (auto p : positions) {
        auto found =
            std::find_if(corners.begin(), corners.end(), [&](auto other) { return glm::length(p - other) < 0.0001f; });
        welded.push_back(static_cast<uint32_t>(found - corners.begin()));
        if (found == corners.end()) corners.push_back(p);
    }
    std::map<std::pair<uint32_t, uint32_t>, unsigned> edges;
    for (size_t i = 0; i < indices.size(); i += 3) {
        for (int j = 0; j < 3; ++j)
            if (indices[i + j] >= positions.size()) throw std::out_of_range("Water exclusion index");
        auto a = positions[indices[i]], b = positions[indices[i + 1]], c = positions[indices[i + 2]];
        auto n = glm::cross(b - a, c - a);
        if (glm::dot(n, n) < 1e-16f) continue;
        for (unsigned edge = 0; edge < 3; ++edge) {
            auto a = welded[indices[i + edge]], b = welded[indices[i + (edge + 1) % 3]];
            if (a == b) throw std::invalid_argument("Degenerate water exclusion edge");
            ++edges[std::minmax(a, b)];
        }
        n = glm::normalize(n);
        if (glm::dot(n, center - a) > 0) n = -n;
        glm::vec4 plane(n, -glm::dot(n, a));
        for (auto p : positions)
            if (glm::dot(plane, glm::vec4(p, 1)) > 0.0001f)
                throw std::invalid_argument("Source water exclusion must be convex");
        if (std::none_of(result.begin(), result.end(), [&](auto old) {
                return glm::length(glm::vec3(old) - n) < 0.0001f && std::abs(old.w - plane.w) < 0.0001f;
            }))
            result.push_back(plane);
    }
    // A plane or open patch cannot define a finite interior. Every plane needs
    // the other vertices strictly behind it; boundary closure is checked below.
    if (result.size() < 4) throw std::invalid_argument("Water exclusion has no finite volume");
    for (auto [edge, count] : edges)
        if (count != 2) throw std::invalid_argument("Water exclusion mesh is not closed");
    for (auto p : positions) {
        unsigned boundaries = 0;
        for (auto plane : result) boundaries += std::abs(glm::dot(plane, glm::vec4(p, 1))) < 0.0002f;
        if (boundaries < 3) throw std::invalid_argument("Water exclusion mesh is not closed");
    }
    return result;
}
inline bool contains(std::span<const glm::vec4> planes, glm::vec3 point) {
    return !planes.empty() && std::all_of(planes.begin(), planes.end(),
                                          [&](auto p) { return glm::dot(p, glm::vec4(point, 1)) <= 0.00002f; });
}
inline void
appendVolume(std::vector<glm::vec4> &data, std::span<const glm::vec4> planes, const glm::mat4 &objectToWorld) {
    if (planes.empty()) return;
    const auto inverse = glm::inverse(objectToWorld);
    for (unsigned c = 0; c < 4; ++c)
        for (unsigned r = 0; r < 4; ++r)
            if (!std::isfinite(inverse[c][r])) throw std::invalid_argument("Singular water exclusion transform");
    if (data.empty()) data.emplace_back(0);
    uint32_t count = std::bit_cast<uint32_t>(data[0].x) + 1;
    data[0].x = std::bit_cast<float>(count);
    data.emplace_back(std::bit_cast<float>(static_cast<uint32_t>(planes.size())), 0, 0, 0);
    for (unsigned c = 0; c < 4; ++c) data.push_back(inverse[c]);
    data.insert(data.end(), planes.begin(), planes.end());
}
} // namespace mcvr::water
