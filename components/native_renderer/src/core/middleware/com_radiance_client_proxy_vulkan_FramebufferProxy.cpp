#include "core/diagnostics/state_bridge_audit.hpp"
#include "com_radiance_client_proxy_vulkan_FramebufferProxy.h"

#include "core/middleware/jni_exception.hpp"
#include "core/render/framebuffers.hpp"
#include "core/render/renderer.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/pipeline.hpp"
#include "core/render/modules/ui_module.hpp"
#include "core/render/textures.hpp"

#include <stdexcept>
#include <vector>

namespace {
std::shared_ptr<Framebuffers> resources(const FrameworkContext *slot = nullptr) {
    auto result = Renderer::instance().framebuffers(slot);
    if (!result) throw std::runtime_error("Vulkan framebuffer resources are not initialized");
    return result;
}

std::shared_ptr<UIModuleContext> drawContext(bool required = true) {
    auto framework = Renderer::instance().framework();
    auto frame = framework ? framework->safeAcquireCurrentContext() : nullptr;
    if (!frame) {
        if (required) throw std::runtime_error("Framebuffer drawing requires an acquired frame");
        return nullptr;
    }
    mcvr::state_audit::context();
    return framework->pipeline()->acquirePipelineContext(frame)->uiModuleContext;
}

void endPass() {
    if (auto context = drawContext(false)) context->end();
}

uint32_t queryFramebufferBinding(uint32_t target, uint64_t bindings) {
    return target == mcvr::framebuffer::READ_FRAMEBUFFER ? uint32_t(bindings) : uint32_t(bindings >> 32);
}

std::optional<VkExtent2D> producerDefaultFramebufferExtent(uint32_t target) {
    auto &renderer = Renderer::instance();
    const auto framework = renderer.framework();
    if (!framework || !framework->canReadProducerQueries() ||
        queryFramebufferBinding(target, renderer.producerFramebufferBindings()) != 0)
        return std::nullopt;
    return framework->pipeline()->uiModule()->defaultFramebufferExtent();
}

void bindFramebufferState(const std::shared_ptr<Framebuffers> &registry,
                          const std::shared_ptr<UIModuleContext> &context,
                          jint target,
                          jint id) {
    if (mcvr::state_audit::enabled()) {
        bool changed =
            target == mcvr::framebuffer::READ_FRAMEBUFFER ? registry->readFramebufferBinding() != uint32_t(id) :
            target == mcvr::framebuffer::DRAW_FRAMEBUFFER ? registry->drawFramebufferBinding() != uint32_t(id) :
                                                            registry->readFramebufferBinding() != uint32_t(id) ||
                                                                registry->drawFramebufferBinding() != uint32_t(id);
        mcvr::state_audit::bind(changed, context && context->overlayMode != NONE);
    }
    if (context) context->end();
    registry->bindFramebuffer(target, id);
}

VkFormat storageFormat(jint format) {
    switch (format) {
        case 0x1908:
        case 0x8058: return VK_FORMAT_R8G8B8A8_UNORM; // RGBA / RGBA8
        case 0x8C43: return VK_FORMAT_R8G8B8A8_SRGB;
        case 0x8051:
        case 0x1907: return VK_FORMAT_R8G8B8A8_UNORM; // RGB8 uses renderable expanded storage
        case 0x8F96: return VK_FORMAT_R8G8B8A8_SNORM; // RGB8_SNORM, expanded attachment storage
        case 0x8229:
        case 0x1903: return VK_FORMAT_R8_UNORM;
        case 0x822B:
        case 0x8227: return VK_FORMAT_R8G8_UNORM;
        case 0x822D: return VK_FORMAT_R16_SFLOAT;
        case 0x822F: return VK_FORMAT_R16G16_SFLOAT;
        case 0x881A:
        case 0x881B: return VK_FORMAT_R16G16B16A16_SFLOAT; // RGBA16F / RGB16F
        case 0x822E: return VK_FORMAT_R32_SFLOAT;
        case 0x8230: return VK_FORMAT_R32G32_SFLOAT;
        case 0x8814: return VK_FORMAT_R32G32B32A32_SFLOAT;
        case 0x81A5: return VK_FORMAT_D16_UNORM;
        case 0x81A6: return VK_FORMAT_X8_D24_UNORM_PACK32;
        case 0x1902:
        case 0x8CAC: return VK_FORMAT_D32_SFLOAT;
        case 0x88F0: return VK_FORMAT_D24_UNORM_S8_UINT;
        case 0x8CAD: return VK_FORMAT_D32_SFLOAT_S8_UINT;
        default: throw std::invalid_argument("Unsupported framebuffer storage format: " + std::to_string(format));
    }
}
} // namespace

