#include "core/diagnostics/state_bridge_audit.hpp"
#include "com_radiance_client_proxy_vulkan_PipelineStateProxy_ViewportState.h"

#include "core/render/modules/ui_module.hpp"
#include "core/render/pipeline.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"
#include "core/middleware/jni_exception.hpp"

extern "C" {
JNIEXPORT jintArray JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024ViewportState_getViewport(JNIEnv *env, jclass) {
    jintArray result = nullptr;
    jni::invokeVoid(
        env, "Read viewport",
        [&] {
            auto framework = Renderer::instance().framework();
            if (!framework) throw std::runtime_error("Viewport requires a Vulkan framework");
            const auto serial = [&] {
                mcvr::FrameExecution::Producer gate(true, "Read viewport reference");
                auto frame = framework->acquireOverlayContext();
                if (!frame) throw std::runtime_error("Viewport requires an acquired frame");
                mcvr::state_audit::context();
                return framework->pipeline()->acquirePipelineContext(frame)->uiModuleContext->overlayViewportGl;
            };
            std::array<jint, 4> viewport;
            if (framework->canReadProducerQueries()) {
                viewport = framework->pipeline()->uiModule()->producerViewport();
                if (mcvr::state_audit::verifyQueryMirrors())
                    mcvr::state_audit::verifyQuery("getViewport", viewport, serial());
            } else {
                viewport = serial();
            }
            mcvr::state_audit::query();
            result = env->NewIntArray(viewport.size());
            if (result) env->SetIntArrayRegion(result, 0, viewport.size(), viewport.data());
        },
        jni::BoundaryPolicy::frameHandoff);
    return result;
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024ViewportState_setScissorEnabled(
    JNIEnv *env, jclass, jboolean enabled) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$ViewportState.setScissorEnabled",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayScissorEnabled(enabled);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024ViewportState_setScissor(
    JNIEnv *env, jclass, jint x, jint y, jint width, jint height) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$ViewportState.setScissor",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayScissor(x, y, width, height);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024ViewportState_setViewport(
    JNIEnv *env, jclass, jint x, jint y, jint width, jint height) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$ViewportState.setViewport",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayViewport(x, y, width, height);
        },
        jni::BoundaryPolicy::producerOverlay);
}
}
