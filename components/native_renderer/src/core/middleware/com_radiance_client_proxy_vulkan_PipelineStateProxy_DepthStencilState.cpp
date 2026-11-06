#include "core/diagnostics/state_bridge_audit.hpp"
#include "com_radiance_client_proxy_vulkan_PipelineStateProxy_DepthStencilState.h"
#include "core/middleware/jni_exception.hpp"

#include "core/render/modules/ui_module.hpp"
#include "core/render/pipeline.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"

extern "C" {
JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_setDepthTestEnable(JNIEnv *env,
                                                                                                   jclass,
                                                                                                   jboolean enable) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.setDepthTestEnable",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayDepthTestEnable(enable);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_setDepthWriteEnable(JNIEnv *env,
                                                                                                    jclass,
                                                                                                    jboolean enable) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.setDepthWriteEnable",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayDepthWriteEnable(enable);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_setStencilTestEnable(JNIEnv *env,
                                                                                                     jclass,
                                                                                                     jboolean enable) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.setStencilTestEnable",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilTestEnable(enable);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetDepthCompareOp(
    JNIEnv *env, jclass, jint depthCompareOp) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetDepthCompareOp",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayDepthCompareOp(depthCompareOp);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilFrontFunc(
    JNIEnv *env, jclass, jint compareOp, jint reference, jint compareMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilFrontFunc",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilFrontFunc(compareOp, reference, compareMask);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilBackFunc(
    JNIEnv *env, jclass, jint compareOp, jint reference, jint compareMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilBackFunc",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilBackFunc(compareOp, reference, compareMask);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilFrontOp(
    JNIEnv *env, jclass, jint failOp, jint depthFailOp, jint passOp) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilFrontOp",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilFrontOp(failOp, depthFailOp, passOp);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilBackOp(
    JNIEnv *env, jclass, jint failOp, jint depthFailOp, jint passOp) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilBackOp",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilBackOp(failOp, depthFailOp, passOp);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilFrontWriteMask(
    JNIEnv *env, jclass, jint writeMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilFrontWriteMask",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilFrontWriteMask(writeMask);
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilBackWriteMask(
    JNIEnv *env, jclass, jint writeMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilBackWriteMask",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->acquireOverlayContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilBackWriteMask(writeMask);
        },
        jni::BoundaryPolicy::producerOverlay);
}
JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilFuncBoth(
    JNIEnv *env, jclass, jint compareOp, jint reference, jint compareMask) {
    jni::invokeVoid(
        env, "Set both stencil Func",
        [&] {
            auto framework = Renderer::instance().framework();
            if (!framework) return;
            auto context = framework->acquireOverlayContext();
            auto ui = framework->pipeline()->acquirePipelineContext(context)->uiModuleContext;
            mcvr::state_audit::context();
            ui->setOverlayStencilFrontFunc(compareOp, reference, compareMask);
            jni::preflight(jni::BoundaryPolicy::normal);
            ui->setOverlayStencilBackFunc(compareOp, reference, compareMask);
        },
        jni::BoundaryPolicy::producerOverlay);
}
JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilOpBoth(
    JNIEnv *env, jclass, jint failOp, jint depthFailOp, jint passOp) {
    jni::invokeVoid(
        env, "Set both stencil Op",
        [&] {
            auto framework = Renderer::instance().framework();
            if (!framework) return;
            auto context = framework->acquireOverlayContext();
            auto ui = framework->pipeline()->acquirePipelineContext(context)->uiModuleContext;
            mcvr::state_audit::context();
            ui->setOverlayStencilFrontOp(failOp, depthFailOp, passOp);
            jni::preflight(jni::BoundaryPolicy::normal);
            ui->setOverlayStencilBackOp(failOp, depthFailOp, passOp);
        },
        jni::BoundaryPolicy::producerOverlay);
}
JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilWriteMaskBoth(
    JNIEnv *env, jclass, jint writeMask) {
    jni::invokeVoid(
        env, "Set both stencil WriteMask",
        [&] {
            auto framework = Renderer::instance().framework();
            if (!framework) return;
            auto context = framework->acquireOverlayContext();
            auto ui = framework->pipeline()->acquirePipelineContext(context)->uiModuleContext;
            mcvr::state_audit::context();
            ui->setOverlayStencilFrontWriteMask(writeMask);
            jni::preflight(jni::BoundaryPolicy::normal);
            ui->setOverlayStencilBackWriteMask(writeMask);
        },
        jni::BoundaryPolicy::producerOverlay);
}
JNIEXPORT jintArray JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_snapshotStencil(JNIEnv *env, jclass) {
    jintArray result = nullptr;
    jni::invokeVoid(
        env, "Read stencil shadow state",
        [&] {
            auto framework = Renderer::instance().framework();
            if (!framework) throw std::runtime_error("Stencil snapshot requires a Vulkan framework");
            const auto serial = [&] {
                mcvr::FrameExecution::Producer gate(true, "Read stencil reference");
                auto frame = framework->acquireOverlayContext();
                if (!frame) throw std::runtime_error("Stencil snapshot requires an acquired frame");
                mcvr::state_audit::context();
                return framework->pipeline()->acquirePipelineContext(frame)->uiModuleContext->stencilState();
            };
            std::array<jint, 14> values;
            if (framework->canReadProducerQueries()) {
                values = framework->pipeline()->uiModule()->producerStencil();
                if (mcvr::state_audit::verifyQueryMirrors())
                    mcvr::state_audit::verifyQuery("snapshotStencil", values, serial());
            } else {
                values = serial();
            }
            mcvr::state_audit::query();
            result = env->NewIntArray(values.size());
            if (result) env->SetIntArrayRegion(result, 0, values.size(), values.data());
        },
        jni::BoundaryPolicy::frameHandoff);
    return result;
}
}
