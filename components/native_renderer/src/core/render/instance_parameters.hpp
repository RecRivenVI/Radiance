#pragma once

#include "common/render_abi.hpp"
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <stdexcept>

namespace mcvr::scene {
// Canonical source-independent instance input. The producer resolves its own byte layout;
// Native owns a copy and evaluates only declared rigid motion/UV phase at the view time.
struct InstanceParameters {
    float pose[16];
    float normal[9];
    float colorMultiply[4];
    float colorReplace[4];
    float uv[4];
    float shadow[4];
    int32_t overlay[2];
    int32_t light[2];
    uint32_t flags;
    int32_t texture;
    float fluidProgress;
    uint32_t motion;
    float axis[3];
    float degreesPerSecond;
    float initialDegrees;
    float pivot[3];
    float uvSpeed[2];
    float uvScale[2];
    float uvPhase[2];
    float uvBase[2];
    uint32_t reserved[3];
};
static_assert(sizeof(InstanceParameters) == abi::CANONICAL_INSTANCE_BYTES);
static_assert(offsetof(InstanceParameters, motion) == 192);
static_assert(offsetof(InstanceParameters, uvBase) == 252);
inline void validateInstanceParameters(const InstanceParameters &p) {
    auto finite = [](const auto &values) {
        for (float value : values)
            if (!std::isfinite(value)) throw std::invalid_argument("Non-finite instance parameter");
    };
    finite(p.pose);
    finite(p.normal);
    finite(p.colorMultiply);
    finite(p.colorReplace);
    finite(p.uv);
    finite(p.shadow);
    finite(p.axis);
    finite(p.pivot);
    finite(p.uvSpeed);
    finite(p.uvScale);
    finite(p.uvPhase);
    finite(p.uvBase);
    if ((p.motion & ~3u) || p.reserved[0] || p.reserved[1] || p.reserved[2] || !std::isfinite(p.fluidProgress) ||
        !std::isfinite(p.degreesPerSecond) || !std::isfinite(p.initialDegrees))
        throw std::invalid_argument("Invalid instance parameter packet");
    if ((p.motion & 1u) && !(p.axis[0] * p.axis[0] + p.axis[1] * p.axis[1] + p.axis[2] * p.axis[2] > 0))
        throw std::invalid_argument("Animated rotation has no axis");
}
} // namespace mcvr::scene
