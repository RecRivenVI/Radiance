#include "com_radiance_api_render_internal_LodSceneNative.h"
#include "core/middleware/jni_exception.hpp"
#include "core/render/renderer.hpp"
#include "core/render/world.hpp"
#include "core/render/lod_scene.hpp"

namespace {
std::shared_ptr<mcvr::LodScene> backend() {
    auto world = Renderer::instance().world();
    if (!world) throw std::logic_error("LOD PT has no native world owner");
    return world->lodScene();
}
} // namespace

JNIEXPORT void JNICALL
Java_com_radiance_api_render_internal_LodSceneNative_nearEmpty(JNIEnv *env, jclass, jint slot, jint x, jint y, jint z) {
    jni::invokeVoid(env, "Publish confirmed-empty near Voxy coverage", [&] {
        if (slot < 0 || x % 16 || y % 16 || z % 16) throw std::invalid_argument("LOD empty section coordinate");
        backend()->nearEmpty(uint32_t(slot), {x, y, z});
    });
}

JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_LodSceneNative_begin(JNIEnv *env,
                                                                                  jclass,
                                                                                  jlong epoch,
                                                                                  jint version) {
    jni::invokeVoid(env, "Begin canonical LOD scene", [&] {
        if (version != mcvr::abi::LOD_INPUT_VERSION) throw std::invalid_argument("LOD ABI version mismatch");
        backend()->begin(uint64_t(epoch));
    });
}
JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_LodSceneNative_end(JNIEnv *env, jclass, jlong epoch) {
    jni::invokeVoid(env, "End Voxy PT session", [&] { backend()->end(uint64_t(epoch)); });
}
JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_LodSceneNative_models(
    JNIEnv *env, jclass, jlong epoch, jlong records, jint count, jlong colours, jint colourCount) {
    jni::invokeVoid(env, "Publish Voxy model table", [&] {
        if (count < 1 || count > 65536 || colourCount < 0 || colourCount > 8 * 1024 * 1024 || !records ||
            (colourCount && !colours))
            throw std::invalid_argument("LOD model input range");
        backend()->models(uint64_t(epoch),
                          {reinterpret_cast<const uint32_t *>(records), size_t(count) * mcvr::lod::modelWords},
                          {reinterpret_cast<const uint32_t *>(colours), size_t(colourCount)});
    });
}
JNIEXPORT jboolean JNICALL Java_com_radiance_api_render_internal_LodSceneNative_section(
    JNIEnv *env, jclass, jlong epoch, jlong key, jlong revision, jlong quads, jint count) {
    return jni::invoke<jboolean>(env, "Queue Voxy section", JNI_FALSE, [&]() -> jboolean {
        if (count < 0 || count > 8 * 65536 || (count && !quads)) throw std::invalid_argument("LOD section input range");
        return backend()->section(uint64_t(epoch), uint64_t(key), uint64_t(revision),
                                  {reinterpret_cast<const mcvr::lod::Quad *>(quads), size_t(count)}) ?
                   JNI_TRUE :
                   JNI_FALSE;
    });
}
JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_LodSceneNative_select(
    JNIEnv *env, jclass, jlong epoch, jlongArray keys, jintArray roots, jint minY, jint maxY) {
    jni::invokeVoid(env, "Publish Voxy LOD cut", [&] {
        if (!keys || !roots) throw std::invalid_argument("LOD selection is null");
        const auto count = env->GetArrayLength(keys);
        const auto rootCount = env->GetArrayLength(roots);
        if (count > 8192 || rootCount > mcvr::lod::RootCoverage::maxRows * 3)
            throw std::length_error("LOD selection budget");
        std::vector<jlong> values(size_t(count), 0);
        env->GetLongArrayRegion(keys, 0, count, values.data());
        if (env->ExceptionCheck()) return;
        std::vector<uint64_t> unsignedValues(values.begin(), values.end());
        std::vector<jint> rootValues(size_t(rootCount), 0);
        env->GetIntArrayRegion(roots, 0, rootCount, rootValues.data());
        if (env->ExceptionCheck()) return;
        backend()->select(uint64_t(epoch), unsignedValues, mcvr::lod::RootCoverage(rootValues, minY, maxY));
    });
}
JNIEXPORT jlongArray JNICALL Java_com_radiance_api_render_internal_LodSceneNative_counters(JNIEnv *env,
                                                                                           jclass,
                                                                                           jlong epoch) {
    return jni::invoke<jlongArray>(env, "Read Voxy PT counters", nullptr, [&]() -> jlongArray {
        auto counters = backend()->counters(uint64_t(epoch));
        std::array<jlong, std::tuple_size_v<decltype(counters)>> values{};
        std::copy(counters.begin(), counters.end(), values.begin());
        auto result = env->NewLongArray(jsize(values.size()));
        if (result) env->SetLongArrayRegion(result, 0, jsize(values.size()), values.data());
        return result;
    });
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_radiance_api_render_internal_LodSceneNative_coverage(JNIEnv *env,
                                                                                                      jclass,
                                                                                                      jlong epoch,
                                                                                                      jlong previous) {
    return jni::invoke<jlongArray>(env, "Read published primary near coverage", nullptr, [&]() -> jlongArray {
        auto rows = backend()->coverage(uint64_t(epoch), uint64_t(previous));
        if (rows.empty()) return nullptr;
        std::vector<jlong> values(rows.begin(), rows.end());
        auto result = env->NewLongArray(jsize(values.size()));
        if (result) env->SetLongArrayRegion(result, 0, jsize(values.size()), values.data());
        return result;
    });
}

JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_LodSceneNative_retain(JNIEnv *env,
                                                                                   jclass,
                                                                                   jlong epoch,
                                                                                   jlongArray keys) {
    jni::invokeVoid(env, "Retire Voxy residency", [&] {
        if (!keys) throw std::invalid_argument("LOD retained selection is null");
        const auto count = env->GetArrayLength(keys);
        if (count > 8192) throw std::length_error("LOD retained selection budget");
        std::vector<jlong> values(size_t(count), 0);
        env->GetLongArrayRegion(keys, 0, count, values.data());
        if (env->ExceptionCheck()) return;
        const std::vector<uint64_t> unsignedValues(values.begin(), values.end());
        backend()->retain(uint64_t(epoch), unsignedValues);
    });
}
