#include <glm/gtc/packing.hpp>
#include "core/render/display_output.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "core/render/chunk_trace.hpp"
#include "core/render/streamline_runtime.hpp"
#include "core/render/frame_acquire_policy.hpp"
#include "core/render/frame_execution.hpp"

#include "core/logging.hpp"
#include "core/render/modules/world/dlss/dlss_frame_generation.hpp"
#include "core/render/render_framework.hpp"

#include "common/shared.hpp"
#include "core/diagnostics/alloc_trace.hpp"
#include "core/diagnostics/fg_timing.hpp"
#include "core/diagnostics/draw_state_trace.hpp"
#include "core/diagnostics/device_loss_trace.hpp"
#include "core/diagnostics/lifecycle_acceptance.hpp"
#include "core/render/buffers.hpp"
#include "core/render/chunks.hpp"
#include "core/render/entities.hpp"
#include "core/render/modules/ui_module.hpp"
#include "core/render/modules/world/world_module.hpp"
#include "core/render/pipeline.hpp"
#include "core/render/renderer.hpp"
#include "core/render/presentation_rates.hpp"
#include "core/render/textures.hpp"
#include "core/render/world.hpp"
#include "core/render/scene_scope.hpp"
#include "core/util/parallel.hpp"
#include "core/failure_state.hpp"

#include <array>
#include <cstdlib>
#include <ctime>
#include <fstream>
#include <iostream>
#include <limits>
#include <random>
#include <sstream>
#include <stdexcept>
#include <string_view>
#include <thread>

namespace {
// The producer owns deferred acquisition. The worker still needs the submitted
// frame's context until presentation finishes.
thread_local Framework *deferredInputAcquire = nullptr;

uint32_t configuredRecordingSlots() {
    if (const auto *value = std::getenv("RADIANCE_FRAME_RECORDING_SLOTS")) {
        const std::string_view slots(value);
        if (slots == "2") return 2;
        if (slots == "3") return 3;
    }
    return mcvr::FrameExecution::enabled() ? 3u : 2u;
}

bool frameSlotDiagnostics() {
    static const auto enabled = [] {
        const auto *flag = std::getenv("RADIANCE_FRAME_SLOT_DIAGNOSTICS");
        return flag && std::string_view(flag) == "1";
    }();
    return enabled;
}
} // namespace

auto renderFrameworkCout() {
    return mcvr::log::info("Render Framework");
}

auto renderFrameworkCerr() {
    return mcvr::log::error("Render Framework");
}

FrameworkContext::FrameworkContext(std::shared_ptr<Framework> framework, uint32_t frameIndex)
    : framework(framework),
      frameIndex(frameIndex),
      instance(framework->instance_),
      window(framework->window_),
      physicalDevice(framework->physicalDevice_),
      device(framework->device_),
      vma(framework->vma_),
      swapchain(framework->swapchain_),
      commandPool(framework->slotCommandPools_[frameIndex]),
      commandFinishedFence(framework->commandFinishedFences_[frameIndex]),
      uploadCommandBuffer(framework->uploadCommandBuffers_[frameIndex]),
      overlayCommandBuffer(framework->overlayCommandBuffers_[frameIndex]),
      worldCommandBuffer(framework->worldCommandBuffers_[frameIndex]),
      fuseCommandBuffer(framework->fuseCommandBuffers_[frameIndex]),
      presentCommandBuffer(vk::CommandBuffer::create(device, commandPool)) {
    if (framework->timestampValidBits_ != 0) {
        VkQueryPoolCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
        createInfo.queryType = VK_QUERY_TYPE_TIMESTAMP;
        createInfo.queryCount = 2;
        const VkResult result = vkCreateQueryPool(device->vkDevice(), &createInfo, nullptr, &frameTimestampQueryPool);
        if (result != VK_SUCCESS) {
            throw std::runtime_error("vkCreateQueryPool(frame timestamps) failed with VkResult=" +
                                     std::to_string(result));
        }
    }
}

const std::shared_ptr<vk::CommandBuffer> &FrameworkContext::recordingOverlayCommands() const {
    mcvr::FrameExecution::checkAccess("overlay commands", this);
    if (!overlayCommandBuffer || !overlayCommandBuffer->isRecording())
        mcvr::failure::invariant("Record overlay commands", "Overlay command buffer is not recording");
    return overlayCommandBuffer;
}

std::shared_ptr<vk::CommandBuffer> FrameworkContext::uiPtCommandStorage() {
    mcvr::FrameExecution::checkAccess("auxiliary view commands", this);
    return uiPtCommands.storage([&] { return vk::CommandBuffer::create(device, commandPool); });
}

std::shared_ptr<vk::CommandBuffer> FrameworkContext::beginUiPtCommands() {
    mcvr::FrameExecution::checkAccess("auxiliary view commands", this);
    return uiPtCommands.begin([&] { return vk::CommandBuffer::create(device, commandPool); });
}

void FrameworkContext::finishWorldInputs() {
    mcvr::FrameExecution::checkAccess("offscreen world inputs", this);
    if (worldInputsFinished || worldRendered) return;
    // Offscreen source passes must execute before the PT reads their attachments.
    // Keep the prefix alive until this context's fence retires; no CPU/GPU wait is needed.
    auditGpu.end(overlayCommandBuffer->vkCommandBuffer(), auditOverlay);
    overlayCommandBuffer->end();
    preWorldCommandBuffer = overlayCommandBuffer;
    if (!overlayContinuationBuffer) overlayContinuationBuffer = vk::CommandBuffer::create(device, commandPool);
    overlayCommandBuffer = overlayContinuationBuffer;
    overlayCommandBuffer->begin();
    auditOverlay = auditGpu.begin(overlayCommandBuffer->vkCommandBuffer(), "ui-buffer");
    worldInputsFinished = true;
}

void FrameworkContext::noteTimeline(Timeline::Event event) {
    if (timeline.owner) timeline.times[event] = mcvr::profile::now();
}

FrameworkContext::~FrameworkContext() {
    if (device) auditGpu.close(device->vkDevice());
    if (frameTimestampQueryPool != VK_NULL_HANDLE && device != nullptr) {
        vkDestroyQueryPool(device->vkDevice(), frameTimestampQueryPool, nullptr);
        frameTimestampQueryPool = VK_NULL_HANDLE;
    }
#ifdef DEBUG
    mcvr::log::info("RenderFramework") << "[Context] context deconstructed" << std::endl;
#endif
}

std::shared_ptr<vk::DeviceLocalImage> FrameworkContext::composeFinal() {
    auto f = framework.lock();
    auto pipelineContext = f->pipeline_->acquirePipelineContext(shared_from_this());
    if (!f->frameGeneration_) f->frameGeneration_ = std::make_unique<DlssFrameGeneration>();
    auto color = f->frameGeneration_->record(*f, *this, pipelineContext);
    mcvr::failure::throwIfFatal();
    return color;
}

void FrameworkContext::recordPresentCopy(const std::shared_ptr<vk::DeviceLocalImage> &finalColor) {
    auto f = framework.lock();
    auto mainQueueIndex = physicalDevice->mainQueueIndex();
    auto &present = presentCommandBuffer;
    const auto &swapchainImage = f->swapchain_->swapchainImages()[presentImageIndex];
    const auto copyTiming = auditGpu.begin(present->vkCommandBuffer(), "swapchain-copy");
    present->barriersBufferImage({},
                                 {
                                     {
                                         .srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                         .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                         .dstStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                         .dstAccessMask = VK_ACCESS_2_TRANSFER_READ_BIT,
                                         .oldLayout = finalColor->imageLayout(),
                                         .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                         .srcQueueFamilyIndex = mainQueueIndex,
                                         .dstQueueFamilyIndex = mainQueueIndex,
                                         .image = finalColor,
                                         .subresourceRange = vk::wholeColorSubresourceRange,
                                     },
                                     {
                                         .srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                         .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                         .dstStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                         .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                         .oldLayout = swapchainImage->imageLayout(),
                                         .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                         .srcQueueFamilyIndex = mainQueueIndex,
                                         .dstQueueFamilyIndex = mainQueueIndex,
                                         .image = swapchainImage,
                                         .subresourceRange = vk::wholeColorSubresourceRange,
                                     },
                                 });

    finalColor->imageLayout() = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    swapchainImage->imageLayout() = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;

    VkImageBlit imageBlit{};
    imageBlit.srcSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    imageBlit.srcSubresource.mipLevel = 0;
    imageBlit.srcSubresource.baseArrayLayer = 0;
    imageBlit.srcSubresource.layerCount = 1;
    imageBlit.srcOffsets[0] = {0, 0, 0};
    imageBlit.srcOffsets[1] = {static_cast<int>(finalColor->width()), static_cast<int>(finalColor->height()), 1};
    imageBlit.dstSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    imageBlit.dstSubresource.mipLevel = 0;
    imageBlit.dstSubresource.baseArrayLayer = 0;
    imageBlit.dstSubresource.layerCount = 1;
    imageBlit.dstOffsets[0] = {0, 0, 0};
    imageBlit.dstOffsets[1] = {static_cast<int>(swapchainImage->width()), static_cast<int>(swapchainImage->height()),
                               1};

    vkCmdBlitImage(present->vkCommandBuffer(), finalColor->vkImage(), finalColor->imageLayout(),
                   swapchainImage->vkImage(), swapchainImage->imageLayout(), 1, &imageBlit, VK_FILTER_LINEAR);

    present->barriersBufferImage({}, {{
                                          .srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                          .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                          .dstStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                          .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                          .oldLayout = finalColor->imageLayout(),
                                          .newLayout = VK_IMAGE_LAYOUT_GENERAL,
                                          .srcQueueFamilyIndex = mainQueueIndex,
                                          .dstQueueFamilyIndex = mainQueueIndex,
                                          .image = finalColor,
                                          .subresourceRange = vk::wholeColorSubresourceRange,
                                      },
                                      {
                                          .srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                          .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                          .dstStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                          .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                          .oldLayout = swapchainImage->imageLayout(),
                                          .newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                                          .srcQueueFamilyIndex = mainQueueIndex,
                                          .dstQueueFamilyIndex = mainQueueIndex,
                                          .image = swapchainImage,
                                          .subresourceRange = vk::wholeColorSubresourceRange,
                                      }});

    finalColor->imageLayout() = VK_IMAGE_LAYOUT_GENERAL;
    swapchainImage->imageLayout() = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    auditGpu.end(present->vkCommandBuffer(), copyTiming);
}

Framework::Framework() : recordingSlotCount(configuredRecordingSlots()) {}

void Framework::init(GLFWwindow *window) {
    instance_ = vk::Instance::create();
    window_ = vk::Window::create(instance_, window);
    physicalDevice_ = vk::PhysicalDevice::create(instance_, window_);
    device_ = vk::Device::create(instance_, window_, physicalDevice_);
    vma_ = vk::VMA::create(instance_, physicalDevice_, device_);
    swapchain_ = vk::Swapchain::create(physicalDevice_, device_, window_);
    mainCommandPool_ = vk::CommandPool::create(physicalDevice_, device_);
    asyncCommandPool_ = vk::CommandPool::create(physicalDevice_, device_, physicalDevice_->secondaryQueueIndex());
    frameResourceRetainer_ = FrameResourceRetainer::create(shared_from_this());

    uint32_t queueFamilyCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice_->vkPhysicalDevice(), &queueFamilyCount, nullptr);
    std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
    vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice_->vkPhysicalDevice(), &queueFamilyCount,
                                             queueFamilies.data());
    const uint32_t mainQueueIndex = physicalDevice_->mainQueueIndex();
    if (mainQueueIndex < queueFamilies.size()) {
        timestampValidBits_ = queueFamilies[mainQueueIndex].timestampValidBits;
        timestampPeriodNs_ = physicalDevice_->properties().limits.timestampPeriod;
    }

