#include "core/render/draw_colour.hpp"
#include "com_radiance_client_proxy_world_EntityProxy.h"

#include "core/middleware/jni_exception.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "core/render/entities.hpp"
#include "core/render/line_geometry.hpp"
#include "core/render/renderer.hpp"

extern "C" JNIEXPORT jintArray JNICALL Java_com_radiance_client_proxy_world_EntityProxy_inspectRenderAbi(JNIEnv *env,
                                                                                                         jclass) {
    return jni::invoke<jintArray>(env, "Inspect render ABI", nullptr, [&]() -> jintArray {
        std::vector<jint> values(std::begin(mcvr::abi::values), std::end(mcvr::abi::values));
        values.push_back(sizeof(vk::VertexFormat::PBRVertex));
        values.push_back(sizeof(vk::VertexFormat::PositionVertex));
        values.push_back(sizeof(vk::VertexFormat::MaterialVertex));
        values.push_back(sizeof(mcvr::lines::SourceVertex));
        auto output = env->NewIntArray(static_cast<jsize>(values.size()));
        if (output) env->SetIntArrayRegion(output, 0, static_cast<jsize>(values.size()), values.data());
        return output;
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_EntityProxy_queueRigidModelsV2(JNIEnv *env,
                                                                                                      jclass,
                                                                                                      jint abiVersion,
                                                                                                      jint recordStride,
                                                                                                      jint byteCount,
                                                                                                      jlong records,
                                                                                                      jint count,
                                                                                                      jlong groups,
                                                                                                      jint groupCount) {
    jni::invokeVoid(env, "Queue rigid models", [&] {
        mcvr::rigid::validateBatch(abiVersion, recordStride, byteCount, count);
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Rigid models submitted without a world");
        if (count < 0 || groupCount < 0) throw std::invalid_argument("Negative rigid model batch size");
        world->entities()->queueRigidModels(reinterpret_cast<const mcvr::rigid::SubmissionRecord *>(records),
                                            static_cast<size_t>(count), reinterpret_cast<const char *const *>(groups),
                                            static_cast<size_t>(groupCount));
    });
}

JNIEXPORT jboolean JNICALL Java_com_radiance_client_proxy_world_EntityProxy_beginCachedCloud(
    JNIEnv *env, jclass, jlong revision, jdouble x, jdouble y, jdouble z) {
    return jni::invoke<jboolean>(env, "Queue cached cloud geometry", JNI_FALSE, [&]() -> jboolean {
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Cloud capture without a world");
        return world->entities()->beginCachedCloud(static_cast<uint64_t>(revision), x, y, z) ? JNI_TRUE : JNI_FALSE;
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_EntityProxy_endCachedCloud(JNIEnv *env,
                                                                                       jclass,
                                                                                       jboolean success) {
    jni::invokeVoid(env, "Finish cached cloud geometry", [&] {
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Cloud capture without a world");
        world->entities()->endCachedCloud(success == JNI_TRUE);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_world_EntityProxy_queueBuildSourcesV3(JNIEnv *env,
                                                                     jclass,
                                                                     jint formatAbiVersion,
                                                                     jboolean directEntityInput,
                                                                     jint coordinate,
                                                                     jboolean normalOffset,
                                                                     jint size,
                                                                     jlong entityHashCodes,
                                                                     jlong entityPosXs,
                                                                     jlong entityPosYs,
                                                                     jlong entityPosZs,
                                                                     jlong entityRayTracingFlags,
                                                                     jlong entityPostRenderFlags,
                                                                     jlong entityPrebuiltBLASs,
                                                                     jlong entityPosts,
                                                                     jlong entityLayerCounts,
                                                                     jlong geometryTypes,
                                                                     jlong geometryGroupNames,
                                                                     jlong geometryContentNames,
                                                                     jlong geometryTextures,
                                                                     jlong vertexFormats,
                                                                     jlong indexFormats,
                                                                     jlong vertexCounts,
                                                                     jlong vertices,
                                                                     jlong vertexByteCounts,
                                                                     jlong lineStyles,
                                                                     jlong drawColours) {
    mcvr::profile::Scope dispatchProfile("entity.jni.sources");
    jni::invokeVoid(env, "Queue entity geometry sources v3", [&] {
        if (formatAbiVersion != mcvr::abi::ENTITY_INPUT_VERSION)
            throw std::invalid_argument("Unsupported entity source submission ABI");
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->entities()->queueBuild(EntitiesBuildTask{
            .coordinate = static_cast<World::Coordinates>(coordinate),
            .normalOffset = static_cast<bool>(normalOffset),
            .entityCount = size,
            .entityHashCodes = reinterpret_cast<int *>(entityHashCodes),
            .entityXs = reinterpret_cast<double *>(entityPosXs),
            .entityYs = reinterpret_cast<double *>(entityPosYs),
            .entityZs = reinterpret_cast<double *>(entityPosZs),
            .entityRayTracingFlags = reinterpret_cast<int *>(entityRayTracingFlags),
            .entityPostRenderFlags = reinterpret_cast<int *>(entityPostRenderFlags),
            .entityPrebuiltBLASs = reinterpret_cast<int *>(entityPrebuiltBLASs),
            .entityPosts = reinterpret_cast<int *>(entityPosts),
            .entityGeometryCounts = reinterpret_cast<int *>(entityLayerCounts),
            .geometryTypes = reinterpret_cast<int *>(geometryTypes),
            .geometryGroupNames = reinterpret_cast<const char **>(geometryGroupNames),
            .geometryContentNames = reinterpret_cast<const char **>(geometryContentNames),
            .geometryTextures = reinterpret_cast<int *>(geometryTextures),
            .vertexFormats = reinterpret_cast<int *>(vertexFormats),
            .indexFormats = reinterpret_cast<int *>(indexFormats),
            .vertexCounts = reinterpret_cast<int *>(vertexCounts),
            .vertices = reinterpret_cast<void **>(vertices),
            .geometryVertexByteCounts = reinterpret_cast<const int64_t *>(vertexByteCounts),
            .formatAbiVersion = formatAbiVersion,
            .directEntityInput = directEntityInput == JNI_TRUE,
            .geometryLineStyles = reinterpret_cast<const std::byte *>(lineStyles),
            .geometryDrawColours = reinterpret_cast<const glm::vec4 *>(drawColours),
        });
    });
}

extern "C" JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_world_EntityProxy_beginWorldMeshFrame(
    JNIEnv *env, jclass, jlong worldToken, jlong frameToken, jlong resourceGeneration) {
    return jni::invokeForVkResult(env, "Begin world mesh frame", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return 2;
        return world->entities()->beginWorldMeshFrame(static_cast<uint64_t>(worldToken),
                                                      static_cast<uint64_t>(frameToken),
                                                      static_cast<uint64_t>(resourceGeneration));
    });
}

extern "C" JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_world_EntityProxy_beginWorldMeshStage(
    JNIEnv *env, jclass, jlong worldToken, jlong frameToken, jlong resourceGeneration, jlong stageToken, jint stage) {
    return jni::invokeForVkResult(env, "Begin world mesh stage", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return 2;
        return world->entities()->beginWorldMeshStage(
            static_cast<uint64_t>(worldToken), static_cast<uint64_t>(frameToken),
            static_cast<uint64_t>(resourceGeneration), static_cast<uint64_t>(stageToken), stage);
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_radiance_client_proxy_world_EntityProxy_queueWorldMeshV3(JNIEnv *env,
                                                                  jclass,
                                                                  jlong worldToken,
                                                                  jlong frameToken,
                                                                  jlong resourceGeneration,
                                                                  jlong stageToken,
                                                                  jint stage,
                                                                  jint coordinate,
                                                                  jdouble originX,
                                                                  jdouble originY,
                                                                  jdouble originZ,
                                                                  jint sourceId,
                                                                  jint rayTracingFlag,
                                                                  jint geometryType,
                                                                  jint textureId,
                                                                  jint vertexFormat,
                                                                  jint drawMode,
                                                                  jint indexType,
                                                                  jint vertexCount,
                                                                  jint indexCount,
                                                                  jlong vertices,
                                                                  jint vertexBytes,
                                                                  jlong indices,
                                                                  jint indexBytes,
                                                                  jint alphaMode,
                                                                  jfloat emission,
                                                                  jlong shaderKey,
                                                                  jlong materialKey,
                                                                  jlong auditId,
                                                                  jlong lineStyle,
                                                                  jfloat red,
                                                                  jfloat green,
                                                                  jfloat blue,
                                                                  jfloat alpha) {
    mcvr::profile::Scope dispatchProfile("entity.jni.world-mesh");
    return jni::invokeForVkResult(env, "Queue world mesh", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return 2;
        return world->entities()->queueWorldMesh(WorldMeshBuildTask{
            .worldToken = static_cast<uint64_t>(worldToken),
            .frameToken = static_cast<uint64_t>(frameToken),
            .resourceGeneration = static_cast<uint64_t>(resourceGeneration),
            .stageToken = static_cast<uint64_t>(stageToken),
            .stage = stage,
            .coordinate = static_cast<World::Coordinates>(coordinate),
            .originX = originX,
            .originY = originY,
            .originZ = originZ,
            .sourceId = sourceId,
            .rayTracingFlag = rayTracingFlag,
            .geometryType = geometryType,
            .textureId = textureId,
            .vertexFormat = vertexFormat,
            .drawMode = drawMode,
            .indexType = indexType,
            .vertexCount = vertexCount,
            .indexCount = indexCount,
            .vertices = reinterpret_cast<void *>(vertices),
            .vertexBytes = vertexBytes,
            .indices = reinterpret_cast<void *>(indices),
            .indexBytes = indexBytes,
            .alphaMode = alphaMode,
            .emission = emission,
            .shaderKey = reinterpret_cast<const char *>(shaderKey),
            .materialKey = reinterpret_cast<const char *>(materialKey),
            .auditId = static_cast<uint64_t>(auditId),
            .lineStyle = reinterpret_cast<const std::byte *>(lineStyle),
            .drawColour = glm::vec4(red, green, blue, alpha),
        });
    });
}

extern "C" JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_world_EntityProxy_pollWorldMeshAudit(
    JNIEnv *env, jclass, jlong auditId, jboolean consumeTerminal) {
    return jni::invokeForVkResult(env, "Poll world mesh audit", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return 0;
        return world->entities()->pollWorldMeshAudit(static_cast<uint64_t>(auditId),
                                                     static_cast<bool>(consumeTerminal));
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_radiance_client_proxy_world_EntityProxy_endWorldMeshStage(JNIEnv *env,
                                                                   jclass,
                                                                   jlong worldToken,
                                                                   jlong frameToken,
                                                                   jlong resourceGeneration,
                                                                   jlong stageToken,
                                                                   jboolean commit) {
    jni::invokeVoid(env, "End world mesh stage", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->entities()->endWorldMeshStage(static_cast<uint64_t>(worldToken), static_cast<uint64_t>(frameToken),
                                             static_cast<uint64_t>(resourceGeneration),
                                             static_cast<uint64_t>(stageToken), static_cast<bool>(commit));
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_EntityProxy_endWorldMeshFrame(
    JNIEnv *env, jclass, jlong worldToken, jlong frameToken, jlong resourceGeneration, jboolean commit) {
    jni::invokeVoid(env, "End world mesh frame", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->entities()->endWorldMeshFrame(static_cast<uint64_t>(worldToken), static_cast<uint64_t>(frameToken),
                                             static_cast<uint64_t>(resourceGeneration), static_cast<bool>(commit));
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_EntityProxy_invalidateWorldMeshGeneration(
    JNIEnv *env, jclass, jlong resourceGeneration) {
    jni::invokeVoid(env, "Invalidate world mesh generation", [&] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->entities()->invalidateWorldMeshGeneration(static_cast<uint64_t>(resourceGeneration));
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_EntityProxy_build(JNIEnv *env, jclass) {
    jni::invokeVoid(env, "Build entity geometry", [] {
        auto world = Renderer::instance().world();
        if (world == nullptr) return;
        world->entities()->build();
    });
}

extern "C" JNIEXPORT jdoubleArray JNICALL Java_com_radiance_client_proxy_world_EntityProxy_inspectLineContract(
    JNIEnv *env, jclass, jobject styleBytes, jobject vertexBytes, jint mode, jint count) {
    return jni::invoke<jdoubleArray>(env, "Inspect line contract", nullptr, [&]() -> jdoubleArray {
        if (count < 0 || mode < 0 || mode > 3) throw std::invalid_argument("Invalid line count/mode");
        const auto *styleData = static_cast<const std::byte *>(env->GetDirectBufferAddress(styleBytes));
        const auto *vertexData =
            static_cast<const mcvr::lines::SourceVertex *>(env->GetDirectBufferAddress(vertexBytes));
        if (!styleData || !vertexData || env->GetDirectBufferCapacity(styleBytes) != mcvr::lines::styleBytes ||
            env->GetDirectBufferCapacity(vertexBytes) != uint64_t(count) * sizeof(mcvr::lines::SourceVertex))
            throw std::invalid_argument("Invalid direct line buffer extent");
        mcvr::lines::Style style;
        if (!mcvr::lines::decodeStyle({styleData, mcvr::lines::styleBytes}, style))
            throw std::invalid_argument("Invalid versioned line style");
        std::vector<double> result{double(style.source), double(style.alphaMode), style.width,
                                   style.sourceWidth,    style.emission,          double(style.flags & 1u)};
        const auto &a = style.owner;
        mcvr::lines::Frame owner{{a[0], a[1], a[2]}, {a[3], a[4], a[5]}, {a[6], a[7], a[8]}};
        for (auto [first, second] : mcvr::lines::segments(mode, count)) {
            auto frame = mcvr::lines::drawingFrame(vertexData[first], owner);
            auto [valid, corners] =
                mcvr::lines::prism(vertexData[first].position, vertexData[second].position, style.width, frame);
            if (!valid) throw std::invalid_argument("Invalid line segment");
            for (auto corner : corners) {
                result.push_back(corner.x);
                result.push_back(corner.y);
                result.push_back(corner.z);
            }
        }
        auto output = env->NewDoubleArray(static_cast<jsize>(result.size()));
        if (output) env->SetDoubleArrayRegion(output, 0, static_cast<jsize>(result.size()), result.data());
        return output;
    });
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_radiance_client_proxy_world_EntityProxy_inspectDrawColour(JNIEnv *env, jclass, jobject bytes, jint offset) {
    return jni::invoke<jfloatArray>(env, "Inspect draw colour", nullptr, [&]() -> jfloatArray {
        const auto count = env->GetDirectBufferCapacity(bytes);
        const auto data = static_cast<const std::byte *>(env->GetDirectBufferAddress(bytes));
        if (!data || count < 0 || offset < 0) throw std::invalid_argument("Invalid direct draw buffer");
        const auto value =
            mcvr::surface::decodeDrawColour({data, static_cast<size_t>(count)}, static_cast<size_t>(offset));
        auto output = env->NewFloatArray(4);
        if (output) env->SetFloatArrayRegion(output, 0, 4, value.data());
        return output;
    });
}
