#include "com_radiance_client_proxy_vulkan_BufferProxy.h"

#include "core/render/buffers.hpp"
#include "core/render/framebuffers.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"
#include "core/middleware/jni_exception.hpp"

namespace {
template <class Operation>
void withBufferInputs(uint32_t id, const char *name, Operation operation) {
    if (id >= 0x40000000u) {
        mcvr::FrameExecution::Producer gate(mcvr::FrameExecution::producerOverlayDeferred(), name);
        mcvr::FrameExecution::prepareDeferredProducer();
        auto buffers = Renderer::instance().buffers();
        if (!buffers) throw std::runtime_error("Vulkan buffer manager is not initialized");
        operation(*buffers, 0);
        return;
    }
    auto framework = Renderer::instance().framework();
    auto context = framework ? framework->acquireOverlayContext() : nullptr;
    if (!context) throw std::runtime_error("Raster buffer requires an acquired frame");
    auto buffers = Renderer::instance().buffers(context.get());
    if (!buffers) throw std::runtime_error("Vulkan buffer manager is not initialized");
    operation(*buffers, context->frameIndex);
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_radiance_client_proxy_vulkan_BufferProxy_allocatePersistentBuffer(JNIEnv *env, jclass) {
    jint id = -1;
    jni::invokeVoid(env, "Allocate persistent raster buffer", [&] {
        auto buffers = Renderer::instance().buffers();
        if (!buffers) throw std::runtime_error("Vulkan buffer manager is not initialized");
        id = static_cast<jint>(buffers->allocatePersistentBuffer());
    });
    return id;
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_releasePersistentBuffer(JNIEnv *env,
                                                                                                            jclass,
                                                                                                            jint id) {
    jni::invokeVoid(env, "Release persistent raster buffer", [&] {
        if (id < 0) return;
        if (auto buffers = Renderer::instance().buffers()) buffers->releasePersistentBuffer(id);
    });
}

JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_allocateBuffer(JNIEnv *env, jclass) {
    jint id = -1;
    jni::invokeVoid(
        env, "Allocate frame raster buffer",
        [&] {
            withBufferInputs(0, "Allocate frame raster buffer", [&](Buffers &buffers, uint32_t frame) {
                id = static_cast<jint>(buffers.allocateBuffer(frame));
            });
        },
        jni::BoundaryPolicy::producerOverlay);
    return id;
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_initializeBuffer(
    JNIEnv *env, jclass, jint id, jint size, jint usageFlags) {
    jni::invokeVoid(
        env, "Initialize Vulkan buffer",
        [&] {
            if (id < 0) throw std::invalid_argument("Negative Vulkan buffer id");
            if (size < 0) throw std::invalid_argument("Negative Vulkan buffer size");
            if (usageFlags == 0) throw std::invalid_argument("Vulkan buffer usage must not be empty");
            withBufferInputs(id, "Initialize Vulkan buffer", [&](Buffers &buffers, uint32_t frame) {
                buffers.initializeBuffer(id, size, usageFlags, frame);
            });
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_buildIndexBuffer(
    JNIEnv *env, jclass, jint dstId, jint type, jint drawMode, jint vertexCount, jint expectedIndexCount) {
    jni::invokeVoid(
        env, "Build Vulkan index buffer",
        [&] {
            if (dstId < 0) throw std::invalid_argument("Negative Vulkan index buffer id");
            if (type < 0 || type > 1) throw std::invalid_argument("Unsupported Vulkan index type");
            if (vertexCount < 0 || expectedIndexCount < 0)
                throw std::invalid_argument("Negative generated index-buffer count");
            withBufferInputs(dstId, "Build Vulkan index buffer", [&](Buffers &buffers, uint32_t frame) {
                buffers.buildIndexBuffer(dstId, type, drawMode, vertexCount, expectedIndexCount, frame);
            });
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_queueUpload(JNIEnv *env,
                                                                                     jclass,
                                                                                     jlong ptr,
                                                                                     jint dstId) {
    jni::invokeVoid(
        env, "Queue Vulkan buffer upload",
        [&] {
            if (dstId < 0) throw std::invalid_argument("Negative Vulkan upload destination id");
            withBufferInputs(dstId, "Queue Vulkan buffer upload", [&](Buffers &buffers, uint32_t frame) {
                buffers.queueOverlayUpload(reinterpret_cast<uint8_t *>(ptr), dstId, frame);
            });
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_queuePersistentUploadRange(
    JNIEnv *env, jclass, jlong ptr, jint size, jint dstId, jint dstOffset) {
    jni::invokeVoid(env, "Queue Vulkan persistent buffer range upload", [&] {
        if (ptr == 0 && size != 0) throw std::invalid_argument("Null range upload source");
        if (size < 0 || dstId < 0 || dstOffset < 0)
            throw std::invalid_argument("Invalid persistent buffer range upload");
        auto buffers = Renderer::instance().buffers();
        if (buffers == nullptr) throw std::runtime_error("Vulkan buffer manager is not initialized");
        buffers->queuePersistentUploadRange(reinterpret_cast<uint8_t *>(ptr), static_cast<uint32_t>(size),
                                            static_cast<uint32_t>(dstId), static_cast<uint32_t>(dstOffset));
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_performQueuedUpload(JNIEnv *env, jclass) {
    jni::invokeVoid(env, "Upload Vulkan buffers", [&] {
        auto buffers = Renderer::instance().buffers();
        if (buffers == nullptr) throw std::runtime_error("Vulkan buffer manager is not initialized");
        buffers->performQueuedUpload();
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_updateOverlayPostUniform(JNIEnv *env,
                                                                                                  jclass,
                                                                                                  jlong ptr) {
    jni::invokeVoid(env, "BufferProxy.updateOverlayPostUniform", [&] {
        auto buffers = Renderer::instance().buffers();
        if (buffers == nullptr) return;
        vk::Data::OverlayPostUBO *ubo = reinterpret_cast<vk::Data::OverlayPostUBO *>(ptr);

        auto extent = Renderer::instance().framework()->swapchain()->vkExtent();
        ubo->inSize = {extent.width, extent.height};
        ubo->outSize = {extent.width, extent.height};

        ubo->blurDir = {1.0, 0.0};
        buffers->appendOverlayPostUniform(*ubo);

        ubo->blurDir = {0.0, 1.0};
        buffers->appendOverlayPostUniform(*ubo);

        ubo->blurDir = {1.0, 0.0};
        ubo->radiusMultiplier = 0.5;
        buffers->appendOverlayPostUniform(*ubo);

        ubo->blurDir = {0.0, 1.0};
        ubo->radiusMultiplier = 0.5;
        buffers->appendOverlayPostUniform(*ubo);

        ubo->blurDir = {1.0, 0.0};
        ubo->radiusMultiplier = 0.25;
        buffers->appendOverlayPostUniform(*ubo);

        ubo->blurDir = {0.0, 1.0};
        ubo->radiusMultiplier = 0.25;
        buffers->appendOverlayPostUniform(*ubo);
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_updateDiagramPostUniform(JNIEnv *env,
                                                                                                  jclass,
                                                                                                  jlong ptr) {
    jni::invokeVoid(env, "Upload diagram post-process uniform", [&] {
        auto buffers = Renderer::instance().buffers();
        auto framework = Renderer::instance().framework();
        if (buffers == nullptr || framework == nullptr) return;

        auto *ubo = reinterpret_cast<vk::Data::OverlayPostUBO *>(ptr);
        auto extent = framework->swapchain()->vkExtent();
        if (auto framebuffers = Renderer::instance().framebuffers();
            framebuffers != nullptr && framebuffers->drawFramebufferBinding() != 0) {
            const auto snapshot = framebuffers->snapshot(mcvr::framebuffer::DRAW_FRAMEBUFFER);
            if (snapshot.extent.width == 0 || snapshot.extent.height == 0)
                throw std::runtime_error("Bound diagram framebuffer has no drawable extent");
            extent = snapshot.extent;
        }
        ubo->inSize = {extent.width, extent.height};
        ubo->outSize = {extent.width, extent.height};
        buffers->appendOverlayPostUniform(*ubo);
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_updateWorldUniform(JNIEnv *env,
                                                                                            jclass,
                                                                                            jlong ptr) {
    jni::invokeVoid(
        env, "BufferProxy.updateWorldUniform",
        [&] {
            auto &renderer = Renderer::instance();
            auto &ubo = *reinterpret_cast<vk::Data::WorldUBO *>(ptr);
            if (auto *slot = renderer.framework()->producerWorldInput()) {
                slot->worldUniform = ubo;
                Buffers::prepareWorldCoordinates(*slot->worldUniform, *renderer.inputWorld());
                slot->cameraEffects.clear();
                return;
            }
            if (auto buffers = renderer.buffers()) buffers->setAndUploadWorldUniformBuffer(ubo);
        },
        jni::BoundaryPolicy::producerWorld);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_updateSkyUniform(JNIEnv *env,
                                                                                          jclass,
                                                                                          jlong ptr) {
    jni::invokeVoid(
        env, "BufferProxy.updateSkyUniform",
        [&] {
            auto &renderer = Renderer::instance();
            auto &ubo = *reinterpret_cast<vk::Data::SkyUBO *>(ptr);
            if (auto *slot = renderer.framework()->producerWorldInput()) {
                slot->skyUniform = ubo;
                return;
            }
            if (auto buffers = renderer.buffers()) buffers->setAndUploadSkyUniformBuffer(ubo);
        },
        jni::BoundaryPolicy::producerWorld);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_BufferProxy_updateMapping(JNIEnv *env, jclass, jlong ptr) {
    jni::invokeVoid(
        env, "BufferProxy.updateMapping",
        [&] {
            auto &renderer = Renderer::instance();
            const auto *entries = reinterpret_cast<const vk::Data::TextureMapEntry *>(ptr);
            if (!entries) throw std::invalid_argument("Missing world texture mapping entries");
            if (auto *slot = renderer.framework()->producerWorldInput()) {
                slot->pendingTextureMapping.assign(
                    entries, entries + std::extent_v<decltype(vk::Data::TextureMapping::entries)>);
                return;
            }
            if (auto buffers = renderer.buffers()) buffers->setAndUploadTextureMappingBuffer(entries);
        },
        jni::BoundaryPolicy::producerWorld);
}