#ifdef _WIN32
    const auto *timeline = std::getenv("RADIANCE_A_GPU_TIMELINE");
    if (timeline && std::string_view(timeline) == "1" && vkGetCalibratedTimestampsEXT &&
        vkGetPhysicalDeviceCalibrateableTimeDomainsEXT) {
        uint32_t count = 0;
        auto physical = physicalDevice_->vkPhysicalDevice();
        if (vkGetPhysicalDeviceCalibrateableTimeDomainsEXT(physical, &count, nullptr) == VK_SUCCESS) {
            std::vector<VkTimeDomainEXT> domains(count);
            if (vkGetPhysicalDeviceCalibrateableTimeDomainsEXT(physical, &count, domains.data()) == VK_SUCCESS)
                timelineClockAvailable_ = std::find(domains.begin(), domains.end(),
                                                    VK_TIME_DOMAIN_QUERY_PERFORMANCE_COUNTER_EXT) != domains.end();
        }
        mcvr::log::info("RenderFramework") << "Frame timeline calibrated=" << timelineClockAvailable_ << std::endl;
    }
#endif
    mcvr::log::info("RenderFramework") << "Recording slots=" << recordingSlotCount
                                       << " worker=" << mcvr::FrameExecution::enabled() << std::endl;
    // Each recording slot owns its command buffers independently of swapchain image count.
    for (uint32_t i = 0; i < recordingSlotCount; ++i) {
        const auto pool =
            mcvr::FrameExecution::enabled() ? vk::CommandPool::create(physicalDevice_, device_) : mainCommandPool_;
        slotCommandPools_.push_back(pool);
        uploadCommandBuffers_.emplace_back(vk::CommandBuffer::create(device_, pool));
        overlayCommandBuffers_.emplace_back(vk::CommandBuffer::create(device_, pool));
        worldCommandBuffers_.emplace_back(vk::CommandBuffer::create(device_, pool));
        fuseCommandBuffers_.emplace_back(vk::CommandBuffer::create(device_, pool));
        commandFinishedFences_.push_back(vk::Fence::create(device_, true));
    }
    worldAsyncCommandBuffer_ = vk::CommandBuffer::create(device_, asyncCommandPool_);

    rebuildPresentationSemaphores();
    for (uint32_t i = 0; i < recordingSlotCount; ++i)
        contexts_.push_back(FrameworkContext::create(shared_from_this(), i));

    pipeline_ = Pipeline::create(shared_from_this());
}

void Framework::rebuildPresentationSemaphores() {
    // A slot fence only proves command completion. Presentation releases a
    // semaphore when the same image is acquired and its acquire wait completes.
    presentReadySemaphores_.clear();
    for (uint32_t i = 0; i < swapchain_->imageCount(); ++i)
        presentReadySemaphores_.push_back(vk::Semaphore::create(device_));
}

Framework::~Framework() {
#ifdef DEBUG
    mcvr::log::info("RenderFramework") << "[Framework] framework deconstructed" << std::endl;
#endif
}

void Framework::deferInputAcquire() {
    deferredInputAcquire = this;
    mcvr::FrameExecution::setDeferredProducerPreparation(+[] {
        auto *framework = deferredInputAcquire;
        if (!framework || !framework->producerContext_ ||
            (!framework->producerInputsPending_ && framework->producerContext_->pendingWorldUpdates.empty()))
            return;
        const auto result = framework->acquireInputContext();
        if (result != VK_SUCCESS)
            mcvr::failure::raise(mcvr::failure::Kind::runtime, result, "Prepare frame inputs after overlay");
        framework->deferInputAcquire();
    });
}

VkResult Framework::waitForInputLatencyBudget() {
    static const bool enabled = [] {
        const auto *value = std::getenv("RADIANCE_FRAME_INPUT_THROTTLE");
        return value && std::string_view(value) == "1";
    }();
    if (!enabled || recordingSlotCount != 3 || !mcvr::FrameExecution::enabled()) return VK_SUCCESS;
    if (!isRunning()) return inactiveResult();

    // Keep the recording slot available, but let the second-oldest frame finish
    // before polling input for the next simulation. The worker can still be
    // recording or submitting the latest frame in its own slot.
    const auto olderSlot = (nextContextIndex_ + 1) % recordingSlotCount;
    if (contexts_[olderSlot] == submittedContext_) return VK_SUCCESS;
    const auto &fence = commandFinishedFences_[olderSlot]->vkFence();
    mcvr::profile::Scope timing("frame-worker.input-gpu-budget");
    const auto result = vkWaitForFences(device_->vkDevice(), 1, &fence, VK_TRUE, UINT64_MAX);
    return result == VK_SUCCESS ? result : recordFailure(result, "vkWaitForFences(input GPU budget)");
}

VkResult Framework::dispatchFrame(bool menuFrame) {
    auto context = safeAcquireCurrentContext();
    if (!context) return VK_NOT_READY;
    if (const auto previous = finishExecutedFrame(); previous != VK_SUCCESS) return previous;
    if (deferredInputAcquire == this) {
        deferredInputAcquire = nullptr;
        mcvr::FrameExecution::setDeferredProducerPreparation(nullptr);
    }
    if (context->splitWorldInputs) prepareSharedProducerInputs();
    auto world = Renderer::instance().world(context.get());
    context->sceneSpaces = std::make_unique<mcvr::scene::Spaces>(world->spaces());
    context->cameraPosition = world->getCameraPos();
    context->renderWorld = world->shouldRender();
    world->entities()->sealFrameInputs();
    mcvr::FrameExecution::setProducerWorldActive(false);
    auto &streamline = mcvr::StreamlineRuntime::get();
    streamline.beginRenderSubmission();
    context->streamlineToken = streamline.frame();
    context->streamlinePacing = streamline.pacesFrames();
    const auto frame = mcvr::profile::frame;
    if (timelineClockAvailable_ && mcvr::profile::enabled()) context->timeline.owner = frame;
    context->noteTimeline(FrameworkContext::Timeline::Handoff);
    mcvr::FrameExecution::instance().dispatch(context.get(), [framework = shared_from_this(), menuFrame, frame] {
        mcvr::profile::frame = frame;
        framework->recordingContext()->noteTimeline(FrameworkContext::Timeline::Worker);
        if (mcvr::audit::enabled(MCVR_AUDIT_LIFECYCLE)) mcvr::diagnostics::lifecycle::noteSubmitAttempt();
        const auto result = framework->submitCommand();
        if (result == VK_SUCCESS && mcvr::audit::enabled(MCVR_AUDIT_LIFECYCLE))
            mcvr::diagnostics::lifecycle::noteSuccessfulSubmit();
        mcvr::StreamlineRuntime::get().marker(sl::PCLMarker::eRenderSubmitEnd);
        if (result != VK_SUCCESS) return int(result);
        framework->setMenuFrame(menuFrame);
        return int(framework->presentToQueue());
    });
    submittedContext_ = std::move(context);
    return VK_SUCCESS;
}

VkResult Framework::finishExecutedFrame() {
    if (mcvr::FrameExecution::enabled()) {
        auto context = std::exchange(submittedContext_, nullptr);
        if (auto result = mcvr::FrameExecution::instance().consume(); result && *result != VK_NOT_READY) {
            if (!context) throw std::logic_error("Completed native frame has no submitted context");
            return finishPresentation(static_cast<VkResult>(*result), *context);
        }
    }
    return VK_SUCCESS;
}

VkResult Framework::acquireInputContext() {
    const bool deferred = deferredInputAcquire == this;
    if (deferred) {
        deferredInputAcquire = nullptr;
        mcvr::FrameExecution::setDeferredProducerPreparation(nullptr);
    }
    if (const auto previous = finishExecutedFrame(); previous != VK_SUCCESS) return previous;
    // Overlay recording can acquire the frame before Java reaches GameRenderer.
    if (deferred && producerContext_ && producerContext_->overlayCommandBuffer->isRecording()) {
        prepareSharedProducerInputs();
        applyWorldUpdates(*producerContext_);
        return VK_SUCCESS;
    }
    if (std::exchange(rebuildAfterRecording_, false)) {
        const auto rebuild = recreate(std::exchange(forcePresentationRebuild_, false));
        if (rebuild != VK_SUCCESS) return rebuild;
    }
    return acquireContext(mcvr::FrameExecution::enabled());
}

VkResult Framework::activateProducerFrame() {
    if (deferredInputAcquire != this || rebuildAfterRecording_ || vk::Window::framebufferResized ||
        Renderer::options.presentationChanged) {
        mcvr::FrameExecution::Producer gate(true, "Acquire frame with shared state");
        return acquireInputContext();
    }
    auto context = acquireOverlayContext();
    if (!context) return VK_NOT_READY;
    if (!context->splitWorldInputs) {
        if (!context->entityInputs) context->entityInputs = std::make_unique<EntityFrameInputs>();
        if (!context->worldUploads)
            context->worldUploads = std::make_shared<std::vector<std::shared_ptr<vk::DeviceLocalBuffer>>>();
        context->splitWorldInputs = true;
        mcvr::FrameExecution::setProducerWorldActive(true);
        Renderer::instance().world(context.get())->entities()->resetFrame();
    }
    return VK_SUCCESS;
}

const FrameworkContext *Framework::worldInputSlot() const {
    if (SceneRecordingScope::active()) return nullptr;
    if (const auto *worker = static_cast<const FrameworkContext *>(mcvr::FrameExecution::workerRecordingSlot()))
        return worker->splitWorldInputs ? worker : nullptr;
    if (!mcvr::FrameExecution::producerWorldActive()) return nullptr;
    return static_cast<const FrameworkContext *>(mcvr::FrameExecution::producerRecordingSlot());
}

FrameworkContext *Framework::producerWorldInput() {
    if (!mcvr::FrameExecution::producerWorldActive()) return nullptr;
    return const_cast<FrameworkContext *>(worldInputSlot());
}

bool Framework::stageWorldUpdate(std::function<void()> update) {
    auto *slot = producerWorldInput();
    if (!slot) return false;
    slot->pendingWorldUpdates.push_back(std::move(update));
    return true;
}

void Framework::applyWorldUpdates(FrameworkContext &context) {
    for (auto &update : context.pendingWorldUpdates) update();
    context.pendingWorldUpdates.clear();
}

void Framework::releaseWorldInputs() {
    mcvr::FrameExecution::checkAccess("release world inputs");
    mcvr::FrameExecution::setProducerWorldActive(false);
    for (const auto &slot : contexts_) {
        slot->splitWorldInputs = false;
        slot->entityInputs.reset();
        slot->worldUploads.reset();
        slot->pendingWorldUpdates.clear();
        slot->worldUniform.reset();
        slot->skyUniform.reset();
        slot->pendingTextureMapping.clear();
        slot->cameraEffects.clear();
        slot->sceneSpaces.reset();
    }
}

VkResult Framework::acquireSwapchainImage(std::shared_ptr<vk::Semaphore> &semaphore, uint32_t &imageIndex) {
    semaphore = acquireSemaphore();
    mcvr::profile::Scope auditWait("swapchain-acquire");
    const auto acquireStart = mcvr::fgdiag::start();
    const auto result = vkAcquireNextImageKHR(device_->vkDevice(), swapchain_->vkSwapchain(), UINT64_MAX,
                                              semaphore->vkSemaphore(), VK_NULL_HANDLE, &imageIndex);
    mcvr::fgdiag::elapsed(mcvr::fgdiag::MainAcquire, acquireStart);
    if (result == VK_SUBOPTIMAL_KHR) suboptimalSwapchain_ = true;
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) recycleSemaphore(std::exchange(semaphore, nullptr));
    return result;
}

