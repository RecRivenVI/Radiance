#pragma once
#include "core/vulkan/all_core_vulkan.hpp"
#include <span>
#include <array>

class Framework;

// Resources are immutable after publication. Recorded work owns its exact descriptor generation.
struct ComputedSurfaceResource {
    uint64_t queuedFrame = 0;
    std::vector<std::byte> auditBytes;
    std::shared_ptr<vk::DeviceLocalBuffer> data;
    std::shared_ptr<vk::DescriptorTable> layout;
    std::shared_ptr<vk::ComputePipeline> pipeline;
};

struct ComputedSurfaceDispatch : public SharedObject<ComputedSurfaceDispatch> {
    std::shared_ptr<ComputedSurfaceResource> program, source, contacts;
    std::shared_ptr<vk::DeviceLocalBuffer> parameters;
    std::shared_ptr<vk::DeviceLocalImage> noise, field;
    std::shared_ptr<vk::Sampler> sampler;
    std::shared_ptr<vk::DescriptorTable> table;
    std::vector<std::byte> auditParameters;
    uint32_t work = 0, vertices = 0, indices = 0;
    bool recorded = false;
    void bind(Framework &f,
              const std::shared_ptr<vk::DeviceLocalBuffer> &positions,
              const std::shared_ptr<vk::DeviceLocalBuffer> &materials,
              const std::shared_ptr<vk::DeviceLocalBuffer> &indexBuffer,
              uint32_t vertexOffset,
              uint32_t indexOffset);
    void record(Framework &f, const std::shared_ptr<vk::CommandBuffer> &commands);
    uint32_t vertexOffset = 0, indexOffset = 0;
    std::array<std::shared_ptr<vk::DeviceLocalBuffer>, 3> outputs;
};

std::shared_ptr<ComputedSurfaceResource> makeSurfaceProgram(Framework &f, std::span<const uint32_t> spirv);
std::shared_ptr<ComputedSurfaceResource> makeSurfaceData(Framework &f, const void *bytes, size_t size);
