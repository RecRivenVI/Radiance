#pragma once
#include "core/diagnostics/frame_profile.hpp"
#include "core/render/render_framework.hpp"
#include "core/vulkan/buffer.hpp"
#include <array>

namespace mcvr::diagnostics {
// Local, opt-in sampled shader counters. The frame retainer and a GPU event
// separate recorded work from completed observations; no GPU wait is introduced.
struct SharcAudit {
    std::shared_ptr<vk::Device> device;
    std::shared_ptr<vk::HostVisibleBuffer> buffer;
    VkEvent completed = VK_NULL_HANDLE;
    uint64_t frame = 0;
    bool sealed = false;

    static bool enabled() {
        static const bool value = [] {
            const char *v = std::getenv("RADIANCE_SHARC_AUDIT");
            return v && std::string_view(v) == "1";
        }();
        return value;
    }
    static std::shared_ptr<SharcAudit> create(const std::shared_ptr<Framework> &framework) {
        auto value = std::make_shared<SharcAudit>();
        value->device = framework->device();
        value->frame = mcvr::profile::frame;
        value->buffer = vk::HostVisibleBuffer::create(framework->vma(), value->device, sizeof(uint32_t) * 8,
                                                      VK_BUFFER_USAGE_STORAGE_BUFFER_BIT |
                                                          VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
        std::array<uint32_t, 8> zero{};
        value->buffer->uploadToBuffer(zero.data());
        VkEventCreateInfo info{VK_STRUCTURE_TYPE_EVENT_CREATE_INFO};
        if (vkCreateEvent(value->device->vkDevice(), &info, nullptr, &value->completed) != VK_SUCCESS)
            throw std::runtime_error("Cannot create SHARC audit completion event");
        framework->frameResourceRetainer().retain(value);
        return value;
    }
    void seal(const std::shared_ptr<vk::CommandBuffer> &commands) {
        commands->barriersMemory({{.srcStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                                   .srcAccessMask = VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
                                   .dstStageMask = VK_PIPELINE_STAGE_2_HOST_BIT,
                                   .dstAccessMask = VK_ACCESS_2_HOST_READ_BIT}});
        vkCmdSetEvent(commands->vkCommandBuffer(), completed, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        sealed = true;
    }
    ~SharcAudit() noexcept {
        if (sealed && vkGetEventStatus(device->vkDevice(), completed) == VK_EVENT_SET) {
            buffer->downloadFromBuffer();
            const auto *values = static_cast<const uint32_t *>(buffer->mappedPtr());
            const char *names[]{"sharc.sampled.queries",     "sharc.sampled.hits",      "sharc.sampled.segments",
                                "sharc.sampled.pixels",      "sharc.update.first-near", "sharc.update.first-rays",
                                "sharc.update.first-misses", "sharc.update.first-hits"};
            for (size_t i = 0; i < 8; ++i) mcvr::profile::emit(frame, 5, names[i], values[i], 0);
        }
        if (completed) vkDestroyEvent(device->vkDevice(), completed, nullptr);
    }
};
} // namespace mcvr::diagnostics