VkResult Framework::acquireContext(bool lateImageAcquire, bool overlayOnly) {
    if (!overlayOnly) mcvr::FrameExecution::checkAccess("frame acquisition");
    mcvr::profile::Scope auditProfile("acquire-other");
    if (mcvr::diagnostics::device_loss::enabled()) ++mcvr::diagnostics::device_loss::observedFrame;
    mcvr::diagnostics::device_loss::note("frame-acquire-begin");
    if (!running_.load(std::memory_order_acquire)) { return inactiveResult(); }
    if (device_ == nullptr || device_->hasFailure()) {
        running_.store(false, std::memory_order_release);
        return inactiveResult();
    }
    if (auto drawable = waitForDrawableWindow(); drawable != VK_SUCCESS) return drawable;

    std::shared_ptr<FrameworkContext> lastContext;
    if (producerContext_) lastContext = producerContext_;

    std::shared_ptr<vk::Semaphore> imageAcquiredSemaphore;
    uint32_t imageIndex = 0;
    VkResult result = VK_SUCCESS;
    if (!lateImageAcquire) {
        mcvr::FrameAcquireAttempt acquireAttempt;
        for (uint32_t attempt = 0; attempt < 2; ++attempt) {
            acquireAttempt.observe(acquireSwapchainImage(imageAcquiredSemaphore, imageIndex));
            if (!acquireAttempt.requiresRecreate()) break;
            const VkResult recreateResult = recreate(true);
            if (recreateResult != VK_SUCCESS) return recreateResult;
        }
        if (!acquireAttempt.acquired()) {
            producerContext_.reset();
            if (acquireAttempt.requiresRecreate()) return acquireAttempt.exhaustedResult();
            mcvr::log::error("RenderFramework") << "Cannot acquire images from swapchain" << std::endl;
            return recordFailure(acquireAttempt.failure(), "vkAcquireNextImageKHR");
        }
    }

    const uint32_t slot = nextContextIndex_;
    const auto &context = contexts_[slot];
    const auto &fence = context->commandFinishedFence;
    {
        mcvr::profile::Scope auditWait("frame-fence-wait");
        const auto fenceStart = mcvr::fgdiag::start();
        result = vkWaitForFences(device_->vkDevice(), 1, &fence->vkFence(), true, UINT64_MAX);
        mcvr::diagnostics::device_loss::note("frame-fence-wait", result, slot, imageIndex, (uint64_t)fence->vkFence());
        mcvr::fgdiag::elapsed(mcvr::fgdiag::MainFence, fenceStart);
    }
    if (result != VK_SUCCESS) {
        mcvr::log::info("RenderFramework") << "vkWaitForFences failed with error: " << std::dec << result << std::endl;
        producerContext_ = nullptr;
        return recordFailure(result, "vkWaitForFences(frame)");
    }
    if (context->frameSubmitted) mcvr::chunkTrace::note("frame-complete", -1, -1, context->chunkTraceSerial);
    result = completeGpuProfile(context);
    if (result != VK_SUCCESS) { return result; }
    nextContextIndex_ = (slot + 1) % recordingSlotCount;
    producerContext_ = context;
    mcvr::FrameExecution::setProducerRecordingSlot(context.get());
    producerContext_->presentImageIndex = imageIndex;
    producerContext_->lateImageAcquire = lateImageAcquire;
    producerContext_->presentedSdkFrames = 0;
    producerContext_->preWorldCommandBuffer.reset();
    producerContext_->overlayCommandBuffer = overlayCommandBuffers_[slot];
    producerContext_->splitWorldInputs = false;
    producerContext_->buildWorldEntities = false;
    producerContext_->worldUniform.reset();
    producerContext_->skyUniform.reset();
    producerContext_->pendingTextureMapping.clear();
    producerContext_->cameraEffects.clear();
    producerContext_->streamlineToken = nullptr;
    producerContext_->streamlinePacing = false;
    producerContext_->timeline = {};
    producerContext_->worldRenderRequired = false;
    producerContext_->worldInputsFinished = false;
    producerContext_->worldRendered = false;
    producerContext_->fgGameFrame = false;
    producerContext_->fgHudlessCaptured = false;
    producerContext_->fgGuiRole = mcvr::ui::GuiRole::Foreground;
    producerContext_->fgGui = {};
    producerContext_->fgBackgroundDependentDraws = 0;
    producerContext_->fgAffineBackgroundDraws = 0;
    producerContext_->fgWeightedBlurPasses = 0;
    producerContext_->frameSubmitted = false;
    producerContext_->uiPtCommands.newFrame();
    ++producerContext_->uiPtRecordingGeneration;
    if (mcvr::chunkTrace::enabled) producerContext_->chunkTraceSerial = ++mcvr::chunkTrace::serial;
    // One queue signals fences in submission order, so any signaled context fence proves every frame
    // submitted before it complete, whichever swapchain image those frames used. This context's fence
    // was just waited; the others are only polled.
    uint64_t completedSerial = producerContext_->submittedSerial;
    for (const auto &context : contexts_) {
        if (overlayOnly && context == submittedContext_) continue;
        if (context->submittedSerial > completedSerial &&
            vkGetFenceStatus(device_->vkDevice(), context->commandFinishedFence->vkFence()) == VK_SUCCESS)
            completedSerial = context->submittedSerial;
    }
    producerContext_->frameSerial = ++frameSerial_;
    frameResourceRetainer_->beginFrame(frameSerial_, completedSerial);
    mcvr::diagnostics::recordFrame(
        "acquire", slot, producerContext_->frameSubmitted,
        mcvr::diagnostics::handleValue(producerContext_->commandFinishedFence->vkFence()),
        mcvr::diagnostics::handleValue(producerContext_->overlayCommandBuffer->vkCommandBuffer()));

    if (producerContext_->imageAcquiredSemaphore != VK_NULL_HANDLE) {
        recycleSemaphore(producerContext_->imageAcquiredSemaphore);
        producerContext_->imageAcquiredSemaphore = VK_NULL_HANDLE;
    }
    producerContext_->imageAcquiredSemaphore = imageAcquiredSemaphore;

    producerContext_->uploadCommandBuffer->begin();
    producerContext_->worldCommandBuffer->begin();
    producerContext_->overlayCommandBuffer->begin();
    producerContext_->fuseCommandBuffer->begin();
    producerContext_->presentCommandBuffer->begin();
    producerContext_->auditGpu.reset(device_->vkDevice(), producerContext_->uploadCommandBuffer->vkCommandBuffer(),
                                     timestampValidBits_ != 0);
    if (producerContext_->auditGpu.lastResult == VK_ERROR_DEVICE_LOST)
        return recordFailure(VK_ERROR_DEVICE_LOST, "vkCreateQueryPool(audit timestamps)");
    producerContext_->auditUpload =
        producerContext_->auditGpu.begin(producerContext_->uploadCommandBuffer->vkCommandBuffer(), "upload-buffer");
    producerContext_->auditWorld =
        producerContext_->auditGpu.begin(producerContext_->worldCommandBuffer->vkCommandBuffer(), "world-buffer");
    producerContext_->auditOverlay =
        producerContext_->auditGpu.begin(producerContext_->overlayCommandBuffer->vkCommandBuffer(), "ui-buffer");
    producerContext_->auditFuse =
        producerContext_->auditGpu.begin(producerContext_->fuseCommandBuffer->vkCommandBuffer(), "fuse-buffer");
    if (producerContext_->frameTimestampQueryPool != VK_NULL_HANDLE) {
        vkCmdResetQueryPool(producerContext_->uploadCommandBuffer->vkCommandBuffer(),
                            producerContext_->frameTimestampQueryPool, 0, 2);
        vkCmdWriteTimestamp(producerContext_->uploadCommandBuffer->vkCommandBuffer(), VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                            producerContext_->frameTimestampQueryPool, 0);
    }

    auto pipelineContext = pipeline_->acquirePipelineContext(producerContext_);
    // Only the producer writes the persistent raster state inherited from the preceding slot.
    std::shared_ptr<UIModuleContext> lastUIContext =
        lastContext == nullptr ? nullptr : pipeline_->contexts().at(lastContext->frameIndex)->uiModuleContext;

    if (overlayOnly) Renderer::instance().buffers(context.get())->beginOverlayFrame(slot);
    pipelineContext->uiModuleContext->begin(lastUIContext, overlayOnly);
    producerInputsPending_ = overlayOnly;
    if (!overlayOnly) resetProducerInputs(false);
    static int frames = 0;
    static auto lastTime = std::chrono::high_resolution_clock::now();

    frames++;
    auto currentTime = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> elapsed = currentTime - lastTime;

    if (elapsed.count() >= 1.0) {
        std::stringstream ss;
        ss << "FPS: " << frames;

        // GLFW_SetWindowTitle(window_->window(), ss.str().c_str());

        frames = 0;
        lastTime = currentTime;
    }
    return VK_SUCCESS;
}

void Framework::prepareWorldComposition() {
    if (!producerInputsPending_) return;
    mcvr::FrameExecution::Producer gate(true, "Prepare overlay descriptor table for world composition");
    prepareSharedProducerInputs();
}

void Framework::prepareSharedProducerInputs() {
    if (!std::exchange(producerInputsPending_, false)) return;
    pipeline_->acquirePipelineContext(producerContext_)->uiModuleContext->bindOverlayForFrame();
    resetProducerInputs(true);
}

void Framework::resetProducerInputs(bool overlayAlreadyOpen) {
    auto &renderer = Renderer::instance();
    if (!producerContext_->splitWorldInputs) renderer.buffers()->resetFrame(overlayAlreadyOpen);
    const auto textures = renderer.textures();
    textures->resetFrame();
    if (!producerContext_->pendingTextureUpdates.empty()) {
        auto updates = std::move(producerContext_->pendingTextureUpdates);
        const auto bytes = std::exchange(producerContext_->pendingTextureBytes, 0);
        if (mcvr::profile::enabled())
            mcvr::profile::emit(mcvr::profile::frame, 5, "frame-inputs.texture-updates", updates.size(), bytes);
        for (auto &update : updates) update(*textures);
        updates.clear();
        producerContext_->pendingTextureUpdates.swap(updates);
    }
    if (producerContext_->splitWorldInputs) return;
    auto world = renderer.world();
    world->resetFrame();
    world->entities()->resetFrame();
}

void Framework::applyWorldInputs(FrameworkContext &context) {
    if (!context.splitWorldInputs) return;
    mcvr::profile::Scope timing("submit.world-inputs");
    applyWorldUpdates(context);
    auto &renderer = Renderer::instance();
    auto world = renderer.world(&context);
    applyWorldUniforms(context);
    if (std::exchange(context.buildWorldEntities, false)) world->entities()->build();
}

void Framework::applyWorldUniforms(FrameworkContext &context) {
    if (!context.splitWorldInputs) return;
    mcvr::FrameExecution::checkAccess("apply world uniforms", &context);
    auto buffers = Renderer::instance().buffers(&context);
    if (!context.pendingTextureMapping.empty()) {
        buffers->setAndUploadTextureMappingBuffer(context.pendingTextureMapping.data());
        context.pendingTextureMapping.clear();
    }
    if (context.worldUniform) {
        buffers->setAndUploadWorldUniformBuffer(*context.worldUniform, true);
        if (!context.cameraEffects.empty()) {
            auto buffer = buffers->worldUniformBuffer();
            auto *ubo = static_cast<vk::Data::WorldUBO *>(buffer->mappedPtr());
            for (auto &effect : context.cameraEffects) effect(*ubo);
            buffer->flush();
        }
        context.worldUniform.reset();
        context.cameraEffects.clear();
    }
    if (context.skyUniform) {
        buffers->setAndUploadSkyUniformBuffer(*context.skyUniform);
        context.skyUniform.reset();
    }
}

