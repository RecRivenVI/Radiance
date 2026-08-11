#pragma once

#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <utility>
#include <vector>
#include <glm/glm.hpp>
#include "core/render/line_contract.hpp"

namespace mcvr::lines {
// Java CapturedLineVertexConsumer: source attributes, followed by pose directions.
struct SourceVertex {
    glm::vec3 position;
    uint32_t color;
    uint32_t normal;
    glm::vec3 frameX, frameY, frameZ;
    glm::vec3 reference;
    uint32_t flags;
};
static_assert(sizeof(SourceVertex) == abi::LINE_VERTEX_BYTES);
static_assert(offsetof(SourceVertex, frameX) == 20);

struct Frame {
    glm::dvec3 x{1, 0, 0}, y{0, 1, 0}, z{0, 0, 1};
};

inline bool validDirection(const glm::dvec3 &v) {
    const double length = glm::length(v);
    return std::isfinite(length) && length > 1e-12;
}

inline Frame capturedFrame(const SourceVertex &vertex, Frame fallback) {
    if (!validDirection(vertex.frameX) || !validDirection(vertex.frameY) || !validDirection(vertex.frameZ))
        return fallback;
    // Keep the Y guide used by extrusion, remove shear from X, and retain mirror parity.
    const auto y = glm::normalize(glm::dvec3(vertex.frameY));
    const auto rawX = glm::dvec3(vertex.frameX);
    auto x = rawX - y * glm::dot(rawX, y);
    if (!validDirection(x)) return fallback;
    x = glm::normalize(x);
    const auto crossZ = glm::cross(x, y);
    return {x, y, glm::dot(crossZ, glm::dvec3(vertex.frameZ)) < 0 ? -crossZ : crossZ};
}

inline Frame drawingFrame(const SourceVertex &vertex, Frame fallback) {
    auto frame = capturedFrame(vertex, fallback);
    if ((vertex.flags & explicitReference) && validDirection(vertex.reference))
        frame.y = glm::normalize(glm::dvec3(vertex.reference));
    return frame;
}

inline std::vector<std::pair<uint32_t, uint32_t>> segments(int mode, uint32_t count) {
    std::vector<std::pair<uint32_t, uint32_t>> result;
    // Minecraft LINES/LINE_STRIP duplicate each logical vertex; DEBUG modes do not.
    if (mode == 0) {
        for (uint32_t i = 0; i + 3 < count; i += 4) result.emplace_back(i, i + 2);
    } else if (mode == 1) {
        for (uint32_t i = 0; i + 3 < count; i += 2) result.emplace_back(i, i + 2);
    } else if (mode == 2) {
        for (uint32_t i = 0; i + 1 < count; i += 2) result.emplace_back(i, i + 1);
    } else if (mode == 3) {
        for (uint32_t i = 0; i + 1 < count; ++i) result.emplace_back(i, i + 1);
    }
    return result;
}

inline std::pair<bool, std::array<glm::dvec3, 8>>
prism(const glm::dvec3 &from, const glm::dvec3 &to, double width, Frame frame) {
    if (!(width > 0.0) || !std::isfinite(width)) return {false, {}};
    const glm::dvec3 axis = to - from;
    if (!validDirection(axis)) return {false, {}};
    if (!validDirection(frame.x) || !validDirection(frame.y) || !validDirection(frame.z)) frame = {};
    const glm::dvec3 a = glm::normalize(axis);
    glm::dvec3 w = std::fabs(glm::dot(a, frame.y)) <= 0.99 ? frame.y : frame.x;
    if (std::fabs(glm::dot(a, w)) > 0.99) w = frame.z;
    glm::dvec3 u = glm::cross(a, w);
    if (!validDirection(u)) u = glm::cross(a, frame.x);
    if (!validDirection(u)) u = glm::cross(a, frame.z);
    if (!validDirection(u)) return {false, {}};
    u = glm::normalize(u);
    const glm::dvec3 v = glm::cross(a, u);
    const double h = width * 0.5;
    return {true,
            {from - a * h - u * h - v * h, from - a * h + u * h - v * h, from - a * h + u * h + v * h,
             from - a * h - u * h + v * h, to + a * h - u * h - v * h, to + a * h + u * h - v * h,
             to + a * h + u * h + v * h, to + a * h - u * h + v * h}};
}

inline constexpr std::array<uint32_t, 36> prismIndices = {0, 3, 2, 0, 2, 1, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4,
                                                          1, 2, 6, 1, 6, 5, 2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7};
} // namespace mcvr::lines
