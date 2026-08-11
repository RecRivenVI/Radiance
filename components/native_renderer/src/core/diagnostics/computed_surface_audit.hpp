#pragma once
#include "core/render/computed_surface.hpp"
#include "core/render/render_framework.hpp"
#include "common/shared.hpp"
#include "core/failure_state.hpp"
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>

namespace mcvr::audit {
inline bool computedSurfacesEnabled() {
    static const bool enabled = [] {
        auto v = std::getenv("RADIANCE_AUDIT_COMPUTED_SURFACES");
        return v && std::strcmp(v, "1") == 0;
    }();
    return enabled;
}

struct SurfaceReadback {
    std::shared_ptr<vk::HostVisibleBuffer> buffer;
    std::shared_ptr<ComputedSurfaceResource> source, contacts;
    std::vector<std::byte> parameters;
    uint32_t ordinal, vertices, indices;
    uint64_t frame;
    ~SurfaceReadback() noexcept {
        // The frame retainer destroys this object only after the submitting fence retires.
        try {
            mcvr::failure::throwIfFatal();
            buffer->downloadFromBuffer();
            auto directory = std::filesystem::current_path() / "computed-surface-audit";
            std::filesystem::create_directories(directory);
            std::ofstream file(directory / (std::to_string(ordinal) + ".bin"), std::ios::binary);
            uint32_t header[]{0x31534643,
                              vertices,
                              indices,
                              uint32_t(parameters.size()),
                              uint32_t(source->auditBytes.size()),
                              uint32_t(contacts->auditBytes.size()),
                              uint32_t(frame),
                              uint32_t(frame >> 32)};
            file.write(reinterpret_cast<const char *>(header), sizeof(header));
            for (const auto *bytes : {&parameters, &source->auditBytes, &contacts->auditBytes})
                file.write(reinterpret_cast<const char *>(bytes->data()), bytes->size());
            file.write(static_cast<const char *>(buffer->mappedPtr()), buffer->size());
        } catch (...) { /* Diagnostic output cannot escape a resource-retirement destructor. */
        }
    }
};

inline void computedSurface(Framework &f,
                            const std::shared_ptr<vk::CommandBuffer> &commands,
                            const ComputedSurfaceDispatch &surface) {
    if (!computedSurfacesEnabled() || !surface.vertices || surface.vertices > 4096) return;
    static uint32_t count = 0;
    auto frame = f.safeAcquireCurrentContext()->frameSerial;
    if (frame % 32 || count >= 64) return;
    auto readback = std::make_shared<SurfaceReadback>();
    readback->ordinal = count++;
    readback->vertices = surface.vertices;
    readback->indices = surface.indices;
    readback->frame = frame;
    // Parameters are immutable per dispatch; the shader's input upload has already completed.
    // They are captured separately when the dispatch is constructed, never mapped while in flight.
    readback->parameters = surface.auditParameters;
    readback->source = surface.source;
    readback->contacts = surface.contacts;
    size_t sizes[]{size_t(surface.vertices) * sizeof(vk::VertexFormat::PositionVertex),
                   size_t(surface.vertices) * sizeof(vk::VertexFormat::MaterialVertex), size_t(surface.indices) * 4};
    size_t offsets[]{size_t(surface.vertexOffset) * sizeof(vk::VertexFormat::PositionVertex),
                     size_t(surface.vertexOffset) * sizeof(vk::VertexFormat::MaterialVertex),
                     size_t(surface.indexOffset) * 4};
    readback->buffer = vk::HostVisibleBuffer::create(f.vma(), f.device(), sizes[0] + sizes[1] + sizes[2],
                                                     VK_BUFFER_USAGE_TRANSFER_DST_BIT);
    VkMemoryBarrier before{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
    before.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    before.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    auto cmd = commands->vkCommandBuffer();
    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 1, &before, 0,
                         nullptr, 0, nullptr);
    size_t target = 0;
    for (int i = 0; i < 3; i++) {
        VkBufferCopy copy{offsets[i], target, sizes[i]};
        vkCmdCopyBuffer(cmd, surface.outputs[i]->vkBuffer(), readback->buffer->vkBuffer(), 1, &copy);
        target += sizes[i];
    }
    VkMemoryBarrier after{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
    after.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    after.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &after, 0, nullptr, 0,
                         nullptr);
    f.frameResourceRetainer().retain(readback);
}
} // namespace mcvr::audit