VkResult Framework::submitCommand() {
    mcvr::profile::Scope auditProfile("submit-record-other");
    mcvr::diagnostics::device_loss::note("frame-submit-begin");
    if (!running_.load(std::memory_order_acquire)) { return inactiveResult(); }

    const auto context = safeAcquireCurrentContext();
    if (!context) return inactiveResult();

    context->noteTimeline(FrameworkContext::Timeline::Inputs);
    mcvr::failure::runCheckedStage([&] { applyWorldInputs(*context); });
    context->noteTimeline(FrameworkContext::Timeline::Textures);
    mcvr::profile::Phases submitStages("submit.texture-upload");
    mcvr::failure::runCheckedStage([&] { Renderer::instance().textures()->performQueuedUpload(); });
    context->noteTimeline(FrameworkContext::Timeline::Buffers);
    submitStages.next("submit.buffer-upload");
    mcvr::failure::runCheckedStage([&] { Renderer::instance().buffers()->performQueuedUpload(); });
    submitStages.next("submit.overlay-uniforms");
    mcvr::failure::runCheckedStage([&] { Renderer::instance().buffers()->buildAndUploadOverlayUniformBuffer(); });

    submitStages.next("submit.pipeline-context");
    auto pipelineContext = pipeline_->acquirePipelineContext(context);
    context->noteTimeline(FrameworkContext::Timeline::World);
    submitStages.next("submit.world-render");
    if (Renderer::instance().world()->shouldRender() && !context->worldRendered) {
        worldDrawStarted_.store(true, std::memory_order_release);
        mcvr::failure::runCheckedStage([&] { pipelineContext->worldPipelineContext->render(); });
        context->worldRendered = true;
    }
    submitStages.next("submit.ui-end");
    pipelineContext->uiModuleContext->end();

    context->noteTimeline(FrameworkContext::Timeline::Compose);
    submitStages.next("submit.fuse");
    const auto finalColor = context->composeFinal();
    context->noteTimeline(FrameworkContext::Timeline::Acquire);
    if (context->lateImageAcquire) {
        const auto acquired = acquireSwapchainImage(context->imageAcquiredSemaphore, context->presentImageIndex);
        if (acquired != VK_SUCCESS && acquired != VK_SUBOPTIMAL_KHR && acquired != VK_ERROR_OUT_OF_DATE_KHR)
            return recordFailure(acquired, "vkAcquireNextImageKHR(executing frame)");
    }
    const bool copyToSwapchain = context->imageAcquiredSemaphore != nullptr;
    if (copyToSwapchain) context->recordPresentCopy(finalColor);

    submitStages.next("submit.command-end");
    if (context->frameTimestampQueryPool != VK_NULL_HANDLE) {
        const auto &timestampBuffer = copyToSwapchain ? context->presentCommandBuffer : context->fuseCommandBuffer;
        vkCmdWriteTimestamp(timestampBuffer->vkCommandBuffer(), VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                            context->frameTimestampQueryPool, 1);
    }

    context->auditGpu.owner = mcvr::profile::frame;
    context->auditGpu.end(context->uploadCommandBuffer->vkCommandBuffer(), context->auditUpload);
    context->auditGpu.end(context->worldCommandBuffer->vkCommandBuffer(), context->auditWorld);
    context->auditGpu.end(context->overlayCommandBuffer->vkCommandBuffer(), context->auditOverlay);
    context->auditGpu.end(context->fuseCommandBuffer->vkCommandBuffer(), context->auditFuse);
    context->uploadCommandBuffer->end();
    context->worldCommandBuffer->end();
    context->uiPtCommands.end();
    context->overlayCommandBuffer->end();
    context->fuseCommandBuffer->end();
    context->presentCommandBuffer->end();

    submitStages.next("submit.submission");
    auto &frame = *context;
    std::array<VkCommandBuffer, 6> offscreen{};
    uint32_t count = 0;
    offscreen[count++] = frame.uploadCommandBuffer->vkCommandBuffer();
    if (frame.preWorldCommandBuffer) offscreen[count++] = frame.preWorldCommandBuffer->vkCommandBuffer();
    offscreen[count++] = frame.worldCommandBuffer->vkCommandBuffer();
    if (frame.uiPtCommands.active()) offscreen[count++] = frame.uiPtCommands.buffer()->vkCommandBuffer();
    offscreen[count++] = frame.overlayCommandBuffer->vkCommandBuffer();
    offscreen[count++] = frame.fuseCommandBuffer->vkCommandBuffer();
    const VkCommandBuffer presentCopy = frame.presentCommandBuffer->vkCommandBuffer();
    const VkPipelineStageFlags acquireStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    // One submission call covers both batches and the fence. Recording a second
    // batch scopes the acquire wait without introducing a partial-submit state.
    const VkSubmitInfo batches[] = {
        {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = count, .pCommandBuffers = offscreen.data()},
        {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
         .waitSemaphoreCount = 1,
         .pWaitSemaphores = copyToSwapchain ? &frame.imageAcquiredSemaphore->vkSemaphore() : nullptr,
         .pWaitDstStageMask = &acquireStage,
         .commandBufferCount = 1,
         .pCommandBuffers = &presentCopy,
         .signalSemaphoreCount = 1,
         .pSignalSemaphores =
             copyToSwapchain ? &presentReadySemaphores_[frame.presentImageIndex]->vkSemaphore() : nullptr},
    };

    std::shared_ptr<vk::Fence> fence = context->commandFinishedFence;
    mcvr::failure::throwIfFatal();
    VkResult result = vkResetFences(device_->vkDevice(), 1, &fence->vkFence());
    if (result != VK_SUCCESS) { return recordFailure(result, "vkResetFences(frame)"); }
    {
        mcvr::profile::Scope auditWait("queue-submit");
        const auto submitStart = mcvr::fgdiag::start();
        // An out-of-date surface still submits uploads and the offscreen frame. Presentation
        // rebuilds the swapchain on the producer before the next frame can reference it.
        context->noteTimeline(FrameworkContext::Timeline::Submit);
        result = vkQueueSubmit(device_->mainVkQueue(), copyToSwapchain ? 2 : 1, batches, fence->vkFence());
        context->noteTimeline(FrameworkContext::Timeline::Submitted);
        if (mcvr::diagnostics::device_loss::enabled()) {
            for (uint32_t i = 0; i < count; ++i)
                mcvr::diagnostics::device_loss::note("frame-queue-submit", result, context->frameIndex, i,
                                                     (uint64_t)offscreen[i], (uint64_t)fence->vkFence(), 0,
                                                     (uint64_t)device_->mainVkQueue());
            if (copyToSwapchain)
                mcvr::diagnostics::device_loss::note("frame-present-copy-submit", result, frame.frameIndex, count,
                                                     (uint64_t)presentCopy, (uint64_t)fence->vkFence(), 0,
                                                     (uint64_t)device_->mainVkQueue());
        }
        mcvr::fgdiag::elapsed(mcvr::fgdiag::MainSubmit, submitStart);
    }
    if (result != VK_SUCCESS) { return recordFailure(result, "vkQueueSubmit(frame)"); }
    submitStages.next("submit.commit");
    context->frameSubmitted = true;
    context->submittedSerial = context->frameSerial;
    indexHistory_.push(frame.frameIndex);
    if (indexHistory_.size() > recordingSlotCount) indexHistory_.pop();
    context->auditGpu.submitted = context->auditGpu.recording;
    if (auto world = Renderer::instance().world(); world && context->worldRendered) {
        world->entities()->commitCachedCloudBuild();
        world->entities()->commitRigidModels();
    }
    mcvr::chunkTrace::note("frame-submit", -1, -1, context->chunkTraceSerial);
    context->timestampQuerySubmitted = context->frameTimestampQueryPool != VK_NULL_HANDLE;
    mcvr::diagnostics::recordFrame("submit", context->frameIndex, context->frameSubmitted,
                                   mcvr::diagnostics::handleValue(fence->vkFence()),
                                   mcvr::diagnostics::handleValue(context->overlayCommandBuffer->vkCommandBuffer()));
    return VK_SUCCESS;
}

VkResult Framework::flushForReadback() {
    mcvr::profile::Scope auditProfile("readback-flush");
    std::lock_guard lock(recreateMtx_);
    if (!isRunning()) return inactiveResult();
    auto context = safeAcquireCurrentContext();
    if (!context) return VK_ERROR_INITIALIZATION_FAILED;
    context->auditGpu.invalidate();
    if (context->frameSubmitted) {
        const auto fence = context->commandFinishedFence->vkFence();
        const auto result = vkWaitForFences(device_->vkDevice(), 1, &fence, VK_TRUE, UINT64_MAX);
        return result == VK_SUCCESS ? result : recordFailure(result, "vkWaitForFences(read submitted frame)");
    }
    mcvr::failure::runCheckedStage([&] { applyWorldInputs(*context); });
    mcvr::failure::runCheckedStage([&] { Renderer::instance().textures()->performQueuedUpload(); });
    mcvr::failure::runCheckedStage([&] { Renderer::instance().buffers()->performQueuedUpload(); });
    mcvr::failure::runCheckedStage([&] { Renderer::instance().buffers()->buildAndUploadOverlayUniformBuffer(); });
    auto pipelineContext = pipeline_->acquirePipelineContext(context);
    pipelineContext->uiModuleContext->end();
    // A mid-world offscreen read must not render a partial TLAS. fuseWorld marks
    // when the complete world output is a dependency of the recorded overlay work.
    if (context->worldRenderRequired && !context->worldRendered && Renderer::instance().world()->shouldRender()) {
        worldDrawStarted_.store(true, std::memory_order_release);
        mcvr::failure::runCheckedStage([&] { pipelineContext->worldPipelineContext->render(); });
        context->worldRendered = true;
    }
    if (pipeline_->uiModule()->uiPtCollecting)
        throw std::runtime_error("Readback during incomplete UI PT scene collection is not supported");
    std::vector recordings{context->uploadCommandBuffer, context->worldCommandBuffer,
                           context->recordingOverlayCommands()};
    if (context->uiPtCommands.active()) recordings.insert(recordings.begin() + 2, context->uiPtCommands.buffer());
    // The prefix was already ended at the world boundary. A readback may submit it
    // once, then subsequent work continues in the suffix buffer.
    const auto prefix = context->preWorldCommandBuffer;
    std::vector<VkCommandBuffer> commands;
    commands.reserve(recordings.size() + bool(prefix));
    for (const auto &recording : recordings) {
        recording->end();
        commands.push_back(recording->vkCommandBuffer());
    }
    if (prefix) commands.insert(commands.begin() + 1, prefix->vkCommandBuffer());
    auto fence = vk::Fence::create(device_);
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submit.commandBufferCount = commands.size();
    submit.pCommandBuffers = commands.data();
    // These commands touch compositor/FBO images, not the acquired swapchain image.
    // Leave the acquire and present semaphores for the final frame submission.
    mcvr::failure::throwIfFatal();
    auto result = vkQueueSubmit(device_->mainVkQueue(), 1, &submit, fence->vkFence());
    if (mcvr::diagnostics::device_loss::enabled())
        for (uint32_t i = 0; i < commands.size(); ++i)
            mcvr::diagnostics::device_loss::note("readback-queue-submit", result, context->frameIndex, i,
                                                 (uint64_t)commands[i], (uint64_t)fence->vkFence(), 0,
                                                 (uint64_t)device_->mainVkQueue());
    if (result != VK_SUCCESS) return recordFailure(result, "vkQueueSubmit(readback flush)");
    if (auto world = Renderer::instance().world(); world && context->worldRendered) {
        world->entities()->commitCachedCloudBuild();
        world->entities()->commitRigidModels();
    }
    result = vkWaitForFences(device_->vkDevice(), 1, &fence->vkFence(), VK_TRUE, UINT64_MAX);
    if (result != VK_SUCCESS) return recordFailure(result, "vkWaitForFences(readback flush)");
    context->preWorldCommandBuffer.reset();
    for (const auto &recording : recordings) recording->reset()->begin();
    return VK_SUCCESS;
}

// Routine surface/rebuild messages use the ordinary logger, not a diagnostic file.
static void radianceDiag(const std::string &line) {
    mcvr::log::debug("RenderFramework") << line << std::endl;
}

// Temporary performance diagnostics: total device memory held by VMA allocations.
static uint64_t vmaAllocatedBytes(const std::shared_ptr<vk::VMA> &vma) {
    if (vma == nullptr) return 0;
    VmaBudget budgets[VK_MAX_MEMORY_HEAPS]{};
    vmaGetHeapBudgets(vma->allocator(), budgets);
    uint64_t total = 0;
    for (uint32_t i = 0; i < VK_MAX_MEMORY_HEAPS; ++i) total += budgets[i].statistics.allocationBytes;
    return total;
}

