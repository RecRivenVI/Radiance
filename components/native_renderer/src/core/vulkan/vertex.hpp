#pragma once

#include "common/shared.hpp"
#include "common/render_abi.hpp"
#include "core/all_extern.hpp"

#include <vector>
#include <span>

namespace vk {
struct VertexLayoutInfo {
    std::vector<VkVertexInputBindingDescription> bindingDescriptions;
    std::vector<VkVertexInputAttributeDescription> attributeDescriptions;
};

struct VertexAttribute {
    VkFormat format;
    uint32_t offset;
};

struct Vertex {
    static constexpr uint32_t useColorLayerBit = mcvr::abi::PACKED_COLOR_BIT;
    static constexpr uint32_t useTextureBit = mcvr::abi::PACKED_TEXTURE_BIT;
    static constexpr uint32_t useOverlayBit = mcvr::abi::PACKED_OVERLAY_BIT;
    static constexpr uint32_t useGlintBit = mcvr::abi::PACKED_GLINT_BIT;
    static constexpr uint32_t useNormBit = mcvr::abi::PACKED_NORMAL_BIT;
    static constexpr uint32_t useLightBit = mcvr::abi::PACKED_LIGHT_BIT;
    static constexpr uint32_t colorLayerMixBit = mcvr::abi::PACKED_COLOR_MIX_BIT;
    static constexpr uint32_t vertexShadingNormalBit = mcvr::abi::PACKED_VERTEX_NORMAL_BIT;
    // Alpha modes use five bits so the text-mode namespace (12..19) cannot
    // collide with transmission/cutout/coverage modes. Coordinate data starts
    // after the widened field.
    static constexpr uint32_t alphaModeShift = mcvr::abi::PACKED_ALPHA_SHIFT;
    static constexpr uint32_t coordinateShift = mcvr::abi::PACKED_COORDINATE_SHIFT;
    static constexpr uint32_t glintModeShift = mcvr::abi::PACKED_GLINT_SHIFT;

    template <typename T>
    static VertexLayoutInfo initVertexLayout(std::vector<VertexAttribute> &attributes);

    template <typename T>
    static void buildVertexLayoutInfo(VertexLayoutInfo &vertexLayoutInfo, std::vector<VertexAttribute> &attributes);

    template <typename T>
    static VertexLayoutInfo &vertexLayoutInfo();

    static uint32_t packMaterialFlags(const VertexFormat::PBRVertex &vertex);
    static VertexFormat::PositionVertex makePositionVertex(const VertexFormat::PBRVertex &vertex);
    static VertexFormat::MaterialVertex makeMaterialVertex(const VertexFormat::PBRVertex &vertex);
    // Exact-sized, disjoint output spans, including mapped upload storage. No allocation.
    static void writePackedVertices(std::span<const VertexFormat::PBRVertex> vertices,
                                    uint32_t emissiveOverlay,
                                    std::span<VertexFormat::PositionVertex> positions,
                                    std::span<VertexFormat::MaterialVertex> materials);
    static void appendPackedVertices(const std::vector<VertexFormat::PBRVertex> &vertices,
                                     uint32_t emissiveOverlay,
                                     std::vector<VertexFormat::PositionVertex> &positions,
                                     std::vector<VertexFormat::MaterialVertex> &materials);
    static std::vector<VertexFormat::PositionVertex>
    buildPositionVertices(const std::vector<VertexFormat::PBRVertex> &vertices);
    static std::vector<VertexFormat::MaterialVertex>
    buildMaterialVertices(const std::vector<VertexFormat::PBRVertex> &vertices);
};

template <typename T>
vk::VertexLayoutInfo vk::Vertex::initVertexLayout(std::vector<VertexAttribute> &attributes) {
    vk::VertexLayoutInfo vertexLayoutInfo{};
    buildVertexLayoutInfo<T>(vertexLayoutInfo, attributes);
    return vertexLayoutInfo;
}

template <typename T>
void Vertex::buildVertexLayoutInfo(VertexLayoutInfo &vertexLayoutInfo, std::vector<VertexAttribute> &attributes) {
    vertexLayoutInfo.bindingDescriptions.push_back({
        .binding = 0,
        .stride = sizeof(T),
        .inputRate = VK_VERTEX_INPUT_RATE_VERTEX,
    });

    for (uint32_t i = 0; i < attributes.size(); i++) {
        vertexLayoutInfo.attributeDescriptions.push_back({
            .location = i,
            .binding = 0,
            .format = attributes[i].format,
            .offset = attributes[i].offset,
        });
    }
}
}; // namespace vk
