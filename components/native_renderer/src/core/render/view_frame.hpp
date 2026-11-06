#pragma once
#include "common/shared.hpp"
#include <glm/gtc/matrix_transform.hpp>
#include <cstdint>
#include <stdexcept>

namespace mcvr::view {
inline constexpr double reconstructionOriginRange = 1024.0;

// Both frame matrices must describe the same, stable world origin. Subtract in
// double before conversion so sub-pixel camera motion survives far coordinates.
inline glm::mat4 reconstructionView(const glm::mat4 &relativeView, const glm::dvec3 &camera, const glm::dvec3 &origin) {
    return relativeView * glm::translate(glm::mat4(1), glm::vec3(origin - camera));
}

inline uint32_t viewForSlot(uint32_t slot, uint32_t framesPerView) {
    if (!framesPerView) throw std::invalid_argument("View frame partition is empty");
    return slot / framesPerView;
}

// Recording-local references into the existing UBO owner, never retained by a task.
struct Frame {
    uint32_t slot, view;
    glm::uvec2 inputSize, outputSize;
    const vk::Data::WorldUBO &current;
    const vk::Data::WorldUBO *previous;
    void validate() const {
        if (!inputSize.x || !inputSize.y || !outputSize.x || !outputSize.y)
            throw std::invalid_argument("View physical extent is empty");
    }
};

enum class ResetReason : uint8_t {
    NONE,
    FIRST_FRAME,
    CAMERA_TRANSLATION,
    CAMERA_ROTATION,
    RESOURCE_RELOAD,
    RESIZE,
    WORLD_SWITCH,
    SDK_FAILURE
};
struct CameraHistory {
    bool firstFrame = true;
    glm::vec3 lastPosition{0.0f}, lastDirection{0.0f, 0.0f, -1.0f};
    ResetReason pending = ResetReason::FIRST_FRAME, lastReason = ResetReason::NONE;
    void invalidate(ResetReason reason) {
        firstFrame = true;
        pending = reason;
    }
    ResetReason evaluate(glm::vec3 position, glm::vec3 direction, float translationLimit, float directionLimit) {
        if (firstFrame) {
            firstFrame = false;
            lastReason = pending;
        } else {
            const float distance = glm::length(position - lastPosition);
            const float cosine = glm::dot(glm::normalize(direction), glm::normalize(lastDirection));
            lastReason = distance > translationLimit ?
                             ResetReason::CAMERA_TRANSLATION :
                             (cosine < directionLimit ? ResetReason::CAMERA_ROTATION : ResetReason::NONE);
        }
        lastPosition = position;
        lastDirection = direction;
        return lastReason;
    }
};
} // namespace mcvr::view