// Diagnostics only (RADIANCE_CHUNK_CENSUS=1): memory VMA holds from the driver (blocks, including
// dedicated allocations) versus live allocations, per heap, plus chunk buffers alive outside published
// slots. Logged from presentation so it also covers the title screen, where no world frame runs.
static void logVmaResidency(const std::shared_ptr<vk::VMA> &vma) {
    if (vma == nullptr) return;
    const VkPhysicalDeviceMemoryProperties *properties = nullptr;
    vmaGetMemoryProperties(vma->allocator(), &properties);
    VmaBudget budgets[VK_MAX_MEMORY_HEAPS]{};
    vmaGetHeapBudgets(vma->allocator(), budgets);
    std::ostringstream out;
    for (uint32_t i = 0; properties != nullptr && i < properties->memoryHeapCount; ++i) {
        const auto &budget = budgets[i];
        if (budget.statistics.blockCount == 0 && budget.statistics.allocationCount == 0) continue;
        out << " heap" << i
            << ((properties->memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) ? "-device" : "-host") << "=blocks"
            << budget.statistics.blockCount << '/' << (budget.statistics.blockBytes >> 20) << "MB"
            << ",allocations" << budget.statistics.allocationCount << '/' << (budget.statistics.allocationBytes >> 20)
            << "MB"
            << ",usage" << (budget.usage >> 20) << "MB,budget" << (budget.budget >> 20) << "MB";
    }
    if (auto world = Renderer::instance().world(); world != nullptr && world->chunks() != nullptr)
        out << world->chunks()->orphanCensus();
    mcvr::log::info("VmaResidency") << out.str() << std::endl;
}

static uint64_t vmaAllocationCount(const std::shared_ptr<vk::VMA> &vma) {
    if (vma == nullptr) return 0;
    VmaBudget budgets[VK_MAX_MEMORY_HEAPS]{};
    vmaGetHeapBudgets(vma->allocator(), budgets);
    uint64_t total = 0;
    for (uint32_t i = 0; i < VK_MAX_MEMORY_HEAPS; ++i) total += budgets[i].statistics.allocationCount;
    return total;
}

void Framework::blurFrameGenerationHudless(FrameworkContext &frame,
                                           const vk::Data::OverlayPostUBO &parameters,
                                           const std::shared_ptr<vk::DeviceLocalImage> &color) {
    if (frameGeneration_ && frame.fgHudlessCaptured) frameGeneration_->blurHudless(*this, frame, parameters, color);
}

std::shared_ptr<vk::DeviceLocalImage> Framework::frameGenerationHudless(FrameworkContext &frame) const {
    return frameGeneration_ && frame.fgHudlessCaptured ? frameGeneration_->hudless(frame.frameIndex) : nullptr;
}

void Framework::captureFrameGenerationHudless(FrameworkContext &frame,
                                              const std::shared_ptr<vk::DeviceLocalImage> &color) {
    if (!Renderer::options.dlssFrameGeneration) return;
    if (!frameGeneration_) frameGeneration_ = std::make_unique<DlssFrameGeneration>();
    frameGeneration_->captureHudless(*this, frame, color);
}

VkResult Framework::present(bool trackPcl) {
    mcvr::profile::Scope auditProfile("present-other");
    const auto context = recordingContext();
    const auto result = presentToQueue(trackPcl);
    return context ? finishPresentation(result, *context) : result;
}

VkResult Framework::presentToQueue(bool trackPcl) {
    mcvr::FrameExecution::checkAccess("presentation");
    mcvr::diagnostics::device_loss::note("frame-present-begin");
    if (!running_.load(std::memory_order_acquire)) { return inactiveResult(); }
    const auto context = recordingContext();
    if (!context) return recordFailure(VK_ERROR_INITIALIZATION_FAILED, "present(no context)");
    mcvr::FrameExecution::checkAccess("presentation", context.get());
    if (context->lateImageAcquire && !context->imageAcquiredSemaphore) return VK_ERROR_OUT_OF_DATE_KHR;

    VkPresentInfoKHR presentInfo = {};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    const auto &ready = presentReadySemaphores_[context->presentImageIndex];
    presentInfo.pWaitSemaphores = &ready->vkSemaphore();

    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &swapchain_->vkSwapchain();
    presentInfo.pImageIndices = &context->presentImageIndex;

    VkResult result;
    {
        mcvr::profile::Scope auditWait("queue-present");
        const auto presentStart = mcvr::fgdiag::start();
        if (trackPcl) mcvr::StreamlineRuntime::get().marker(sl::PCLMarker::ePresentStart);
        result = vkQueuePresentKHR(device_->mainVkQueue(), &presentInfo);
        mcvr::diagnostics::device_loss::note("frame-queue-present", result, context->frameIndex,
                                             context->presentImageIndex, (uint64_t)swapchain_->vkSwapchain(),
                                             (uint64_t)ready->vkSemaphore(), 0, (uint64_t)device_->mainVkQueue());
        mcvr::fgdiag::elapsed(mcvr::fgdiag::FirstPresent, presentStart);
    }
    if (trackPcl) mcvr::StreamlineRuntime::get().marker(sl::PCLMarker::ePresentEnd);
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR && result != VK_ERROR_OUT_OF_DATE_KHR)
        return recordFailure(result, "vkQueuePresentKHR");
    context->presentedSdkFrames = mcvr::StreamlineRuntime::get().presentedFrames();
    return result;
}

VkResult Framework::finishPresentation(VkResult result, const FrameworkContext &frame) {
    mcvr::FrameExecution::checkAccess("presentation completion");
    if (!running_.load(std::memory_order_acquire)) return inactiveResult();
    const bool firstAccepted = result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR;
    const auto outputCount = frame.presentedSdkFrames;
    if (mcvr::audit::enabled(MCVR_AUDIT_CAPTURE)) {
        auto nativeWindow = window_->window();
        auto evidence = mcvr::log::info("RenderFramework");
        evidence << "FG_WINDOW serial=" << frameSerial_ << " glfwWindow=" << nativeWindow
                 << " glfwFocused=" << GLFW_GetWindowAttrib(nativeWindow, GLFW_FOCUSED)
                 << " glfwVisible=" << GLFW_GetWindowAttrib(nativeWindow, GLFW_VISIBLE)
                 << " glfwIconified=" << GLFW_GetWindowAttrib(nativeWindow, GLFW_ICONIFIED)
                 << " swapchain=" << (uint64_t)swapchain_->vkSwapchain();
#ifdef _WIN32
        const HWND application = p_glfwGetWin32Window ? p_glfwGetWin32Window(nativeWindow) : nullptr;
        const HWND foreground = GetForegroundWindow();
        DWORD applicationPid = 0, foregroundPid = 0;
        if (application) GetWindowThreadProcessId(application, &applicationPid);
        if (foreground) GetWindowThreadProcessId(foreground, &foregroundPid);
        evidence << " appHwnd=" << application
                 << " appRoot=" << (application ? GetAncestor(application, GA_ROOT) : nullptr)
                 << " foregroundHwnd=" << foreground
                 << " foregroundRoot=" << (foreground ? GetAncestor(foreground, GA_ROOT) : nullptr)
                 << " appPid=" << applicationPid << " foregroundPid=" << foregroundPid;
#endif
        evidence << std::endl;
    }
    if (mcvr::audit::enabled(MCVR_AUDIT_CAPTURE))
        mcvr::log::info("RenderFramework") << "FG_PRESENT serial=" << frame.frameSerial << " frame=" << frame.frameIndex
                                           << " game=" << frame.fgGameFrame << " world=" << frame.worldRendered
                                           << " accepted=" << firstAccepted << " output=" << outputCount << std::endl;
    mcvr::presentationRates.recordCounts(firstAccepted,
                                         Renderer::options.dlssFrameGeneration ? outputCount : uint32_t(firstAccepted));
    if (frameSlotDiagnostics() && firstAccepted && frame.frameSerial % 32 == 0)
        mcvr::StreamlineRuntime::get().reportLatency();
    static const bool vmaResidencyLog = [] {
        const char *value = std::getenv("RADIANCE_CHUNK_CENSUS");
        return value && std::string_view(value) == "1";
    }();
    static uint64_t vmaResidencyPresents = 0;
    if (vmaResidencyLog && ++vmaResidencyPresents % 60 == 0) logVmaResidency(vma());
    if (auto observer = mcvr::audit::sink.load(std::memory_order_acquire);
        observer && mcvr::audit::enabled(MCVR_AUDIT_ALLOCATIONS | MCVR_AUDIT_TIMING) && observer->wantsFrame()) {
        mcvr::profile::Scope basicTelemetry("observer.basic-telemetry");
        McvrAuditFrame frame{vmaAllocatedBytes(vma()),      vmaAllocationCount(vma()), swapchain_->vkExtent().width,
                             swapchain_->vkExtent().height, swapchain_->imageCount(),  static_cast<int32_t>(result)};
        observer->frame(&frame);
    }

    const auto now = std::chrono::steady_clock::now();
    if (result == VK_SUBOPTIMAL_KHR) suboptimalSwapchain_ = true;
    bool checkSurface = vk::Window::framebufferResized || Renderer::options.presentationChanged ||
                        (suboptimalSwapchain_ && now - lastSurfaceCheck_ >= std::chrono::milliseconds(250));
    // HDR mode and monitor changes need not produce a GLFW resize or SUBOPTIMAL.
    // Unchanged formats only refresh luminance; continue the ordinary frame limiter.
    if (!checkSurface && now - lastSurfaceCheck_ >= std::chrono::milliseconds(500)) {
        lastSurfaceCheck_ = now;
        checkSurface = swapchain_->needsReconstruction();
    }
    if (result == VK_ERROR_OUT_OF_DATE_KHR || checkSurface ||
        (!preparingInitialWorld_ && (Renderer::options.needRecreate || pipeline_->isRecreationNeeded))) {
        if (producerContext_ && producerContext_.get() != &frame &&
            producerContext_->overlayCommandBuffer->isRecording()) {
            rebuildAfterRecording_ = true;
            forcePresentationRebuild_ |= result == VK_ERROR_OUT_OF_DATE_KHR;
            return VK_SUCCESS;
        }
        return recreate(result == VK_ERROR_OUT_OF_DATE_KHR, checkSurface);
    } else if (result == VK_SUBOPTIMAL_KHR) {
        // Still presentable; repeated SUBOPTIMAL reports do not trigger full rebuilds.
        limitFrameRate(frame.lateImageAcquire ? frame.streamlinePacing : mcvr::StreamlineRuntime::get().pacesFrames());
        return VK_SUCCESS;
    }

    limitFrameRate(frame.lateImageAcquire ? frame.streamlinePacing : mcvr::StreamlineRuntime::get().pacesFrames());
    return VK_SUCCESS;
}

uint32_t Framework::effectiveFrameRateLimit() const {
    if (menuFrame_) return 60;
    const uint32_t maxFps = Renderer::options.maxFps;

    if (maxFps == 0 || maxFps >= 260) { return 0; }

    return maxFps;
}

void Framework::limitFrameRate(bool paced) {
    mcvr::profile::Scope auditProfile("frame-limiter");
    if (paced) {
        frameLimitAnchor_ = {};
        return;
    }
    mcvr::fgdiag::Scope timing(mcvr::fgdiag::Limiter);
    const uint32_t fpsLimit = effectiveFrameRateLimit();
    if (fpsLimit == 0) {
        frameLimitAnchor_ = {};
        frameLimitFps_ = 0;
        return;
    }

    const auto frameDuration =
        std::chrono::duration_cast<std::chrono::steady_clock::duration>(std::chrono::duration<double>(1.0 / fpsLimit));
    if (frameDuration <= std::chrono::steady_clock::duration::zero()) { return; }

    const auto now = std::chrono::steady_clock::now();
    if (frameLimitFps_ != fpsLimit || frameLimitAnchor_ == std::chrono::steady_clock::time_point{}) {
        frameLimitAnchor_ = now;
        frameLimitFps_ = fpsLimit;
    }

    const auto target = frameLimitAnchor_ + frameDuration;
    if (now < target) {
        std::this_thread::sleep_until(target);
        frameLimitAnchor_ = std::chrono::steady_clock::now();
    } else {
        frameLimitAnchor_ = now;
    }
}

