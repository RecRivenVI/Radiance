#pragma once

#include <algorithm>
#include <cmath>
#include <glm/glm.hpp>
#include <glm/gtc/quaternion.hpp>

namespace mcvr::replay {
struct CameraInput {
    double forward = 0, right = 0, up = 0;
    double lookX = 0, lookY = 0; // Relative mouse pixels, independent of frame time.
    bool fast = false, slow = false, reset = false;
};

// Camera-relative static geometry has to move opposite the camera. Keep the
// captured transforms immutable; derive both current and previous origins from it.
inline glm::mat4 relativeTransform(glm::mat4 captured, glm::dvec3 offset) {
    for (int axis = 0; axis != 3; ++axis)
        captured[3][axis] = static_cast<float>(double(captured[3][axis]) - offset[axis]);
    return captured;
}

class ReplayCamera {
    glm::dvec3 origin_, position_;
    glm::mat4 capturedView_, capturedEffectedView_, effect_;
    glm::dquat initialOrientation_, orientation_;
    bool rotated_ = false;

  public:
    ReplayCamera(glm::dvec3 origin, glm::mat4 view, glm::mat4 effectedView)
        : origin_(origin),
          position_(origin),
          capturedView_(view),
          capturedEffectedView_(effectedView),
          effect_(effectedView * glm::inverse(view)),
          initialOrientation_(glm::normalize(glm::quat_cast(glm::dmat3(glm::inverse(view))))),
          orientation_(initialOrientation_) {}

    void update(const CameraInput &input, double seconds) {
        if (input.reset) {
            position_ = origin_;
            orientation_ = initialOrientation_;
            rotated_ = false;
            return;
        }
        if (input.lookX != 0 || input.lookY != 0) {
            constexpr double radiansPerPixel = 0.0025;
            orientation_ =
                glm::normalize(glm::angleAxis(-input.lookX * radiansPerPixel, glm::dvec3(0, 1, 0)) * orientation_ *
                               glm::angleAxis(-input.lookY * radiansPerPixel, glm::dvec3(1, 0, 0)));
            rotated_ = true;
        }
        auto direction = orientation_ * glm::dvec3(input.right, 0, -input.forward) + glm::dvec3(0, input.up, 0);
        const double length = glm::length(direction);
        if (length > 1) direction /= length;
        // Clamp a stall/focus transition instead of teleporting on the next poll.
        const double dt = std::isfinite(seconds) ? std::clamp(seconds, 0.0, 0.1) : 0.0;
        position_ += direction * dt * (input.fast ? 160.0 : input.slow ? 2.0 : 20.0);
    }
    glm::dvec3 position() const {
        return position_;
    }
    glm::dvec3 offset() const {
        return position_ - origin_;
    }
    glm::mat4 view() const {
        if (!rotated_) return capturedView_;
        auto result = glm::mat4_cast(glm::quat(glm::conjugate(orientation_)));
        result[3] = capturedView_[3];
        return result;
    }
    glm::mat4 effectedView() const {
        return rotated_ ? effect_ * view() : capturedEffectedView_;
    }
};
} // namespace mcvr::replay
