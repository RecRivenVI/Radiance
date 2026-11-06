#include "com_radiance_client_proxy_vulkan_TextureProxy.h"

#include "core/diagnostics/state_bridge_audit.hpp"
#include "core/render/emission.hpp"
#include "core/render/renderer.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/textures.hpp"
#include "core/middleware/jni_exception.hpp"

namespace {
constexpr size_t pendingTextureLimit = 4096;
constexpr size_t pendingTextureBytesLimit = 64 * 1024 * 1024;

template <class Update>
void applyTextureInput(const char *operation, Update update) {
    mcvr::FrameExecution::Producer gate(mcvr::FrameExecution::producerOverlayDeferred(), operation);
    mcvr::FrameExecution::prepareDeferredProducer();
    if (auto textures = Renderer::instance().textures()) update(*textures);
}

template <class Update>
void stageSamplerInput(const char *operation, Update update) {
    auto framework = Renderer::instance().framework();
    if (auto frame = framework ? framework->deferredTextureContext() : nullptr;
        frame && frame->pendingTextureUpdates.size() < pendingTextureLimit) {
        frame->pendingTextureUpdates.emplace_back(std::move(update));
        return;
    }
    applyTextureInput(operation, std::move(update));
}
} // namespace

extern "C" {
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_setWorldMaterial(JNIEnv *env,
                                                                                           jclass,
                                                                                           jint id,
                                                                                           jlong parameters) {
    jni::invokeVoid(env, "Declare layered world material", [&] {
        if (parameters == 0) throw std::invalid_argument("Missing world material parameters");
        Renderer::instance().textures()->setWorldMaterial(
            static_cast<uint32_t>(id), *reinterpret_cast<const vk::Data::LayeredWorldMaterial *>(parameters));
    });
}
JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_generateTextureIdNative(JNIEnv *env, jclass) {
    jint id = 0;
    jni::invokeVoid(env, "Allocate Vulkan texture ID", [&] {
        auto textures = Renderer::instance().textures();
        if (textures == nullptr) throw std::runtime_error("Vulkan textures are not initialized");
        id = static_cast<jint>(textures->allocateTexture());
    });
    return id;
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_releaseTextureIdNative(JNIEnv *env,
                                                                                                 jclass,
                                                                                                 jint id,
                                                                                                 jint fallbackId) {
    jni::invokeVoid(env, "Release Vulkan texture ID", [&] {
        auto textures = Renderer::instance().textures();
        if (textures != nullptr) {
            textures->releaseTexture(static_cast<uint32_t>(id), static_cast<uint32_t>(fallbackId));
        }
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_prepareImageNative(
    JNIEnv *env, jclass, jint id, jint maxLevel, jint width, jint height, jint format) {
    jni::invokeVoid(env, "Allocate Vulkan texture", [&] {
        auto textures = Renderer::instance().textures();
        if (textures == nullptr) return;
        auto vkFormat = static_cast<VkFormat>(format);
        textures->initializeTexture(id, maxLevel, width, height, vkFormat);
        if (auto emission = textures->emission(); emission != nullptr) {
            emission->resetTexture(static_cast<uint32_t>(id));
        }
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_setFilter(
    JNIEnv *env, jclass, jint id, jint samplingMode, jint mipmapMode) {
    constexpr const char *operation = "Configure Vulkan texture filtering";
    jni::invokeVoid(
        env, operation,
        [&] {
            stageSamplerInput(operation, [=](Textures &textures) {
                textures.setSamplingMode(id, static_cast<VkFilter>(samplingMode),
                                         static_cast<VkSamplerMipmapMode>(mipmapMode));
            });
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_setClamp(JNIEnv *env,
                                                                                   jclass,
                                                                                   jint id,
                                                                                   jint addressMode) {
    constexpr const char *operation = "Configure Vulkan texture addressing";
    jni::invokeVoid(
        env, operation,
        [&] {
            stageSamplerInput(operation, [=](Textures &textures) {
                textures.setAddressMode(id, static_cast<VkSamplerAddressMode>(addressMode));
            });
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_queueUpload(JNIEnv *env,
                                                                                      jclass,
                                                                                      jlong srcPointer,
                                                                                      jint srcSizeInBytes,
                                                                                      jint srcRowPixels,
                                                                                      jint dstId,
                                                                                      jint srcOffsetX,
                                                                                      jint srcOffsetY,
                                                                                      jint dstOffsetX,
                                                                                      jint dstOffsetY,
                                                                                      jint width,
                                                                                      jint height,
                                                                                      jint level) {
    constexpr const char *operation = "Queue Vulkan texture upload";
    jni::invokeVoid(
        env, operation,
        [&] {
            auto framework = Renderer::instance().framework();
            if (auto frame = framework ? framework->deferredTextureContext() : nullptr;
                frame && width > 0 && height > 0 && frame->pendingTextureUpdates.size() < pendingTextureLimit) {
                auto destination = Renderer::instance().textures(frame.get())->texture(dstId);
                if (!destination) throw std::invalid_argument("Texture upload requires initialized storage");
                const auto texelBytes = vk::formatToByte(destination->vkFormat());
                const auto region = mcvr::TextureUploadRegion::checked(srcSizeInBytes, srcRowPixels, srcOffsetX,
                                                                       srcOffsetY, width, height, texelBytes);
                if (region.packedBytes <= pendingTextureBytesLimit - frame->pendingTextureBytes) {
                    std::vector<uint8_t> pixels(region.packedBytes);
                    region.copy(pixels.data(), reinterpret_cast<const void *>(srcPointer));
                    frame->pendingTextureUpdates.emplace_back([pixels = std::move(pixels), dstId, dstOffsetX,
                                                               dstOffsetY, width, height,
                                                               level](Textures &textures) mutable {
                        textures.queueUpload(pixels.data(), static_cast<uint32_t>(pixels.size()), width, dstId, 0, 0,
                                             dstOffsetX, dstOffsetY, width, height, level);
                    });
                    frame->pendingTextureBytes += region.packedBytes;
                    return;
                }
            }
            applyTextureInput(operation, [&](Textures &textures) {
                textures.queueUpload(reinterpret_cast<uint8_t *>(srcPointer), srcSizeInBytes, srcRowPixels, dstId,
                                     srcOffsetX, srcOffsetY, dstOffsetX, dstOffsetY, width, height, level);
            });
        },
        jni::BoundaryPolicy::producerOverlay);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_downloadTexture(
    JNIEnv *env, jclass, jint id, jint level, jint width, jint height, jint channel, jlong dstPointer) {
    jni::invokeVoid(env, "Download Vulkan texture", [&] {
        auto textures = Renderer::instance().textures();
        if (textures == nullptr) { throw std::runtime_error("Texture manager is unavailable"); }
        const VkResult result = textures->downloadTexture(
            static_cast<uint32_t>(id), static_cast<uint32_t>(level), static_cast<uint32_t>(width),
            static_cast<uint32_t>(height), static_cast<uint32_t>(channel), reinterpret_cast<void *>(dstPointer));
        if (result != VK_SUCCESS) {
            throw std::runtime_error("Texture download failed with VkResult=" + std::to_string(result));
        }
    });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_radiance_client_proxy_vulkan_TextureProxy_isFramebufferTexture(JNIEnv *env, jclass, jint id) {
    return jni::invoke<jboolean>(
        env, "Query framebuffer texture storage", JNI_FALSE,
        [&] {
            auto &renderer = Renderer::instance();
            const auto serial = [&] {
                mcvr::FrameExecution::Producer gate(true, "Verify framebuffer texture query");
                auto textures = renderer.textures();
                if (!textures) throw std::runtime_error("Texture manager is unavailable");
                return textures->isFramebufferTexture(id);
            };
            if (!mcvr::FrameExecution::enabled()) return serial();
            const bool value = renderer.producerFramebufferTexture(id);
            if (mcvr::state_audit::verifyQueryMirrors())
                mcvr::state_audit::verifyQuery("isFramebufferTexture", value, serial());
            return value;
        },
        jni::BoundaryPolicy::frameHandoff);
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_uploadEmissionTileNative(
    JNIEnv *env, jclass, jint textureId, jlong tileKey, jlong cellsPtr, jint cellCount) {
    jni::invokeVoid(env, "JNI com.radiance.client.proxy.vulkan.TextureProxy.uploadEmissionTileNative", [&] {
        auto textures = Renderer::instance().textures();
        if (textures == nullptr) return;
        auto emission = textures->emission();
        if (emission == nullptr) return;

        emission->updateTile(static_cast<uint32_t>(textureId), static_cast<uint64_t>(tileKey),
                             reinterpret_cast<const EmissionCellUpload *>(cellsPtr), cellCount);
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_vulkan_TextureProxy_performQueuedUpload(JNIEnv *env, jclass) {
    jni::invokeVoid(env, "Upload Vulkan textures", [&] {
        auto textures = Renderer::instance().textures();
        if (textures == nullptr) return;
        textures->performQueuedUpload();
    });
}
}
