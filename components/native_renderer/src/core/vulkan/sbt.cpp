#include "core/logging.hpp"
#include "core/failure_state.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "core/vulkan/sbt.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"

#include "core/vulkan/buffer.hpp"
#include "core/vulkan/command.hpp"
#include "core/vulkan/device.hpp"
#include "core/vulkan/physical_device.hpp"
#include "core/vulkan/pipeline.hpp"
#include "core/vulkan/vma.hpp"

#include <cstring>
#include <vector>

static uint32_t align(uint32_t addr, uint32_t alignment) {
    return (addr + alignment - 1) & ~(alignment - 1);
}

vk::SBT::SBT(std::shared_ptr<PhysicalDevice> physicalDevice,
             std::shared_ptr<Device> device,
             std::shared_ptr<VMA> vma,
             std::shared_ptr<RayTracingPipeline> pipeline,
             uint32_t missCount,
             uint32_t hitCount)
    : vma_(vma), device_(device), missCount_(missCount), hitCount_(hitCount) {
    VkPhysicalDeviceRayTracingPipelinePropertiesKHR rayTracingProperties = physicalDevice->rayTracingProperties();

    handleSize_ = rayTracingProperties.shaderGroupHandleSize;
    uint32_t handleAlignment = rayTracingProperties.shaderGroupHandleAlignment;
    baseAlignment_ = rayTracingProperties.shaderGroupBaseAlignment;
#ifdef DEBUG
    mcvr::log::info("Sbt") << "handleSize: " << handleSize_ << " handleAlignment: " << handleAlignment
                           << " baseAlignment: " << baseAlignment_ << std::endl;
#endif
    uint32_t groupCount = 1 + missCount + hitCount; // RayGen(1) + Miss + HitGroup

    alignedHandleSize_ = handleSize_;
    if (handleAlignment > 0) { alignedHandleSize_ = align(handleSize_, handleAlignment); }

    shaderHandleStorage_.resize(groupCount * handleSize_);
    const auto handleResult =
        vkGetRayTracingShaderGroupHandlesKHR(device->vkDevice(), pipeline->vkPipeline(), 0, groupCount,
                                             shaderHandleStorage_.size(), shaderHandleStorage_.data());
    if (handleResult != VK_SUCCESS) {
        device->recordFailure(handleResult, "vkGetRayTracingShaderGroupHandlesKHR");
        mcvr::failure::raise(mcvr::failure::Kind::runtime, handleResult, "vkGetRayTracingShaderGroupHandlesKHR");
    }

    rgenSBT_ = DeviceLocalBuffer::create(vma, device, false, handleSize_,
                                         VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR |
                                             VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                                         0, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, baseAlignment_);
    rmissSBT_ = DeviceLocalBuffer::create(vma, device, false, alignedHandleSize_ * missCount,
                                          VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR |
                                              VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                                          0, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, baseAlignment_);

    rgenSBT_->uploadToStagingBuffer(&shaderHandleStorage_[0]);
    rmissSBT_->uploadToStagingBuffer(&shaderHandleStorage_[handleSize_]);

    // 设置SBT区域
    raygenRegion_.deviceAddress = rgenSBT_->bufferAddress();
    raygenRegion_.stride = alignedHandleSize_;
    raygenRegion_.size = alignedHandleSize_; // RayGen usually 1 group

    missRegion_.deviceAddress = rmissSBT_->bufferAddress(); // 紧接RayGen后
    missRegion_.stride = alignedHandleSize_;
    missRegion_.size = alignedHandleSize_ * missCount_;
}

vk::SBT::~SBT() {}

