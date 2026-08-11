#pragma once
#include "common/render_abi.hpp"
#include <cstdint>
#include <stdexcept>

namespace mcvr::surface {
inline uint32_t sourceEmission(uint32_t geometry) {
    if ((geometry & 0xF0000000u) != 0) throw std::invalid_argument("Unknown source emission bits");
    return (geometry >> abi::SOURCE_EMISSION_SHIFT) & abi::SOURCE_EMISSION_MASK;
}
inline constexpr uint32_t sourceFaces(uint32_t geometry) {
    return (geometry >> 8u) & 0xFFFFu;
}
inline constexpr bool forceUnitEmission(uint32_t policy) {
    return (policy & abi::SOURCE_EMISSION_FORCE) != 0;
}
inline constexpr bool eyeOverlay(uint32_t policy) {
    return (policy & abi::SOURCE_EMISSION_EYE) != 0;
}
inline constexpr bool eyeBase(uint32_t policy) {
    return (policy & abi::SOURCE_EMISSION_NO_EYE_BASE) == 0;
}
} // namespace mcvr::surface
