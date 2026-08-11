#pragma once
#include <array>
#include <cmath>
#include <cstddef>
#include <cstring>
#include <span>
#include <stdexcept>

namespace mcvr::surface {
inline void validateDrawColour(const float *colour) {
    for (size_t i = 0; i < 4; ++i)
        if (!std::isfinite(colour[i])) throw std::invalid_argument("Non-finite draw colour");
}
inline std::array<float, 4> decodeDrawColour(std::span<const std::byte> bytes, size_t offset) {
    if (offset > bytes.size() || bytes.size() - offset < 16) throw std::invalid_argument("Draw colour byte range");
    std::array<float, 4> value;
    std::memcpy(value.data(), bytes.data() + offset, 16);
    validateDrawColour(value.data());
    return value;
}
} // namespace mcvr::surface