void vk::SBT::uploadStaticSBT(std::shared_ptr<CommandBuffer> commandBuffer) {
    auto addBarrier = [commandBuffer](std::shared_ptr<vk::DeviceLocalBuffer> buffer) {
        commandBuffer->barriersBufferImage({vk::CommandBuffer::BufferMemoryBarrier{
                                               .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                               .srcAccessMask = VK_ACCESS_2_MEMORY_WRITE_BIT,
                                               .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                                               .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT,
                                               .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                               .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                               .buffer = buffer,
                                           }},
                                           {});
    };

    rgenSBT_->uploadToBuffer(commandBuffer);
    addBarrier(rgenSBT_);
    rmissSBT_->uploadToBuffer(commandBuffer);
    addBarrier(rmissSBT_);
}

void vk::SBT::setupHitSBT(const std::shared_ptr<const std::vector<uint32_t>> &hitGroupIndices,
                          std::shared_ptr<CommandBuffer> commandBuffer) {
    if (!hitGroupIndices || hitGroupIndices->empty()) {
        mcvr::log::error("Sbt") << "Hit group should contains something!" << std::endl;
        mcvr::failure::invariant("SBT::setupHitSBT", "hit group list is empty");
    }
    const VkDeviceSize rhitSBTSize = mcvr::hitTableBytes(hitGroupIndices->size(), alignedHandleSize_);

    if (mcvr::profile::contentAuditEnabled()) {
        mcvr::profile::Scope observed("pt.sbt.content-check");
        const auto epoch = mcvr::profile::epoch.load();
        const bool cold = observedProfileEpoch_ != epoch || observedHitGroups_.empty();
        const bool equal = !cold && observedHitGroups_ == *hitGroupIndices;
        mcvr::profile::emit(mcvr::profile::frame, 5, "sbt.hit.calls", 1, rhitSBTSize);
        mcvr::profile::emit(mcvr::profile::frame, 5,
                            cold  ? "sbt.hit.cold" :
                            equal ? "sbt.hit.equal" :
                                    "sbt.hit.changed",
                            1, rhitSBTSize);
        observedHitGroups_ = *hitGroupIndices;
        observedProfileEpoch_ = epoch;
    }

    const auto selection = hitTable_.acquire(
        hitGroupIndices,
        [&] {
            auto packed = mcvr::packHitTable(*hitGroupIndices, shaderHandleStorage_, handleSize_, alignedHandleSize_,
                                             size_t{1} + missCount_, hitCount_);
            auto buffer = DeviceLocalBuffer::create(vma_, device_, false, rhitSBTSize,
                                                    VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR |
                                                        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                                                    0, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, baseAlignment_);
            buffer->uploadToStagingBuffer(packed.data());
            return buffer;
        },
        [&](const auto &buffer) {
            buffer->uploadToBuffer(commandBuffer);
            commandBuffer->barriersBufferImage({vk::CommandBuffer::BufferMemoryBarrier{
                                                   .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                                   .srcAccessMask = VK_ACCESS_2_MEMORY_WRITE_BIT,
                                                   .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                                                   .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT,
                                                   .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                                   .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                                   .buffer = buffer,
                                               }},
                                               {});
        },
        [&](const auto &buffer) { Renderer::instance().framework()->frameResourceRetainer().retain(buffer); });
    if (mcvr::profile::enabled())
        mcvr::profile::emit(mcvr::profile::frame, 5, selection.uploaded ? "sbt.hit.upload" : "sbt.hit.reuse", 1,
                            rhitSBTSize);

    hitRegion_.deviceAddress = selection.buffer->bufferAddress();
    hitRegion_.stride = alignedHandleSize_;
    hitRegion_.size = rhitSBTSize;
}

VkStridedDeviceAddressRegionKHR &vk::SBT::raygenRegion() {
    return raygenRegion_;
}

VkStridedDeviceAddressRegionKHR &vk::SBT::missRegion() {
    return missRegion_;
}

VkStridedDeviceAddressRegionKHR &vk::SBT::hitRegion() {
    return hitRegion_;
}

VkStridedDeviceAddressRegionKHR &vk::SBT::callableRegion() {
    return callableRegion_;
}
