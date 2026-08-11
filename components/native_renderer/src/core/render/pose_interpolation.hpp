#pragma once

#include <glm/glm.hpp>
#include <glm/gtc/matrix_transform.hpp>
#include <glm/gtc/quaternion.hpp>
#include <algorithm>
#include <array>
#include <cmath>
#include <stdexcept>

namespace mcvr::scene {
// Explicit source TRS, not decomposition or interpolation of an arbitrary affine matrix.
struct PoseEndpoint {
    glm::dvec3 position{0};
    glm::dquat rotation{1, 0, 0, 0};
    glm::dvec3 scale{1};

    void validate() const {
        for (int i = 0; i < 3; ++i)
            if (!std::isfinite(position[i]) || !std::isfinite(scale[i]) || scale[i] == 0)
                throw std::invalid_argument("Invalid source pose endpoint");
        for (int i = 0; i < 4; ++i)
            if (!std::isfinite(rotation[i])) throw std::invalid_argument("Invalid source rotation");
        if (!std::isfinite(glm::length(rotation)) || glm::length(rotation) < 1e-12)
            throw std::invalid_argument("Invalid source quaternion magnitude");
    }
    static PoseEndpoint decode(const double *data) {
        PoseEndpoint result{
            {data[0], data[1], data[2]}, {data[6], data[3], data[4], data[5]}, {data[7], data[8], data[9]}};
        result.validate();
        result.rotation = glm::normalize(result.rotation);
        return result;
    }
};

struct PoseMotion {
    PoseEndpoint before, after;
    double start = 0, end = 0;

    void validate() const {
        before.validate();
        after.validate();
        if (!std::isfinite(start) || !std::isfinite(end) || end < start || !std::isfinite(end - start))
            throw std::invalid_argument("Invalid source time interval");
        for (int i = 0; i < 3; ++i)
            if (std::signbit(before.scale[i]) != std::signbit(after.scale[i]))
                throw std::invalid_argument("Source scale interpolation crosses a singular pose");
    }
    glm::dmat4 at(double time) const {
        if (!std::isfinite(time)) throw std::invalid_argument("Invalid source sample time");
        const double alpha = end == start || time >= end ? 1 : time <= start ? 0 : (time - start) / (end - start);
        const auto position = glm::mix(before.position, after.position, alpha);
        const auto rotation =
            glm::normalize(glm::slerp(glm::normalize(before.rotation), glm::normalize(after.rotation), alpha));
        const auto scale = glm::mix(before.scale, after.scale, alpha);
        const auto result =
            glm::translate(glm::dmat4(1), position) * glm::mat4_cast(rotation) * glm::scale(glm::dmat4(1), scale);
        for (int c = 0; c < 4; ++c)
            for (int r = 0; r < 4; ++r)
                if (!std::isfinite(result[c][r])) throw std::invalid_argument("Non-finite sampled source pose");
        return result;
    }
};
} // namespace mcvr::scene
