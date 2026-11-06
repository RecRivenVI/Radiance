#pragma once

#include <array>
#include <bit>
#include <cstdint>
#include <cstring>
#include <glm/mat4x4.hpp>
#include <glm/vec4.hpp>
#include <span>
#include <vector>

namespace mcvr {

// A lossless transport encoding. The render shaders still read the original mat4 table.
struct SceneHistoryUpload {
    static constexpr uint32_t translationOnly = UINT32_MAX;
    static constexpr uint32_t one = std::bit_cast<uint32_t>(1.0f);
    static_assert(sizeof(glm::mat4) == 64 && sizeof(glm::uvec4) == 16);

    std::vector<glm::uvec4> words;

    void encode(std::span<const glm::mat4> matrices, size_t staticFirst = 0, size_t staticCount = 0) {
        struct Basis {
            const glm::mat4 *matrix = nullptr;
            uint32_t offset = 0;
        };
        std::array<Basis, 4> recent{};
        size_t nextBasis = 0;
        const glm::mat4 identity(1);
        const size_t count = matrices.size() - staticCount;
        words.resize(count);
        for (size_t i = 0; i < count; ++i) {
            const auto &matrix = matrices[i < staticFirst ? i : i + staticCount];
            const auto tail = std::bit_cast<glm::uvec4>(matrix[3]);
            if (tail.w == one && !std::memcmp(&matrix[0], &identity[0], 3 * sizeof(glm::vec4))) {
                words[i] = {tail.x, tail.y, tail.z, translationOnly};
                continue;
            }
            uint32_t offset = translationOnly;
            for (const auto &basis : recent)
                if (basis.matrix && !std::memcmp(&matrix[0], &(*basis.matrix)[0], 3 * sizeof(glm::vec4)) &&
                    tail.w == std::bit_cast<uint32_t>((*basis.matrix)[3].w)) {
                    offset = basis.offset;
                    break;
                }
            if (offset == translationOnly) {
                offset = static_cast<uint32_t>(words.size());
                recent[nextBasis++ % recent.size()] = {&matrix, offset};
                for (int column = 0; column < 3; ++column) words.push_back(std::bit_cast<glm::uvec4>(matrix[column]));
                words.push_back({0, 0, 0, tail.w});
            }
            words[i] = {tail.x, tail.y, tail.z, offset};
        }
    }
};

} // namespace mcvr
