#pragma once

#include "common/render_abi.hpp"
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <span>

namespace mcvr::lines {
inline constexpr uint32_t contractMagic = abi::LINE_MAGIC;
inline constexpr uint32_t contractVersion = abi::LINE_VERSION;
inline constexpr size_t styleBytes = abi::LINE_STYLE_BYTES;
inline constexpr uint32_t explicitReference = abi::LINE_EXPLICIT_REFERENCE;

struct Style {
    uint32_t source = 0, flags = 0, alphaMode = 0;
    float width = 0, sourceWidth = 0;
    std::array<float, 9> owner{1, 0, 0, 0, 1, 0, 0, 0, 1};
    float emission = 0;
};

// Byte decoding is shared by actual submissions and the JNI contract probe.
inline bool decodeStyle(std::span<const std::byte> input, Style &out) noexcept {
    if (input.size() != styleBytes) return false;
    uint32_t magic, version;
    std::memcpy(&magic, input.data(), 4);
    std::memcpy(&version, input.data() + 4, 4);
    if (magic != contractMagic || version != contractVersion) return false;
    std::memcpy(&out.source, input.data() + 8, 4);
    std::memcpy(&out.flags, input.data() + 12, 4);
    std::memcpy(&out.width, input.data() + 16, 4);
    std::memcpy(&out.sourceWidth, input.data() + 20, 4);
    std::memcpy(out.owner.data(), input.data() + 24, 36);
    std::memcpy(&out.emission, input.data() + 60, 4);
    out.alphaMode = (out.flags >> abi::LINE_ALPHA_SHIFT) & abi::LINE_ALPHA_MASK;
    if (!out.source || (out.flags & ~abi::LINE_ALLOWED_FLAGS) || out.alphaMode > 24 || !(out.width > 0) ||
        !std::isfinite(out.width) || !(out.sourceWidth > 0) || !std::isfinite(out.sourceWidth) ||
        !(out.emission >= 0) || !std::isfinite(out.emission))
        return false;
    for (float value : out.owner)
        if (!std::isfinite(value)) return false;
    const auto &a = out.owner;
    const double determinant = double(a[0]) * (double(a[4]) * a[8] - double(a[5]) * a[7]) -
                               double(a[3]) * (double(a[1]) * a[8] - double(a[2]) * a[7]) +
                               double(a[6]) * (double(a[1]) * a[5] - double(a[2]) * a[4]);
    return std::isfinite(determinant) && std::abs(determinant) > 1e-12 &&
           (determinant < 0) == ((out.flags & abi::LINE_MIRROR_BIT) != 0);
}

inline uint64_t styleFingerprint(std::span<const std::byte> input) noexcept {
    Style style;
    if (!decodeStyle(input, style)) return 0;
    uint64_t hash = 1469598103934665603ull;
    for (auto value : input) {
        hash ^= std::to_integer<uint8_t>(value);
        hash *= 1099511628211ull;
    }
    return hash;
}
} // namespace mcvr::lines