VkResult Framework::waitForDrawableWindow() {
    static bool drawableMinimized = false;
    int width = 0, height = 0;
    GLFW_GetFramebufferSize(window_->window(), &width, &height);
    if ((width <= 0 || height <= 0) && !drawableMinimized) {
        drawableMinimized = true;
        radianceDiag("[radiance-surface] drawable minimized (0x0)");
    }
    while (width <= 0 || height <= 0) {
        if (nonBlockingResize || GLFW_WindowShouldClose(window_->window()) || !isRunning()) return VK_NOT_READY;
        // The main thread pumps events without retaining the recreation mutex or discarding resources.
        GLFW_WaitEventsTimeout(0.05);
        GLFW_GetFramebufferSize(window_->window(), &width, &height);
    }
    if (drawableMinimized) {
        drawableMinimized = false;
        radianceDiag("[radiance-surface] drawable restored " + std::to_string(width) + "x" + std::to_string(height));
    }
    return VK_SUCCESS;
}

VkResult Framework::recreate(bool forcePresentation, bool checkSurface) {
    if (!running_.load(std::memory_order_acquire)) { return inactiveResult(); }

    if (auto drawable = waitForDrawableWindow(); drawable != VK_SUCCESS) return drawable;

    std::unique_lock<std::recursive_mutex> lck(Renderer::instance().framework()->recreateMtx());
    const bool reportNativeProgress = Pipeline::nativeRebuildActive();
    const auto surfaceCheckTime = std::chrono::steady_clock::now();

    try {
        const bool surfaceStateHandled = checkSurface || forcePresentation;
        auto &sl = mcvr::StreamlineRuntime::get();
        // An unloaded optional plugin cannot change the current surface's mode.
        // Apply its requested state even when the old swapchain still fits.
        const bool fgStateChanged =
            mcvr::optionalFeaturePresentationChanged(sl.ready(), sl.fgLoaded(), Renderer::options.dlssFrameGeneration);
        const bool presentation =
            forcePresentation || (checkSurface && (fgStateChanged || swapchain_->needsReconstruction()));
        const bool worldChanged =
            !preparingInitialWorld_ && (Renderer::options.needRecreate || pipeline_->isRecreationNeeded);
        radianceDiag("[radiance-recreate] enter forcePresentation=" + std::to_string(forcePresentation) +
                     " checkSurface=" + std::to_string(checkSurface) + " presentation=" + std::to_string(presentation) +
                     " worldChanged=" + std::to_string(worldChanged) +
                     " framebufferResized=" + std::to_string(vk::Window::framebufferResized) +
                     " presentationChanged=" + std::to_string(Renderer::options.presentationChanged) +
                     " suboptimal=" + std::to_string(suboptimalSwapchain_));
        if (checkSurface || forcePresentation) lastSurfaceCheck_ = surfaceCheckTime;
        if (!presentation && !worldChanged) {
            if (surfaceStateHandled) {
                vk::Window::framebufferResized = false;
                Renderer::options.presentationChanged = false;
                suboptimalSwapchain_ = false;
            }
            radianceDiag("[radiance-recreate] noop (surface state only)");
            return VK_SUCCESS;
        }

        // Flush SL's asynchronous present worker before destroying tagged inputs.
        const VkResult idleResult = mcvr::StreamlineRuntime::get().ready() ? waitDeviceIdle() : waitRenderQueueIdle();
        if (idleResult != VK_SUCCESS) {
            if (reportNativeProgress) { Pipeline::endNativeRebuild(); }
            return idleResult;
        }
        const VkResult backendIdle = waitBackendQueueIdle();
        if (backendIdle != VK_SUCCESS) return backendIdle;

        for (const auto &context : contexts_) {
            const VkResult profileResult = completeGpuProfile(context);
            if (profileResult != VK_SUCCESS) {
                if (reportNativeProgress) { Pipeline::endNativeRebuild(); }
                return profileResult;
            }
        }
        if (presentation && fgStateChanged) {
            // Destroy the proxy while its plugin is still hooked. Unloading first
            // hides destruction from DLSS-G and leaves its single-swapchain state alive.
            frameGeneration_.reset();
            swapchain_->release();
        }
        if (presentation && !mcvr::optionalFeatureRequestSatisfied(
                                mcvr::StreamlineRuntime::get().setFGLoaded(Renderer::options.dlssFrameGeneration))) {
            mcvr::log::error("RenderFramework") << "Cannot apply the requested DLSS-G plugin state" << std::endl;
            if (reportNativeProgress) { Pipeline::endNativeRebuild(); }
            return VK_ERROR_INITIALIZATION_FAILED;
        }
        const auto oldExtent = swapchain_->vkExtent();
        const auto oldFormat = swapchain_->displayProfile().surface.format;
        const auto oldEncoding = swapchain_->displayProfile().encoding;
        const uint32_t oldCount = swapchain_->imageCount();
        if (presentation) {
            swapchain_->reconstruct();
            rebuildPresentationSemaphores();
            sl.invalidateFramePacing();
        }
        const uint32_t size = swapchain_->imageCount();
        const auto extent = swapchain_->vkExtent();
        const bool resizeTargets = oldExtent.width != extent.width || oldExtent.height != extent.height;
        const bool displayChanged = oldFormat != swapchain_->displayProfile().surface.format ||
                                    oldEncoding != swapchain_->displayProfile().encoding;
        if (resizeTargets || displayChanged) displayOutput_.reset();
        if (resizeTargets || displayChanged || worldChanged)
            frameGeneration_.reset();
        else if (presentation && frameGeneration_)
            frameGeneration_->invalidatePresentationHistory();
        if (resizeTargets || worldChanged) {
            indexHistory_ = {};
            pipeline_->recreate(shared_from_this(), resizeTargets, worldChanged, preparingInitialWorld_);
            Renderer::instance().buffers()->invalidateWorldHistory();
            Renderer::instance().textures()->bindAllTextures();
        }
        {
            radianceDiag("[radiance-recreate] rebuild images=" + std::to_string(size) +
                         " extent=" + std::to_string(extent.width) + "x" + std::to_string(extent.height) +
                         " oldImages=" + std::to_string(oldCount) + " oldExtent=" + std::to_string(oldExtent.width) +
                         "x" + std::to_string(oldExtent.height) + " resizeTargets=" + std::to_string(resizeTargets) +
                         " worldChanged=" + std::to_string(worldChanged) +
                         " vram_mb=" + std::to_string(vmaAllocatedBytes(vma()) >> 20));
            if (pipeline_ && pipeline_->uiModule()) {
                radianceDiag("[radiance-ui] recreate " + pipeline_->uiModule()->resourceDiagnostics());
            }
        }
        if (!preparingInitialWorld_) {
            Renderer::options.needRecreate = false;
            pipeline_->isRecreationNeeded = false;
        }
        if (surfaceStateHandled) {
            Renderer::options.presentationChanged = false;
            vk::Window::framebufferResized = false;
            suboptimalSwapchain_ = false;
        }
        if (reportNativeProgress && !preparingInitialWorld_) { Pipeline::endNativeRebuild(); }
        return VK_SUCCESS;
    } catch (const mcvr::failure::FatalError &failure) {
        renderFrameworkCerr() << "swapchain recreation failed: " << failure.what() << std::endl;
        if (reportNativeProgress) { Pipeline::endNativeRebuild(); }
        return recordFailure(failure.result(), failure.operation().c_str());
    } catch (const std::exception &exception) {
        renderFrameworkCerr() << "swapchain recreation failed: " << exception.what() << std::endl;
        if (reportNativeProgress) { Pipeline::endNativeRebuild(); }
        return recordFailure(VK_ERROR_INITIALIZATION_FAILED, "Framework::recreate");
    } catch (...) {
        renderFrameworkCerr() << "swapchain recreation failed with an unknown exception" << std::endl;
        if (reportNativeProgress) { Pipeline::endNativeRebuild(); }
        return recordFailure(VK_ERROR_UNKNOWN, "Framework::recreate");
    }
}

VkResult Framework::warmupCurrentPipeline(const std::function<void(const char *, size_t, size_t)> &progress) {
    if (!running_.load(std::memory_order_acquire)) { return inactiveResult(); }
    if (pipeline_ == nullptr || pipeline_->worldPipelineBlueprint() == nullptr ||
        (!Renderer::options.needRecreate && !pipeline_->isRecreationNeeded)) {
        return VK_SUCCESS;
    }
    if (producerContext_ == nullptr) { return VK_NOT_READY; }
    auto world = Renderer::instance().world();
    if (world == nullptr || world->shouldRender() || worldDrawStarted_.load(std::memory_order_acquire)) {
        return VK_NOT_READY;
    }

    if (pipeline_->worldPipeline()) return recreate(false, false);

    std::lock_guard lock(recreateMtx_);
    preparingInitialWorld_ = true;
    struct PreparationEnd {
        bool &active;
        ~PreparationEnd() {
            active = false;
        }
    } preparationEnd{preparingInitialWorld_};
    mcvr::ParallelWaitScope pump([&](const char *phase, size_t done, size_t total) {
        SceneRecordingScope::Suspend suspend;
        progress(phase, done, total);
    });
    std::shared_ptr<ShaderPack> reusable;
    for (;;) {
        SceneRecordingState recording;
        recording.buffers = Renderer::instance().buffers();
        recording.world = world;
        recording.contexts = contexts_;
        recording.current = producerContext_;
        auto extent = swapchain_->vkExtent();
        std::shared_ptr<WorldPipeline> prepared;
        {
            SceneRecordingScope scope(recording);
            prepared = WorldPipeline::create(shared_from_this(), pipeline_, reusable,
                                             std::vector<std::shared_ptr<WorldModuleRebuildState>>{}, extent);
        }
        const auto currentExtent = swapchain_->vkExtent();
        if (recording.contexts.size() != contexts_.size() || extent.width != currentExtent.width ||
            extent.height != currentExtent.height) {
            reusable = prepared->shaderPack();
            for (auto &module : prepared->worldModules()) module->preClose();
            continue;
        }
        const VkResult idle = waitDeviceIdle();
        if (idle != VK_SUCCESS) return idle;
        const VkResult backend = waitBackendQueueIdle();
        if (backend != VK_SUCCESS) return backend;
        pipeline_->activatePreparedWorld(shared_from_this(), std::move(prepared));
        Renderer::instance().buffers()->invalidateWorldHistory();
        Renderer::instance().textures()->bindAllTextures();
        Renderer::options.needRecreate = false;
        pipeline_->isRecreationNeeded = false;
        Pipeline::endNativeRebuild();
        return VK_SUCCESS;
    }
}

VkResult Framework::waitDeviceIdle() {
    if (device_ == nullptr) { return VK_SUCCESS; }
    if (device_->isDeviceLost()) { return VK_ERROR_DEVICE_LOST; }
    std::lock_guard backend(device_->secondaryQueueMutex());
    const VkResult result = vkDeviceWaitIdle(device_->vkDevice());
    if (result != VK_SUCCESS) { return recordFailure(result, "vkDeviceWaitIdle"); }
    return VK_SUCCESS;
}

VkResult Framework::waitRenderQueueIdle() {
    if (device_ == nullptr) { return VK_SUCCESS; }
    if (device_->isDeviceLost()) { return VK_ERROR_DEVICE_LOST; }
    const VkResult result = vkQueueWaitIdle(device_->mainVkQueue());
    if (result != VK_SUCCESS) { return recordFailure(result, "vkQueueWaitIdle(main)"); }
    return VK_SUCCESS;
}

