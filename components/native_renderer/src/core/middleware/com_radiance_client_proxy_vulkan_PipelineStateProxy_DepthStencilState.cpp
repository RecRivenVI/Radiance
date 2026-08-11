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
    jni::invokeVoid(env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.setDepthTestEnable",
                    [&] {
                        auto framework = Renderer::instance().framework();
                        if (framework == nullptr) return;
                        auto context = framework->safeAcquireCurrentContext();
                        auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
                        mcvr::state_audit::context();
                        pipelineContext->uiModuleContext->setOverlayDepthTestEnable(enable);
                    });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_setDepthWriteEnable(JNIEnv *env,
                                                                                                    jclass,
                                                                                                    jboolean enable) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.setDepthWriteEnable", [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->safeAcquireCurrentContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayDepthWriteEnable(enable);
        });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_setStencilTestEnable(JNIEnv *env,
                                                                                                     jclass,
                                                                                                     jboolean enable) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.setStencilTestEnable", [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->safeAcquireCurrentContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilTestEnable(enable);
        });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetDepthCompareOp(
    JNIEnv *env, jclass, jint depthCompareOp) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetDepthCompareOp", [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->safeAcquireCurrentContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayDepthCompareOp(depthCompareOp);
        });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilFrontFunc(
    JNIEnv *env, jclass, jint compareOp, jint reference, jint compareMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilFrontFunc", [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->safeAcquireCurrentContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilFrontFunc(compareOp, reference, compareMask);
        });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilBackFunc(
    JNIEnv *env, jclass, jint compareOp, jint reference, jint compareMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilBackFunc", [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->safeAcquireCurrentContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilBackFunc(compareOp, reference, compareMask);
        });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilFrontOp(
    JNIEnv *env, jclass, jint failOp, jint depthFailOp, jint passOp) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilFrontOp", [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->safeAcquireCurrentContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilFrontOp(failOp, depthFailOp, passOp);
        });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilBackOp(
    JNIEnv *env, jclass, jint failOp, jint depthFailOp, jint passOp) {
    jni::invokeVoid(env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilBackOp",
                    [&] {
                        auto framework = Renderer::instance().framework();
                        if (framework == nullptr) return;
                        auto context = framework->safeAcquireCurrentContext();
                        auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
                        mcvr::state_audit::context();
                        pipelineContext->uiModuleContext->setOverlayStencilBackOp(failOp, depthFailOp, passOp);
                    });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilFrontWriteMask(
    JNIEnv *env, jclass, jint writeMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilFrontWriteMask",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->safeAcquireCurrentContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilFrontWriteMask(writeMask);
        });
}

JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilBackWriteMask(
    JNIEnv *env, jclass, jint writeMask) {
    jni::invokeVoid(
        env, "JNI com.radiance.client.proxy.vulkan.PipelineStateProxy$DepthStencilState.vkSetStencilBackWriteMask",
        [&] {
            auto framework = Renderer::instance().framework();
            if (framework == nullptr) return;
            auto context = framework->safeAcquireCurrentContext();
            auto pipelineContext = framework->pipeline()->acquirePipelineContext(context);
            mcvr::state_audit::context();
            pipelineContext->uiModuleContext->setOverlayStencilBackWriteMask(writeMask);
        });
}
JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilFuncBoth(
    JNIEnv *env, jclass, jint compareOp, jint reference, jint compareMask) {
    jni::invokeVoid(env, "Set both stencil Func", [&] {
        auto framework = Renderer::instance().framework();
        if (!framework) return;
        auto context = framework->safeAcquireCurrentContext();
        auto ui = framework->pipeline()->acquirePipelineContext(context)->uiModuleContext;
        mcvr::state_audit::context();
        ui->setOverlayStencilFrontFunc(compareOp, reference, compareMask);
        jni::preflight(jni::BoundaryPolicy::normal);
        ui->setOverlayStencilBackFunc(compareOp, reference, compareMask);
    });
}
JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilOpBoth(
    JNIEnv *env, jclass, jint failOp, jint depthFailOp, jint passOp) {
    jni::invokeVoid(env, "Set both stencil Op", [&] {
        auto framework = Renderer::instance().framework();
        if (!framework) return;
        auto context = framework->safeAcquireCurrentContext();
        auto ui = framework->pipeline()->acquirePipelineContext(context)->uiModuleContext;
        mcvr::state_audit::context();
        ui->setOverlayStencilFrontOp(failOp, depthFailOp, passOp);
        jni::preflight(jni::BoundaryPolicy::normal);
        ui->setOverlayStencilBackOp(failOp, depthFailOp, passOp);
    });
}
JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_vkSetStencilWriteMaskBoth(
    JNIEnv *env, jclass, jint writeMask) {
    jni::invokeVoid(env, "Set both stencil WriteMask", [&] {
        auto framework = Renderer::instance().framework();
        if (!framework) return;
        auto context = framework->safeAcquireCurrentContext();
        auto ui = framework->pipeline()->acquirePipelineContext(context)->uiModuleContext;
        mcvr::state_audit::context();
        ui->setOverlayStencilFrontWriteMask(writeMask);
        jni::preflight(jni::BoundaryPolicy::normal);
        ui->setOverlayStencilBackWriteMask(writeMask);
    });
}
JNIEXPORT jintArray JNICALL
Java_com_radiance_client_proxy_vulkan_PipelineStateProxy_00024DepthStencilState_snapshotStencil(JNIEnv *env, jclass) {
    jintArray result = nullptr;
    jni::invokeVoid(env, "Read stencil shadow state", [&] {
        auto framework = Renderer::instance().framework();
        auto context = framework ? framework->safeAcquireCurrentContext() : nullptr;
        if (!context) throw std::runtime_error("Stencil snapshot requires an acquired frame");
        auto ui = framework->pipeline()->acquirePipelineContext(context)->uiModuleContext;
        mcvr::state_audit::context();
        mcvr::state_audit::query();
        jint values[14];
        for (int i = 0; i < 2; ++i) {
            int base = i * 7;
            values[base] = ui->overlayCompareOp[i];
            values[base + 1] = ui->overlayReference[i];
            values[base + 2] = ui->overlayCompareMask[i];
            values[base + 3] = ui->overlayFailOp[i];
            values[base + 4] = ui->overlayDepthFailOp[i];
            values[base + 5] = ui->overlayPassOp[i];
            values[base + 6] = ui->overlayWriteMask[i];
        }
        result = env->NewIntArray(14);
        if (result) env->SetIntArrayRegion(result, 0, 14, values);
    });
    return result;
}
}
