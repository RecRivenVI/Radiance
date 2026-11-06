#include "com_radiance_client_proxy_vulkan_DrawCommandProxy_Overlay.h"
#include "core/middleware/jni_exception.hpp"

#include "core/render/modules/ui_module.hpp"
#include "core/render/pipeline.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"

namespace {
std::shared_ptr<UIModuleContext>
clearTarget(Framework &framework, std::optional<mcvr::FrameExecution::Producer> &gate, int aspectMask) {
    if (!mcvr::FrameExecution::producerOverlayDeferred())
        return framework.pipeline()->acquirePipelineContext(framework.safeAcquireCurrentContext())->uiModuleContext;
    auto requireSharedInputs = [&] {
        gate.emplace(true, "Clear shared framebuffer attachment");
        mcvr::FrameExecution::prepareDeferredProducer();
    };
    if (!Renderer::instance().defaultDrawFramebufferBound()) requireSharedInputs();
    const auto context = gate ? framework.safeAcquireCurrentContext() : framework.acquireOverlayContext();
    auto ui = framework.pipeline()->acquirePipelineContext(context)->uiModuleContext;
    constexpr auto allColor =
        VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    if (!gate && ((aspectMask == VK_IMAGE_ASPECT_COLOR_BIT && ui->overlayColorWriteMask != allColor) ||
                  ((aspectMask & VK_IMAGE_ASPECT_STENCIL_BIT) && (ui->overlayWriteMask[0] & 0xffu) != 0xffu)))
        requireSharedInputs();
    return ui;
}
} // namespace

extern "C" {
JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_DrawCommandProxy_00024Overlay_vkCmdClearEntireColorAttachment(JNIEnv *env,
                                                                                                    jclass) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.DrawCommandProxy$Overlay.vkCmdClearEntireColorAttachment",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            std::optional<mcvr::FrameExecution::Producer> gate;
            clearTarget(*framework, gate, VK_IMAGE_ASPECT_COLOR_BIT)->clearOverlayEntireColorAttachment();
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_DrawCommandProxy_00024Overlay_vkCmdClearEntireDepthStencilAttachment(
    JNIEnv *env, jclass, jint aspectMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.DrawCommandProxy$Overlay.vkCmdClearEntireDepthStencilAttachment",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            std::optional<mcvr::FrameExecution::Producer> gate;
            clearTarget(*framework, gate, aspectMask)->clearOverlayEntireDepthStencilAttachment(aspectMask);
        },
        jni::BoundaryPolicy::producerOverlay);
}
}
