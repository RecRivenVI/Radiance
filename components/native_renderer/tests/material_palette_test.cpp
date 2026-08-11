#include "core/render/material_palette.hpp"
#include <iostream>

using Material = std::array<uint32_t, 20>;
void check(bool value) {
    if (!value) throw std::runtime_error("Lossless material dictionary");
}
void roundtrip(const std::vector<Material> &source, bool encoded) {
    const auto packed = mcvr::materialPalette::pack<Material>(source);
    check(packed.dictionary == encoded);
    for (size_t i = 0; i < source.size(); ++i) {
        size_t offset = i * sizeof(Material);
        if (packed.dictionary) {
            uint32_t paletteOffset, index;
            std::memcpy(&paletteOffset, packed.bytes.data(), 4);
            std::memcpy(&index, packed.bytes.data() + 16 + i * 4, 4);
            offset = paletteOffset * 4 + index * sizeof(Material);
        }
        check(std::memcmp(&source[i], packed.bytes.data() + offset, sizeof(Material)) == 0);
    }
}
int main() {
    roundtrip({}, false);
    roundtrip(std::vector<Material>(4), false);
    std::vector<Material> repeated(1024);
    for (size_t i = 0; i < repeated.size(); ++i) {
        for (size_t j = 0; j < 20; ++j) repeated[i][j] = uint32_t((i % 13) * 97 + j);
        repeated[i][0] = (i % 13) == 0 ? 0x80000000U : (i % 13) == 1 ? 0U : 0x7fc00000U + uint32_t(i % 13);
    }
    roundtrip(repeated, true); // signed zero and distinct NaN payloads are not merged.
    auto changed = repeated;
    changed[500][18] ^= 1;
    roundtrip(changed, true);
    std::vector<Material> unique(1024);
    for (size_t i = 0; i < unique.size(); ++i) unique[i][0] = uint32_t(i);
    roundtrip(unique, false);
    auto old = mcvr::materialPalette::pack<Material>(repeated);
    auto next = mcvr::materialPalette::pack<Material>(changed);
    check(old.bytes != next.bytes); // independent immutable publication payloads.
    std::cout << "Material exact-bit dictionary/direct fallback, replacement and 16-byte layout PASS\n";
}