extern "C" {
JNIEXPORT jintArray JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_dimensions(JNIEnv *env,
                                                                                              jclass,
                                                                                              jint target) {
    jintArray result = nullptr;
    jni::invokeVoid(
        env, "Query framebuffer dimensions",
        [&] {
            if (target != mcvr::framebuffer::FRAMEBUFFER && target != mcvr::framebuffer::READ_FRAMEBUFFER &&
                target != mcvr::framebuffer::DRAW_FRAMEBUFFER)
                throw std::invalid_argument("Invalid framebuffer dimension target");
            const auto serial = [&] {
                mcvr::FrameExecution::Producer gate(true, "Resolve framebuffer dimensions");
                mcvr::FrameExecution::prepareDeferredProducer();
                const auto snapshot = drawContext()->framebufferSnapshot(target);
                if (snapshot.status != mcvr::framebuffer::FRAMEBUFFER_COMPLETE)
                    throw std::runtime_error("Framebuffer dimensions require complete attachments");
                return std::array{jint(snapshot.extent.width), jint(snapshot.extent.height)};
            };
            std::array<jint, 2> values;
            if (auto extent = producerDefaultFramebufferExtent(target)) {
                values = {jint(extent->width), jint(extent->height)};
                if (mcvr::state_audit::verifyQueryMirrors()) {
                    mcvr::FrameExecution::Producer gate(true, "Verify default framebuffer dimensions");
                    auto framework = Renderer::instance().framework();
                    const auto frame = framework->acquireOverlayContext();
                    const auto snapshot =
                        framework->pipeline()->acquirePipelineContext(frame)->uiModuleContext->framebufferSnapshot(
                            target);
                    mcvr::state_audit::verifyQuery(
                        "defaultFramebufferDimensions", values,
                        std::array{jint(snapshot.extent.width), jint(snapshot.extent.height)});
                }
            } else {
                values = serial();
            }
            result = env->NewIntArray(2);
            if (result) env->SetIntArrayRegion(result, 0, 2, values.data());
        },
        jni::BoundaryPolicy::frameHandoff);
    return result;
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_readPixels(
    JNIEnv *env, jclass, jint x, jint y, jint width, jint height, jint format, jint type, jlong destination) {
    jni::invokeVoid(env, "Read framebuffer pixels", [&] {
        auto framework = Renderer::instance().framework();
        if (!framework) throw std::runtime_error("Framebuffer readback requires a Vulkan framework");
        auto result = framework->readPixels(x, y, width, height, format, type, reinterpret_cast<void *>(destination));
        if (result != VK_SUCCESS)
            throw std::runtime_error("Framebuffer readback failed: VkResult=" + std::to_string(result));
    });
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_clear(JNIEnv *env,
                                                                                    jclass,
                                                                                    jfloat red,
                                                                                    jfloat green,
                                                                                    jfloat blue,
                                                                                    jfloat alpha,
                                                                                    jfloat depth,
                                                                                    jint stencil,
                                                                                    jint mask,
                                                                                    jintArray drawBuffers) {
    jni::invokeVoid(env, "Clear framebuffer attachments", [&] {
        if ((mask & ~(0x4000 | 0x0100 | 0x0400)) != 0) throw std::invalid_argument("Invalid clear mask");
        auto context = drawContext();
        auto registry = resources();
        const auto previousColors = context->overlayClearColors;
        const auto previousDepth = context->overlayClearDepth;
        const auto previousStencil = context->overlayClearStencil;
        const auto snapshot = context->framebufferSnapshot(mcvr::framebuffer::DRAW_FRAMEBUFFER);
        bool changedBuffers = false;
        bool clearColor = (mask & 0x4000) != 0;
        if (drawBuffers != nullptr) {
            const auto count = env->GetArrayLength(drawBuffers);
            std::vector<jint> values(count);
            if (count != 0) env->GetIntArrayRegion(drawBuffers, 0, count, values.data());
            if (env->ExceptionCheck()) return;
            clearColor = clearColor && !values.empty();
            if (registry->drawFramebufferBinding() != 0) {
                context->end();
                std::vector<uint32_t> selection(values.begin(), values.end());
                registry->setDrawBuffers(mcvr::framebuffer::DRAW_FRAMEBUFFER, selection);
                changedBuffers = true;
            } else if (values.size() > 1 ||
                       (!values.empty() && values[0] != 0 && values[0] != 0x0405 && values[0] != 0x8CE0)) {
                throw std::invalid_argument("Invalid default framebuffer clear attachment");
            } else if (!values.empty() && values[0] == 0)
                clearColor = false;
        }
        auto restore = [&] {
            context->overlayClearColors = previousColors;
            context->overlayClearDepth = previousDepth;
            context->overlayClearStencil = previousStencil;
            if (changedBuffers) {
                context->end();
                registry->setDrawBuffers(mcvr::framebuffer::DRAW_FRAMEBUFFER, snapshot.drawBuffers);
            }
        };
        try {
            context->overlayClearColors = {red, green, blue, alpha};
            context->overlayClearDepth = depth;
            context->overlayClearStencil = stencil;
            context->clearMaskedFramebuffer(clearColor, (mask & 0x0100) != 0, (mask & 0x0400) != 0);
        } catch (...) {
            restore();
            throw;
        }
        restore();
    });
}
JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_createFramebuffer(JNIEnv *env, jclass) {
    jint id = 0;
    jni::invokeVoid(env, "Create framebuffer", [&] { id = resources()->allocateFramebuffer(); });
    return id;
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_deleteFramebuffer(JNIEnv *env,
                                                                                                jclass,
                                                                                                jint id) {
    jni::invokeVoid(env, "Delete framebuffer", [&] {
        endPass();
        resources()->deleteFramebuffer(id);
    });
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_bindFramebuffer(JNIEnv *env,
                                                                                              jclass,
                                                                                              jint target,
                                                                                              jint id) {
    jni::invokeVoid(
        env, "Bind framebuffer",
        [&] {
            const auto framework = Renderer::instance().framework();
            const auto frame = framework ? framework->acquireOverlayContext() : nullptr;
            auto registry = resources(frame.get());
            auto context = frame ? framework->pipeline()->acquirePipelineContext(frame)->uiModuleContext : nullptr;
            if (context) mcvr::state_audit::context();
            bindFramebufferState(registry, context, target, id);
        },
        jni::BoundaryPolicy::producerOverlay);
}
JNIEXPORT jintArray JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_captureMaterialRouting(JNIEnv *env,
                                                                                                          jclass) {
    jintArray result = nullptr;
    jni::invokeVoid(
        env, "Capture material routing",
        [&] {
            auto &renderer = Renderer::instance();
            auto framework = renderer.framework();
            const auto reference = [&] {
                mcvr::FrameExecution::Producer gate(true, "Verify material routing query");
                auto registry = resources();
                const auto &viewport = drawContext()->overlayViewportGl;
                return std::array<jint, 6>{jint(registry->readFramebufferBinding()),
                                           jint(registry->drawFramebufferBinding()),
                                           viewport[0],
                                           viewport[1],
                                           viewport[2],
                                           viewport[3]};
            };
            std::array<jint, 6> values;
            if (framework && framework->canReadProducerQueries()) {
                const auto bindings = renderer.producerFramebufferBindings();
                const auto viewport = framework->pipeline()->uiModule()->producerViewport();
                values = {
                    jint(uint32_t(bindings)), jint(bindings >> 32), viewport[0], viewport[1], viewport[2], viewport[3]};
                if (mcvr::state_audit::verifyQueryMirrors())
                    mcvr::state_audit::verifyQuery("captureMaterialRouting", values, reference());
            } else {
                mcvr::FrameExecution::Producer gate(true, "Capture material routing");
                mcvr::FrameExecution::prepareDeferredProducer();
                values = reference();
            }
            mcvr::state_audit::query();
            result = env->NewIntArray(values.size());
            if (result) env->SetIntArrayRegion(result, 0, values.size(), values.data());
        },
        jni::BoundaryPolicy::frameHandoff);
    return result;
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_bindFramebufferPair(JNIEnv *env,
                                                                                                  jclass,
                                                                                                  jint read,
                                                                                                  jint draw) {
    jni::invokeVoid(env, "Restore material framebuffer bindings", [&] {
        auto registry = resources();
        auto context = drawContext(false);
        bindFramebufferState(registry, context, mcvr::framebuffer::READ_FRAMEBUFFER, read);
        jni::preflight(jni::BoundaryPolicy::normal);
        bindFramebufferState(registry, context, mcvr::framebuffer::DRAW_FRAMEBUFFER, draw);
    });
}
JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_boundFramebuffer(JNIEnv *env,
                                                                                               jclass,
                                                                                               jint target) {
    jint id = 0;
    jni::invokeVoid(
        env, "Query framebuffer binding",
        [&] {
            mcvr::state_audit::query();
            if (target != mcvr::framebuffer::READ_FRAMEBUFFER && target != mcvr::framebuffer::DRAW_FRAMEBUFFER &&
                target != mcvr::framebuffer::FRAMEBUFFER)
                throw std::invalid_argument("Invalid framebuffer target");
            const bool read = target == mcvr::framebuffer::READ_FRAMEBUFFER;
            auto &renderer = Renderer::instance();
            const auto serial = [&] {
                mcvr::FrameExecution::Producer gate(true, "Verify framebuffer binding query");
                auto registry = resources();
                return read ? registry->readFramebufferBinding() : registry->drawFramebufferBinding();
            };
            if (mcvr::FrameExecution::enabled()) {
                const auto bindings = renderer.producerFramebufferBindings();
                id = read ? uint32_t(bindings) : uint32_t(bindings >> 32);
                if (mcvr::state_audit::verifyQueryMirrors())
                    mcvr::state_audit::verifyQuery("boundFramebuffer", uint32_t(id), serial());
            } else {
                id = serial();
            }
        },
        jni::BoundaryPolicy::frameHandoff);
    return id;
}
JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_checkStatus(JNIEnv *env,
                                                                                          jclass,
                                                                                          jint target) {
    jint status = mcvr::framebuffer::FRAMEBUFFER_UNDEFINED;
    jni::invokeVoid(
        env, "Check framebuffer attachments",
        [&] {
            if (target != mcvr::framebuffer::READ_FRAMEBUFFER && target != mcvr::framebuffer::DRAW_FRAMEBUFFER &&
                target != mcvr::framebuffer::FRAMEBUFFER)
                throw std::invalid_argument("Invalid framebuffer target");
            const auto serial = [&]() -> jint {
                mcvr::FrameExecution::Producer gate(true, "Resolve framebuffer attachments");
                mcvr::FrameExecution::prepareDeferredProducer();
                auto registry = resources();
                auto binding = target == mcvr::framebuffer::READ_FRAMEBUFFER ? registry->readFramebufferBinding() :
                                                                               registry->drawFramebufferBinding();
                if (binding != 0) return registry->checkStatus(target);
                if (auto context = drawContext(false)) return context->framebufferSnapshot(target).status;
                return mcvr::framebuffer::FRAMEBUFFER_UNDEFINED;
            };
            if (producerDefaultFramebufferExtent(target)) {
                status = mcvr::framebuffer::FRAMEBUFFER_COMPLETE;
                if (mcvr::state_audit::verifyQueryMirrors()) {
                    mcvr::FrameExecution::Producer gate(true, "Verify default framebuffer status");
                    auto framework = Renderer::instance().framework();
                    const auto frame = framework->acquireOverlayContext();
                    const auto snapshot =
                        framework->pipeline()->acquirePipelineContext(frame)->uiModuleContext->framebufferSnapshot(
                            target);
                    mcvr::state_audit::verifyQuery("defaultFramebufferStatus", status, jint(snapshot.status));
                }
            } else {
                status = serial();
            }
        },
        jni::BoundaryPolicy::frameHandoff);
    return status;
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_attachTexture(
    JNIEnv *env, jclass, jint target, jint attachment, jint texture, jint level) {
    jni::invokeVoid(env, "Attach framebuffer texture", [&] {
        endPass();
        resources()->framebufferTexture(target, attachment, texture, level);
    });
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_attachRenderbuffer(
    JNIEnv *env, jclass, jint target, jint attachment, jint buffer) {
    jni::invokeVoid(env, "Attach framebuffer renderbuffer", [&] {
        endPass();
        resources()->framebufferRenderbuffer(target, attachment, buffer);
    });
}
JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_createRenderbuffer(JNIEnv *env, jclass) {
    jint id = 0;
    jni::invokeVoid(env, "Create renderbuffer", [&] { id = resources()->allocateRenderbuffer(); });
    return id;
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_deleteRenderbuffer(JNIEnv *env,
                                                                                                 jclass,
                                                                                                 jint id) {
    jni::invokeVoid(env, "Delete renderbuffer", [&] {
        endPass();
        resources()->deleteRenderbuffer(id);
    });
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_bindRenderbuffer(JNIEnv *env,
                                                                                               jclass,
                                                                                               jint id) {
    jni::invokeVoid(env, "Bind renderbuffer", [&] { resources()->bindRenderbuffer(id); });
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_renderbufferStorage(
    JNIEnv *env, jclass, jint format, jint width, jint height, jint samples) {
    jni::invokeVoid(env, "Allocate renderbuffer storage", [&] {
        if (width <= 0 || height <= 0 || samples <= 0)
            throw std::invalid_argument("Invalid renderbuffer dimensions or sample count");
        endPass();
        auto registry = resources();
        auto result = registry->renderbufferStorage(registry->renderbufferBinding(), storageFormat(format), width,
                                                    height, static_cast<VkSampleCountFlagBits>(samples));
        if (result != Framebuffers::StorageResult::Success)
            throw std::runtime_error("Unsupported renderbuffer storage contract: " +
                                     std::to_string(static_cast<int>(result)));
    });
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_prepareAttachmentTexture(
    JNIEnv *env, jclass, jint texture, jint levels, jint width, jint height, jint format) {
    jni::invokeVoid(env, "Allocate framebuffer texture storage", [&] {
        if (texture <= 0 || levels <= 0 || width <= 0 || height <= 0)
            throw std::invalid_argument("Invalid framebuffer texture storage dimensions");
        auto textures = Renderer::instance().textures();
        if (!textures) throw std::runtime_error("Vulkan textures are not initialized");
        endPass();
        auto vkFormat = storageFormat(format);
        textures->initializeAttachmentTexture(texture, levels, width, height, vkFormat,
                                              mcvr::framebuffer::formatAspects(vkFormat));
    });
}
JNIEXPORT jboolean JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_configureMainTargetAliases(
    JNIEnv *env, jclass, jint colorTexture, jint depthTexture, jint width, jint height) {
    jboolean stencilAvailable = JNI_FALSE;
    jni::invokeVoid(env, "Configure main target frame aliases", [&] {
        if (colorTexture <= 0 || depthTexture <= 0 || width <= 0 || height <= 0) {
            throw std::invalid_argument("Invalid main target aliases or dimensions");
        }
        auto rendererTextures = Renderer::instance().textures();
        auto framework = Renderer::instance().framework();
        auto module = framework && framework->pipeline() ? framework->pipeline()->uiModule() : nullptr;
        if (!rendererTextures || !module) throw std::runtime_error("Main target resources are not initialized");
        rendererTextures->registerFrameAlias(static_cast<uint32_t>(colorTexture), Textures::FrameAliasKind::MainColor);
        rendererTextures->registerFrameAlias(static_cast<uint32_t>(depthTexture), Textures::FrameAliasKind::MainDepth);
        stencilAvailable = module->defaultStencilAvailable() ? JNI_TRUE : JNI_FALSE;
    });
    return stencilAvailable;
}
JNIEXPORT jboolean JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_defaultStencilAvailable(JNIEnv *env,
                                                                                                          jclass) {
    return jni::invoke<jboolean>(
        env, "Query main target stencil support", JNI_FALSE,
        [&] {
            auto framework = Renderer::instance().framework();
            const auto read = [&] {
                auto module = framework && framework->pipeline() ? framework->pipeline()->uiModule() : nullptr;
                if (!module) throw std::runtime_error("Main target resources are not initialized");
                return module->defaultStencilAvailable();
            };
            const auto serial = [&] {
                mcvr::FrameExecution::Producer gate(true, "Verify main target stencil support");
                mcvr::FrameExecution::prepareDeferredProducer();
                return read();
            };
            if (!framework || !framework->canReadProducerQueries()) return serial();
            const bool available = read();
            if (mcvr::state_audit::verifyQueryMirrors())
                mcvr::state_audit::verifyQuery("defaultStencilAvailable", available, serial());
            return available;
        },
        jni::BoundaryPolicy::frameHandoff);
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_drawBuffers(JNIEnv *env,
                                                                                          jclass,
                                                                                          jintArray attachments) {
    jni::invokeVoid(env, "Select framebuffer draw buffers", [&] {
        if (!attachments) throw std::invalid_argument("Null draw-buffer list");
        std::vector<jint> values(env->GetArrayLength(attachments));
        env->GetIntArrayRegion(attachments, 0, values.size(), values.data());
        if (env->ExceptionCheck()) return;
        std::vector<uint32_t> buffers(values.begin(), values.end());
        endPass();
        resources()->setDrawBuffers(mcvr::framebuffer::DRAW_FRAMEBUFFER, buffers);
    });
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_readBuffer(JNIEnv *env,
                                                                                         jclass,
                                                                                         jint attachment) {
    jni::invokeVoid(env, "Select framebuffer read buffer",
                    [&] { resources()->setReadBuffer(mcvr::framebuffer::READ_FRAMEBUFFER, attachment); });
}
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_FramebufferProxy_blit(JNIEnv *env,
                                                                                   jclass,
                                                                                   jint sx0,
                                                                                   jint sy0,
                                                                                   jint sx1,
                                                                                   jint sy1,
                                                                                   jint dx0,
                                                                                   jint dy0,
                                                                                   jint dx1,
                                                                                   jint dy1,
                                                                                   jint mask,
                                                                                   jint filter) {
    jni::invokeVoid(env, "Blit framebuffer",
                    [&] { drawContext()->blitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter); });
}
} // extern C