VkResult Framework::waitBackendQueueIdle() {
    if (device_ == nullptr) { return VK_SUCCESS; }
    if (device_->isDeviceLost()) { return VK_ERROR_DEVICE_LOST; }
    std::lock_guard backend(device_->secondaryQueueMutex());
    const VkResult result = vkQueueWaitIdle(device_->secondaryQueue());
    if (result != VK_SUCCESS) { return recordFailure(result, "vkQueueWaitIdle(secondary)"); }
    return VK_SUCCESS;
}

void Framework::close() {
    if (deferredInputAcquire == this) {
        deferredInputAcquire = nullptr;
        mcvr::FrameExecution::setDeferredProducerPreparation(nullptr);
    }
    running_.store(false, std::memory_order_release);
    if (!closeGate_.begin()) return;
    if (!isDeviceLost()) waitDeviceIdle();
    frameGeneration_.reset();
    displayOutput_.reset();
    if (pipeline_ != nullptr) { pipeline_->close(); }
}

bool Framework::isRunning() const {
    return running_.load(std::memory_order_acquire);
}

bool Framework::isDeviceLost() const noexcept {
    return device_ == nullptr ? mcvr::failure::isDeviceLost() : device_->isDeviceLost();
}

VkResult Framework::lastFailure() const noexcept {
    return device_ == nullptr ? mcvr::failure::globalState.firstResult() : device_->lastFailure();
}

std::string Framework::lastFailureDescription() const {
    if (device_ == nullptr || !device_->hasFailure()) { return {}; }
    return device_->lastFailureOperation() + " failed with VkResult=" + std::to_string(device_->lastFailure());
}

VkResult Framework::recordFailure(VkResult result, const char *operation) noexcept {
    if (result == VK_SUCCESS) { return result; }
    if (device_ != nullptr) {
        device_->recordFailure(result, operation);
    } else {
        mcvr::failure::record(result == VK_ERROR_DEVICE_LOST ? mcvr::failure::Kind::deviceLost :
                                                               mcvr::failure::Kind::runtime,
                              result, operation);
    }
    running_.store(false, std::memory_order_release);
    if (result == VK_ERROR_DEVICE_LOST) { mcvr::diagnostics::device_loss::dump(operation, result); }
    try {
        renderFrameworkCerr() << operation << " failed with VkResult=" << result
                              << (result == VK_ERROR_DEVICE_LOST ? " (device lost)" : "") << std::endl;
    } catch (...) { mcvr::failure::globalState.noteDetailFailure(); }
    return result;
}

VkResult Framework::inactiveResult() const {
    const VkResult failure = lastFailure();
    return failure == VK_SUCCESS ? VK_NOT_READY : failure;
}

uint32_t Framework::beginGpuProfile() {
    std::lock_guard<std::mutex> lock(gpuProfileMtx_);
    uint32_t sequence = nextGpuProfileSequence_++;
    if (nextGpuProfileSequence_ == 0 ||
        nextGpuProfileSequence_ > static_cast<uint32_t>(std::numeric_limits<int32_t>::max())) {
        nextGpuProfileSequence_ = 1;
    }

    if (producerContext_ == nullptr || producerContext_->frameTimestampQueryPool == VK_NULL_HANDLE ||
        producerContext_->gpuProfileSequence != 0) {
        completedGpuProfiles_[sequence] = 0;
        while (completedGpuProfiles_.size() > 64) { completedGpuProfiles_.erase(completedGpuProfiles_.begin()); }
    } else {
        producerContext_->gpuProfileSequence = sequence;
    }
    return sequence;
}

bool Framework::isGpuProfileReady(uint32_t sequence) {
    std::lock_guard<std::mutex> lock(gpuProfileMtx_);
    return completedGpuProfiles_.contains(sequence);
}

uint64_t Framework::gpuProfileTimeNs(uint32_t sequence) {
    std::lock_guard<std::mutex> lock(gpuProfileMtx_);
    const auto result = completedGpuProfiles_.find(sequence);
    return result == completedGpuProfiles_.end() ? 0 : result->second;
}

VkResult Framework::completeGpuProfile(const std::shared_ptr<FrameworkContext> &context) {
    if (context == nullptr || !context->timestampQuerySubmitted || context->frameTimestampQueryPool == VK_NULL_HANDLE) {
        return VK_SUCCESS;
    }

    uint64_t timestamps[2]{};
    const VkResult result =
        vkGetQueryPoolResults(device_->vkDevice(), context->frameTimestampQueryPool, 0, 2, sizeof(timestamps),
                              timestamps, sizeof(uint64_t), VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT);
    if (result != VK_SUCCESS) { return recordFailure(result, "vkGetQueryPoolResults(frame timestamps)"); }

    if (context->auditGpu.submitted) {
        context->auditGpu.collect(device_->vkDevice(), timestampValidBits_, timestampPeriodNs_);
        if (context->auditGpu.lastResult == VK_ERROR_DEVICE_LOST)
            return recordFailure(VK_ERROR_DEVICE_LOST, "vkGetQueryPoolResults(audit timestamps)");
        const uint64_t mask = timestampValidBits_ >= 64 ? UINT64_MAX : (uint64_t{1} << timestampValidBits_) - 1;
        mcvr::profile::emit(context->auditGpu.owner, 4, "main-queue-frame",
                            static_cast<uint64_t>(((timestamps[1] - timestamps[0]) & mask) * timestampPeriodNs_), 0);
    }
    context->timestampQuerySubmitted = false;
    const uint32_t sequence = context->gpuProfileSequence;
    context->gpuProfileSequence = 0;
    const uint64_t mask =
        timestampValidBits_ >= 64 ? std::numeric_limits<uint64_t>::max() : ((uint64_t{1} << timestampValidBits_) - 1);
    const uint64_t elapsedTicks = (timestamps[1] - timestamps[0]) & mask;
    const long double elapsedNs = static_cast<long double>(elapsedTicks) * timestampPeriodNs_;
    const uint64_t durationNs = elapsedNs >= static_cast<long double>(std::numeric_limits<uint64_t>::max()) ?
                                    std::numeric_limits<uint64_t>::max() :
                                    static_cast<uint64_t>(elapsedNs);
    mcvr::fgdiag::add(mcvr::fgdiag::GpuFrame, double(durationNs) / 1e6);
    if (context->timeline.owner) {
        const auto trace = traceFrameTimeline(*context, timestamps, mask);
        if (trace != VK_SUCCESS) return trace;
    }
    if (frameSlotDiagnostics() || timelineClockAvailable_) {
        if (previousGpuFrameSerial_ && previousGpuFrameSerial_ + 1 == context->submittedSerial) {
            const uint64_t gap = (timestamps[0] - previousGpuEnd_) & mask;
            // TOP of the next frame can run before BOTTOM of the previous frame.
            // Interpret that signed modular interval as no uncovered time.
            mcvr::fgdiag::add(mcvr::fgdiag::GpuFrameGap, gap > mask / 2 ? 0.0 : double(gap) * timestampPeriodNs_ / 1e6);
        }
        previousGpuFrameSerial_ = context->submittedSerial;
        previousGpuEnd_ = timestamps[1];
    }
    if (sequence == 0) { return VK_SUCCESS; }
    storeGpuProfileResult(sequence, durationNs);
    return VK_SUCCESS;
}

