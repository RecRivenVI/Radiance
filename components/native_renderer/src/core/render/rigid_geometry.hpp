#pragma once

#include <cstddef>
#include <cstdint>
#include <span>
#include <vector>

namespace mcvr {
// Created only on a declared model-version miss, after topology/layer composition.
// Material payload is deliberately absent; traversal needs positions, indices and
// the exact geometry flags/order, while each appearance owns its material buffer.
struct RigidGeometryContent {
    std::vector<uint32_t> layout, indices;
    std::vector<std::byte> positions;

    void append(uint32_t flags, std::span<const std::byte> vertices, std::span<const uint32_t> elements) {
        layout.insert(layout.end(), {flags, uint32_t(vertices.size()), uint32_t(elements.size())});
        positions.insert(positions.end(), vertices.begin(), vertices.end());
        indices.insert(indices.end(), elements.begin(), elements.end());
    }
    bool operator==(const RigidGeometryContent &) const = default;
    uint64_t hash() const {
        uint64_t value = 14695981039346656037ull;
        for (auto bytes : {std::as_bytes(std::span(layout)), std::span<const std::byte>(positions),
                           std::as_bytes(std::span(indices))})
            for (auto byte : bytes) value = (value ^ std::to_integer<uint8_t>(byte)) * 1099511628211ull;
        return value;
    }
    size_t storageBytes() const {
        return positions.size() + (layout.size() + indices.size()) * sizeof(uint32_t);
    }
};
} // namespace mcvr
