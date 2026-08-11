#include "core/render/chunk_trace.hpp"
#include "com_radiance_client_proxy_world_ChunkProxy.h"
#include <glm/gtc/type_ptr.hpp>

#include "core/render/chunks.hpp"
#include "core/render/renderer.hpp"
#include "core/middleware/jni_exception.hpp"

#include <iostream>

extern "C" JNIEXPORT jlong JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_beginHandoffNative(JNIEnv *env,
                                                                                                      jclass) {
    return jni::invoke<jlong>(env, "Begin section handoff", 0, [&]() -> jlong {
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Section handoff without a world");
        return world->chunks()->beginHandoff();
    });
}
extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_holdHandoffNative(JNIEnv *env,
                                                                                                    jclass,
                                                                                                    jlong token,
                                                                                                    jlong section) {
    jni::invokeVoid(env, "Hold section publication", [&] {
        if (auto world = Renderer::instance().world()) world->chunks()->holdHandoff(token, section);
    });
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_holdChangedHandoffNative(
    JNIEnv *env, jclass, jlong token, jlong section, jlong removalVersion) {
    return jni::invoke<jboolean>(env, "Hold changed published section", false, [&]() -> jboolean {
        auto world = Renderer::instance().world();
        return world && world->chunks()->holdChangedHandoff(token, section, removalVersion);
    });
}
extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_sealHandoffNative(JNIEnv *env,
                                                                                                    jclass,
                                                                                                    jlong token) {
    jni::invokeVoid(env, "Seal section handoff", [&] {
        if (auto world = Renderer::instance().world()) world->chunks()->sealHandoff(token);
    });
}
extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_joinHandoffNative(JNIEnv *env,
                                                                                                    jclass,
                                                                                                    jlong token,
                                                                                                    jlong other) {
    jni::invokeVoid(env, "Join related section handoffs", [&] {
        if (auto world = Renderer::instance().world()) world->chunks()->joinHandoff(token, other);
    });
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_commitHandoffNative(JNIEnv *env,
                                                                                                          jclass,
                                                                                                          jlong token) {
    return jni::invoke<jboolean>(env, "Commit ready section handoff", JNI_FALSE, [&]() -> jboolean {
        auto world = Renderer::instance().world();
        return world && world->chunks()->commitHandoff(token);
    });
}
extern "C" JNIEXPORT jstring JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_handoffStatusNative(JNIEnv *env,
                                                                                                         jclass,
                                                                                                         jlong token) {
    return jni::invoke<jstring>(env, "Inspect section handoff", nullptr, [&]() -> jstring {
        auto world = Renderer::instance().world();
        auto status = world ? world->chunks()->handoffStatus(token) : "world-unloaded";
        return env->NewStringUTF(status.c_str());
    });
}
extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_closeHandoffNative(JNIEnv *env,
                                                                                                     jclass,
                                                                                                     jlong token) {
    jni::invokeVoid(env, "Close section handoff", [&] {
        if (auto world = Renderer::instance().world()) world->chunks()->closeHandoff(token);
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_bindExternalSectionNative(
    JNIEnv *env, jclass, jlong id, jlong space, jdoubleArray matrix) {
    jni::invokeVoid(env, "Bind section to source space", [&] {
        if (!matrix || env->GetArrayLength(matrix) != 16) throw std::invalid_argument("Invalid section pose span");
        double data[16];
        env->GetDoubleArrayRegion(matrix, 0, 16, data);
        if (env->ExceptionCheck()) return;
        auto world = Renderer::instance().world();
        if (!world || !world->spaces().contains(space)) throw std::invalid_argument("Unknown section space");
        auto local = glm::make_mat4(data);
        for (double v : data)
            if (!std::isfinite(v)) throw std::invalid_argument("Non-finite section pose");
        world->chunks()->bindExternalChunkSpace(id, space, local);
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_completeBuildsNative(JNIEnv *env, jclass) {
    jni::invokeVoid(env, "Publish completed section builds", [&] {
        auto world = Renderer::instance().world();
        if (world)
            if (auto scheduler = world->chunks()->chunkBuildScheduler()) scheduler->tryCheckBatchesFinish();
    });
}

JNIEXPORT jlong JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_advanceBuildsNative(JNIEnv *env, jclass) {
    return jni::invoke<jlong>(env, "Chunk pipeline admission budget", 0, [&]() -> jlong {
        auto world = Renderer::instance().world();
        if (!world) return 0;
        // Source callbacks have registered their holds. Consume later completions before
        // sampling the current budget, including any nested view's new work.
        if (auto scheduler = world->chunks()->chunkBuildScheduler()) scheduler->tryCheckBatchesFinish();
        const auto [count, bytes] = world->chunks()->buildBacklog();
        return static_cast<jlong>((std::min<uint64_t>(bytes, UINT32_MAX) << 32) |
                                  std::min<uint64_t>(count, UINT32_MAX));
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_initNative(
    JNIEnv *env, jclass, jint chunkNum, jint sizeX, jint sizeY, jint sizeZ, jint bottomSectionCoord) {
    jni::invokeVoid(env, "Initialize chunk storage", [&] {
        auto world = Renderer::instance().world();
        if (world != nullptr) { world->chunks()->reset(chunkNum, sizeX, sizeY, sizeZ, bottomSectionCoord); }
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_releaseSceneNative(JNIEnv *env, jclass) {
    jni::invokeVoid(env, "Release world scene", [&] {
        auto world = Renderer::instance().world();
        if (world != nullptr) world->releaseScene();
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_updateSectionPosNative(
    JNIEnv *env, jclass, jint sectionX, jint sectionY, jint sectionZ) {
    jni::invokeVoid(env, "JNI com.radiance.client.proxy.world.ChunkProxy.updateSectionPosNative", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->chunks()->setChunkStorageSectionPos(glm::ivec3(sectionX, sectionY, sectionZ));
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_rebuildSingle(JNIEnv *env,
                                                                                     jclass,
                                                                                     jint originX,
                                                                                     jint originY,
                                                                                     jint originZ,
                                                                                     jlong index,
                                                                                     jlong generation,
                                                                                     jint geometryCount,
                                                                                     jlong geometryTypes,
                                                                                     jlong geometryGroupNames,
                                                                                     jlong geometryMaterialFlags,
                                                                                     jlong geometryTextures,
                                                                                     jlong vertexFormats,
                                                                                     jlong vertexCounts,
                                                                                     jlong vertexAddrs,
                                                                                     jint priority,
                                                                                     jboolean collectEmission) {
    jni::invokeVoid(env, "Queue chunk rebuild", [&] {
        if (priority < 0 || priority > 1) throw std::invalid_argument("Chunk priority outside [0,1]");
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->chunks()->queueChunkBuild(ChunkBuildTask{
            .x = originX,
            .y = originY,
            .z = originZ,
            .id = index,
            .generation = generation,
            .geometryCount = geometryCount,
            .geometryTypes = reinterpret_cast<int *>(geometryTypes),
            .geometryGroupNames = reinterpret_cast<const char **>(geometryGroupNames),
            .geometryMaterialFlags = reinterpret_cast<int *>(geometryMaterialFlags),
            .geometryTextures = reinterpret_cast<int *>(geometryTextures),
            .vertexFormats = reinterpret_cast<int *>(vertexFormats),
            .vertexCounts = reinterpret_cast<int *>(vertexCounts),
            .vertices = reinterpret_cast<vk::VertexFormat::PBRVertex **>(vertexAddrs),
            .priority = priority,
            .collectEmission = static_cast<bool>(collectEmission),
        });
    });
}

JNIEXPORT jlong JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_allocateExternalChunkNative(JNIEnv *env,
                                                                                                    jclass) {
    return jni::invoke<jlong>(env, "JNI com.radiance.client.proxy.world.ChunkProxy.allocateExternalChunkNative", 0,
                              [&]() -> jlong {
                                  auto world = Renderer::instance().world();
                                  if (world == nullptr) return -1;
                                  return world->chunks()->allocateExternalChunk();
                              });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_releaseExternalChunkNative(JNIEnv *env,
                                                                                                  jclass,
                                                                                                  jlong index) {
    jni::invokeVoid(env, "JNI com.radiance.client.proxy.world.ChunkProxy.releaseExternalChunkNative", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->chunks()->releaseExternalChunk(index);
    });
}

JNIEXPORT jboolean JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_isChunkReady(JNIEnv *env, jclass, jlong id) {
    return jni::invoke<jboolean>(env, "JNI com.radiance.client.proxy.world.ChunkProxy.isChunkReady", JNI_FALSE,
                                 [&]() -> jboolean {
                                     auto world = Renderer::instance().world();
                                     if (world == nullptr)
                                         return false;
                                     else
                                         return world->chunks()->isChunkReady(id);
                                 });
}

JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_countReadyChunksNative(JNIEnv *env, jclass) {
    return jni::invoke<jint>(env, "JNI com.radiance.client.proxy.world.ChunkProxy.countReadyChunksNative", 0,
                             [&]() -> jint {
                                 auto world = Renderer::instance().world();
                                 if (world == nullptr || world->chunks() == nullptr) return 0;
                                 return static_cast<jint>(world->chunks()->countReadyPrimaryChunks());
                             });
}

JNIEXPORT jstring JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_performanceSnapshotNative(JNIEnv *env,
                                                                                                    jclass) {
    return jni::invoke<jstring>(env, "JNI com.radiance.client.proxy.world.ChunkProxy.performanceSnapshotNative",
                                nullptr, [&]() -> jstring {
                                    auto world = Renderer::instance().world();
                                    if (world == nullptr || world->chunks() == nullptr) return env->NewStringUTF("");
                                    const std::string snapshot = world->chunks()->performanceSnapshot();
                                    return env->NewStringUTF(snapshot.c_str());
                                });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_markChunkDirtyNative(JNIEnv *env,
                                                                                            jclass,
                                                                                            jlong index,
                                                                                            jlong generation) {
    jni::invokeVoid(env, "JNI com.radiance.client.proxy.world.ChunkProxy.markChunkDirtyNative", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->chunks()->markChunkDirty(index, generation);
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_relocateSingle(
    JNIEnv *env, jclass, jlong index, jint originX, jint originY, jint originZ, jlong generation) {
    jni::invokeVoid(env, "JNI com.radiance.client.proxy.world.ChunkProxy.relocateSingle", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->chunks()->relocateChunk(index, originX, originY, originZ, generation);
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_invalidateSingle(JNIEnv *env,
                                                                                        jclass,
                                                                                        jlong index,
                                                                                        jlong generation) {
    jni::invokeVoid(env, "JNI com.radiance.client.proxy.world.ChunkProxy.invalidateSingle", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->chunks()->invalidateChunk(index, generation);
    });
}

JNIEXPORT jstring JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_drainUpdateTraceNative(JNIEnv *env, jclass) {
    return jni::invoke<jstring>(
        env, "Read chunk update trace", nullptr,
        [&]() -> jstring {
            auto data = mcvr::chunkTrace::drain();
            return env->NewStringUTF(data.c_str());
        },
        jni::BoundaryPolicy::allowAfterFatal);
}