VkResult
Framework::traceFrameTimeline(const FrameworkContext &context, const uint64_t (&timestamps)[2], uint64_t mask) {
#ifdef _WIN32
    using Timeline = FrameworkContext::Timeline;
    const VkCalibratedTimestampInfoEXT domains[] = {
        {VK_STRUCTURE_TYPE_CALIBRATED_TIMESTAMP_INFO_EXT, nullptr, VK_TIME_DOMAIN_DEVICE_EXT},
        {VK_STRUCTURE_TYPE_CALIBRATED_TIMESTAMP_INFO_EXT, nullptr, VK_TIME_DOMAIN_QUERY_PERFORMANCE_COUNTER_EXT},
    };
    uint64_t calibration[2]{}, deviation = 0;
    const auto result = vkGetCalibratedTimestampsEXT(device_->vkDevice(), 2, domains, calibration, &deviation);
    if (result == VK_ERROR_DEVICE_LOST) return recordFailure(result, "vkGetCalibratedTimestampsEXT");
    if (result != VK_SUCCESS || deviation > 500'000) return VK_SUCCESS;
    LARGE_INTEGER before{}, after{}, frequency{};
    QueryPerformanceFrequency(&frequency);
    QueryPerformanceCounter(&before);
    const auto host = mcvr::profile::now();
    QueryPerformanceCounter(&after);
    if (frequency.QuadPart <= 0) return VK_SUCCESS;
    const double hostNsPerTick = 1e9 / double(frequency.QuadPart);
    const auto hostSpan = double(after.QuadPart - before.QuadPart) * hostNsPerTick;
    if (hostSpan > 100'000) return VK_SUCCESS;
    const auto hostTicks = before.QuadPart + (after.QuadPart - before.QuadPart) / 2;
    const double offset = double(int64_t(calibration[1]) - hostTicks) * hostNsPerTick;
    const auto convert = [&](uint64_t timestamp) {
        const auto delta = (timestamp - calibration[0]) & mask;
        const auto ticks = delta > mask / 2 ? -double((calibration[0] - timestamp) & mask) : double(delta);
        return uint64_t(double(host) + offset + ticks * timestampPeriodNs_);
    };
    constexpr const char *names[Timeline::Count] = {
        "handoff", "worker", "inputs", "textures", "buffers", "world", "compose", "acquire", "submit", "submitted",
    };
    const auto &timeline = context.timeline;
    std::ostringstream row;
    row << "A4_FRAME_TIMELINE serial=" << context.submittedSerial << " owner=" << timeline.owner
        << " errorNs=" << uint64_t(deviation + hostSpan) << " gpuBegin=" << convert(timestamps[0])
        << " gpuEnd=" << convert(timestamps[1]);
    if (previousGpuFrameSerial_ && previousGpuFrameSerial_ + 1 == context.submittedSerial)
        row << " priorGpuEnd=" << convert(previousGpuEnd_);
    for (size_t i = 0; i < Timeline::Count; ++i) row << ' ' << names[i] << '=' << timeline.times[i];
    mcvr::log::info("RenderFramework") << row.str() << std::endl;
#endif
    return VK_SUCCESS;
}

void Framework::storeGpuProfileResult(uint32_t sequence, uint64_t durationNs) {
    std::lock_guard<std::mutex> lock(gpuProfileMtx_);
    completedGpuProfiles_[sequence] = durationNs;
    while (completedGpuProfiles_.size() > 64) { completedGpuProfiles_.erase(completedGpuProfiles_.begin()); }
}

VkResult Framework::takeScreenshot(bool withUI, int width, int height, int channel, void *dstPointer) {
    if (!isRunning()) { return inactiveResult(); }
    if (indexHistory_.empty()) { return VK_NOT_READY; }

    uint32_t targetIndex = indexHistory_.front();
    auto context = contexts_[targetIndex];
    std::shared_ptr<vk::Fence> fence = context->commandFinishedFence;
    VkResult result = vkWaitForFences(device_->vkDevice(), 1, &fence->vkFence(), true, UINT64_MAX);
    if (result != VK_SUCCESS) {
        mcvr::log::info("RenderFramework")
            << "vkWaitForFences failed with error for screenshot: " << std::dec << result << std::endl;
        return recordFailure(result, "vkWaitForFences(screenshot source)");
    }

    std::shared_ptr<vk::HostVisibleBuffer> dstBuffer;
    std::shared_ptr<vk::DeviceLocalImage> srcImage;

    if (withUI) {
        auto pipelineContext = pipeline_->acquirePipelineContext(context);
        srcImage = pipelineContext->uiModuleContext->overlayDrawColorImage;

        uint32_t finalImageBufferSize =
            srcImage->width() * srcImage->height() * srcImage->layer() * vk::formatToByte(srcImage->vkFormat());
        if (srcImage->width() != width || srcImage->height() != height || channel != 4) {
            return VK_ERROR_FORMAT_NOT_SUPPORTED;
        }

        dstBuffer =
            vk::HostVisibleBuffer::create(vma_, device_, finalImageBufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
    } else {
        auto pipelineContext = pipeline_->acquirePipelineContext(context);
        srcImage = pipelineContext->worldPipelineContext->outputImage;

        uint32_t worldImageBufferSize =
            srcImage->width() * srcImage->height() * srcImage->layer() * vk::formatToByte(srcImage->vkFormat());
        if (srcImage->width() != width || srcImage->height() != height || channel != 4) {
            return VK_ERROR_FORMAT_NOT_SUPPORTED;
        }

        dstBuffer =
            vk::HostVisibleBuffer::create(vma_, device_, worldImageBufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
    }

    VkImageLayout initialLayout = srcImage->imageLayout();
    auto mainQueueIndex = physicalDevice_->mainQueueIndex();

    std::shared_ptr<vk::CommandBuffer> oneTimeBuffer = vk::CommandBuffer::create(device_, mainCommandPool_);
    oneTimeBuffer->begin();

    oneTimeBuffer->barriersBufferImage(
        {},
        {{
            .srcStageMask = VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR |
                            VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_2_TRANSFER_BIT,
            .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
            .dstStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
            .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
            .oldLayout = initialLayout,
            .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            .srcQueueFamilyIndex = mainQueueIndex,
            .dstQueueFamilyIndex = mainQueueIndex,
            .image = srcImage,
            .subresourceRange = vk::wholeColorSubresourceRange,
        }});
    VkBufferImageCopy bufferImageCopy{};
    bufferImageCopy.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    bufferImageCopy.imageSubresource.mipLevel = 0;
    bufferImageCopy.imageSubresource.baseArrayLayer = 0;
    bufferImageCopy.imageSubresource.layerCount = 1;
    bufferImageCopy.imageExtent.width = srcImage->width();
    bufferImageCopy.imageExtent.height = srcImage->height();
    bufferImageCopy.imageExtent.depth = 1;
    vkCmdCopyImageToBuffer(oneTimeBuffer->vkCommandBuffer(), srcImage->vkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                           dstBuffer->vkBuffer(), 1, &bufferImageCopy);
    oneTimeBuffer
        ->barriersBufferImage(
            {}, {{
                    .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                    .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                    .dstStageMask = VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT |
                                    VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR |
                                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                    .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                    .oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    .newLayout = initialLayout,
                    .srcQueueFamilyIndex = mainQueueIndex,
                    .dstQueueFamilyIndex = mainQueueIndex,
                    .image = srcImage,
                    .subresourceRange = vk::wholeColorSubresourceRange,
                }})
        ->end();

    VkSubmitInfo vkSubmitInfo = {};
    vkSubmitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    vkSubmitInfo.waitSemaphoreCount = 0;
    vkSubmitInfo.commandBufferCount = 1;
    vkSubmitInfo.pCommandBuffers = &oneTimeBuffer->vkCommandBuffer();
    vkSubmitInfo.signalSemaphoreCount = 0;
    std::shared_ptr<vk::Fence> oneTimeFence = vk::Fence::create(device_);

    result = vkQueueSubmit(device_->mainVkQueue(), 1, &vkSubmitInfo, oneTimeFence->vkFence());
    mcvr::diagnostics::device_loss::note("screenshot-queue-submit", result, 1, 0,
                                         (uint64_t)oneTimeBuffer->vkCommandBuffer(), (uint64_t)oneTimeFence->vkFence(),
                                         0, (uint64_t)device_->mainVkQueue());
    if (result != VK_SUCCESS) { return recordFailure(result, "vkQueueSubmit(screenshot)"); }
    result = vkWaitForFences(device_->vkDevice(), 1, &oneTimeFence->vkFence(), true, UINT64_MAX);
    if (result != VK_SUCCESS) {
        mcvr::log::info("RenderFramework")
            << "vkWaitForFences failed with error for screenshot: " << std::dec << result << std::endl;
        return recordFailure(result, "vkWaitForFences(screenshot copy)");
    }

    dstBuffer->downloadFromBuffer();
    if (srcImage->vkFormat() == VK_FORMAT_R16G16B16A16_SFLOAT) {
        // Minecraft screenshots remain SDR byte images, never evidence of HDR scanout.
        auto input = static_cast<const uint16_t *>(dstBuffer->mappedPtr());
        auto output = static_cast<uint8_t *>(dstPointer);
        for (size_t i = 0; i < size_t(width) * height * channel; ++i)
            output[i] = uint8_t(std::clamp(glm::unpackHalf1x16(input[i]), 0.f, 1.f) * 255.f + .5f);
    } else
        std::memcpy(dstPointer, dstBuffer->mappedPtr(), dstBuffer->size());
    return VK_SUCCESS;
}

std::recursive_mutex &Framework::recreateMtx() {
    return recreateMtx_;
}

std::shared_ptr<vk::Instance> Framework::instance() {
    return instance_;
}

std::shared_ptr<vk::Window> Framework::window() {
    return window_;
}

std::shared_ptr<vk::PhysicalDevice> Framework::physicalDevice() {
    return physicalDevice_;
}

std::shared_ptr<vk::Device> Framework::device() {
    return device_;
}

std::shared_ptr<vk::VMA> Framework::vma() {
    return vma_;
}

std::shared_ptr<vk::Swapchain> Framework::swapchain() {
    return swapchain_;
}

std::shared_ptr<vk::CommandPool> Framework::mainCommandPool() {
    return mainCommandPool_;
}

std::shared_ptr<vk::CommandPool> Framework::asyncCommandPool() {
    return asyncCommandPool_;
}

std::shared_ptr<vk::CommandBuffer> Framework::worldAsyncCommandBuffer() {
    return worldAsyncCommandBuffer_;
}

std::vector<std::shared_ptr<vk::Fence>> &Framework::commandFinishedFences() {
    return commandFinishedFences_;
}

uint32_t Framework::recordingContextCount() {
    if (auto scope = SceneRecordingScope::active()) return static_cast<uint32_t>(scope->contexts.size());
    return static_cast<uint32_t>(contexts_.size());
}

std::vector<std::shared_ptr<FrameworkContext>> &Framework::contexts() {
    if (auto scene = SceneRecordingScope::active()) return scene->contexts;
    return contexts_;
}

std::shared_ptr<FrameworkContext> Framework::recordingContext() const {
    if (auto *worker = static_cast<FrameworkContext *>(mcvr::FrameExecution::workerRecordingSlot()))
        return worker->shared_from_this();
    return producerContext_;
}

uint64_t Framework::frameSerial() const {
    if (const auto *worker = static_cast<FrameworkContext *>(mcvr::FrameExecution::workerRecordingSlot()))
        return worker->frameSerial;
    return frameSerial_;
}

std::shared_ptr<FrameworkContext> Framework::safeAcquireCurrentContext() {
    if (auto scene = SceneRecordingScope::active()) return scene->current;
    if (mcvr::FrameExecution::workerRecordingSlot()) return recordingContext();
    if (const auto *slot = worldInputSlot()) {
        mcvr::FrameExecution::checkAccess("recording context", slot);
        return producerContext_;
    }
    mcvr::FrameExecution::checkAccess("recording context");
    if (deferredInputAcquire == this && (!producerContext_ || !producerContext_->overlayCommandBuffer->isRecording())) {
        const auto result = acquireInputContext();
        if (result != VK_SUCCESS)
            mcvr::failure::raise(mcvr::failure::Kind::runtime, result, "Acquire frame for recording");
        deferInputAcquire();
    }
    std::unique_lock<std::recursive_mutex> lck(recreateMtx_);
    // for continous window operation, producerContext_ will always be reset, busy waiting
    while (producerContext_ == nullptr && isRunning()) {
        // ensure producerContext_ is not nullptr after seapchain recreation
        if (acquireContext() != VK_SUCCESS) { break; }
    }
    mcvr::FrameExecution::checkAccess("recording context", producerContext_.get());
    return producerContext_;
}

bool Framework::canReadProducerQueries() const {
    return deferredInputAcquire == this && !rebuildAfterRecording_ && !vk::Window::framebufferResized &&
           !Renderer::options.presentationChanged;
}

std::shared_ptr<FrameworkContext> Framework::acquireOverlayContext() {
    if (deferredInputAcquire != this) return safeAcquireCurrentContext();
    if (producerContext_ && producerContext_ != submittedContext_ &&
        producerContext_->overlayCommandBuffer->isRecording())
        return producerContext_;
    if (rebuildAfterRecording_ || vk::Window::framebufferResized || Renderer::options.presentationChanged) {
        mcvr::FrameExecution::Producer producer(true, "Prepare overlay after display change");
        return safeAcquireCurrentContext();
    }
    const auto result = acquireContext(true, true);
    if (result != VK_SUCCESS)
        mcvr::failure::raise(mcvr::failure::Kind::runtime, result, "Acquire overlay recording slot");
    return producerContext_;
}

std::shared_ptr<FrameworkContext> Framework::deferredTextureContext() {
    if (deferredInputAcquire != this) return nullptr;
    auto context = acquireOverlayContext();
    return producerInputsPending_ ? context : nullptr;
}

std::shared_ptr<Pipeline> Framework::pipeline() {
    return pipeline_;
}

FrameResourceRetainer &Framework::frameResourceRetainer() {
    return *frameResourceRetainer_;
}

std::shared_ptr<vk::Semaphore> Framework::acquireSemaphore() {
    std::lock_guard lock(acquiredSemaphoreMtx_);
    std::shared_ptr<vk::Semaphore> semaphore;
    if (recycledImageAcquiredSemaphores_.empty()) {
        semaphore = vk::Semaphore::create(device_);
    } else {
        semaphore = recycledImageAcquiredSemaphores_.front();
        recycledImageAcquiredSemaphores_.pop();
    }
    return semaphore;
}

void Framework::recycleSemaphore(std::shared_ptr<vk::Semaphore> semaphore) {
    std::lock_guard lock(acquiredSemaphoreMtx_);
    recycledImageAcquiredSemaphores_.push(semaphore);
}

FrameResourceRetainer::FrameResourceRetainer(std::shared_ptr<Framework> framework)
    : framesInFlight_(framework->mainFrameSlotCount()) {}

uint64_t FrameResourceRetainer::workerFrameSerial() {
    const auto *worker = static_cast<FrameworkContext *>(mcvr::FrameExecution::workerRecordingSlot());
    return worker ? worker->frameSerial : 0;
}

void FrameResourceRetainer::beginFrame(uint64_t frameSerial, uint64_t completedSerial) {
    decltype(retention_.clear()) released;
    {
        std::unique_lock<std::recursive_mutex> lck(mtx_);
        currentSerial_ = frameSerial;
        released = retention_.beginFrame(frameSerial, completedSerial, framesInFlight_);
    }
    // Destroyed outside the lock: releasing a resource may retain another.
}

void FrameResourceRetainer::clearAfterGpuIdle(uint64_t currentFrameSerial) {
    decltype(retention_.clear()) released;
    {
        std::unique_lock<std::recursive_mutex> lock(mtx_);
        released = retention_.clear();
        // GUI/upload commands may still be recorded in the active context after
        // unload. Do not put their resources into the default serial-0 group.
        retention_.beginFrame(currentFrameSerial, currentFrameSerial, framesInFlight_);
    }
}

std::string FrameResourceRetainer::census() {
    std::unique_lock<std::recursive_mutex> lck(mtx_);
    std::ostringstream out;
    out << " retainer=groups" << retention_.groups() << ",resources" << retention_.resources() << ",oldest"
        << retention_.oldestResources() << '@' << retention_.oldestAge();
    return out.str();
}

std::shared_ptr<vk::DeviceLocalImage>
Framework::encodeDisplay(FrameworkContext &frame, const std::shared_ptr<vk::DeviceLocalImage> &source, uint32_t role) {
    if (!displayOutput_) displayOutput_ = std::make_unique<DisplayOutput>();
    return displayOutput_->encode(*this, frame.fuseCommandBuffer, frame.frameIndex, role, source);
}
