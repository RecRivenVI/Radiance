#include "core/diagnostics/state_bridge_audit.hpp"
#include "com_radiance_client_proxy_vulkan_PipelineStateProxy_RasterizationState.h"
#include "core/middleware/jni_exception.hpp"

#include "core/render/modules/ui_module.hpp"
#include "core/render/pipeline.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"

extern "C" {
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024RasterizationState_setLineWidth(
    JNIEnv *env, jclass, jfloat lineWidth) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$RasterizationState.setLineWidth",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayLineWidth(lineWidth);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024RasterizationState_vkSetPolygonMode(JNIEnv *env,
                                                                                                  jclass,
                                                                                                  jint polygonMode) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$RasterizationState.vkSetPolygonMode",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayPolygonMode(polygonMode);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024RasterizationState_vkSetCullMode(
    JNIEnv *env, jclass, jint cullMode) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$RasterizationState.vkSetCullMode",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayCullMode(cullMode);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024RasterizationState_vkSetFrontFace(
    JNIEnv *env, jclass, jint frontFace) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$RasterizationState.vkSetFrontFace",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayFrontFace(frontFace);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024RasterizationState_vkSetDepthBiasEnable(JNIEnv *env,
                                                                                                      jclass,
                                                                                                      jint polygonMode,
                                                                                                      jboolean enable) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$RasterizationState.vkSetDepthBiasEnable",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayDepthBiasEnable(polygonMode, enable);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024RasterizationState_vkSetDepthBias(
    JNIEnv *env, jclass, jfloat depthBiasSlopeFactor, jfloat depthBiasConstantFactor) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$RasterizationState.vkSetDepthBias",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayDepthBias(depthBiasSlopeFactor, depthBiasConstantFactor);
        },
        jni::BoundaryPolicy::producerOverlay);
}
}
