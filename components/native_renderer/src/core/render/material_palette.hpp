#pragma once

#include <array>
#include <bit>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <span>
#include <stdexcept>
#include <type_traits>
#include <vector>

namespace mcvr::materialPalette {
// Material addresses are at least 16-byte aligned. Bit zero selects a dictionary;
// it is stripped before forming a Vulkan buffer reference, never used by the AS.
inline constexpr uint64_t addressTag = 1;
struct Packed {
    std::vector<std::byte> bytes;
    bool dictionary = false;
};

template <class T>
Packed pack(std::span<const T> source) {
    static_assert(std::is_trivially_copyable_v<T> && sizeof(T) % 16 == 0);
    if (source.size() > UINT32_MAX) throw std::overflow_error("Material dictionary vertex count");
    const auto directBytes = source.size_bytes();
    auto direct = [&] {
        Packed result;
        result.bytes.resize(directBytes);
        if (directBytes) std::memcpy(result.bytes.data(), source.data(), directBytes);
        return result;
    };
    if (source.size() < 16) return direct();
    const size_t paletteOffset = (16 + source.size() * 4 + 15) & ~size_t(15);
    // Pay the extra lookup only when storage falls by at least one quarter.
    const size_t limit = directBytes * 3 / 4;
    if (paletteOffset + sizeof(T) > limit) return direct();
    std::vector<T> palette;
    std::vector<uint32_t> ids;
    ids.reserve(source.size());
    std::vector<uint32_t> slots(std::bit_ceil(source.size() * 2), 0);
    for (const T &value : source) {
        const auto words = std::bit_cast<std::array<uint32_t, sizeof(T) / 4>>(value);
        uint64_t hash = 0x9e3779b97f4a7c15ULL;
        for (uint32_t word : words) hash = (hash ^ word) * 0x100000001b3ULL;
        size_t at = (hash ^ (hash >> 32)) & (slots.size() - 1);
        while (slots[at] && std::memcmp(&palette[slots[at] - 1], &value, sizeof(T))) at = (at + 1) & (slots.size() - 1);
        if (!slots[at]) {
            if (paletteOffset + (palette.size() + 1) * sizeof(T) > limit) return direct();
            palette.push_back(value);
            slots[at] = static_cast<uint32_t>(palette.size());
        }
        ids.push_back(slots[at] - 1);
    }
    Packed result;
    result.dictionary = true;
    result.bytes.resize(paletteOffset + palette.size() * sizeof(T));
    const std::array<uint32_t, 4> header{static_cast<uint32_t>(paletteOffset / 4), static_cast<uint32_t>(source.size()),
                                         static_cast<uint32_t>(palette.size()), 0};
    std::memcpy(result.bytes.data(), header.data(), sizeof(header));
    std::memcpy(result.bytes.data() + 16, ids.data(), ids.size() * 4);
    std::memcpy(result.bytes.data() + paletteOffset, palette.data(), palette.size() * sizeof(T));
    return result;
}
} // namespace mcvr::materialPalette
