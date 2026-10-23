#include "com_radiance_compatibility_voxy_VoxyNative.h"
#include "core/middleware/jni_exception.hpp"
#include "core/render/renderer.hpp"
#include "core/render/world.hpp"
#include "core/render/voxy_sections.hpp"

namespace {
std::shared_ptr<mcvr::VoxySections> backend() {
    auto world = Renderer::instance().world();
    if (!world) throw std::logic_error("Voxy PT has no native world owner");
    return world->voxy();
}
} // namespace

JNIEXPORT void JNICALL
Java_com_radiance_compatibility_voxy_VoxyNative_nearEmpty(JNIEnv *env, jclass, jint slot, jint x, jint y, jint z) {
    jni::invokeVoid(env, "Publish confirmed-empty near Voxy coverage", [&] {
        if (slot < 0 || x % 16 || y % 16 || z % 16) throw std::invalid_argument("Voxy empty section coordinate");
        backend()->nearEmpty(uint32_t(slot), {x, y, z});
    });
}

JNIEXPORT void JNICALL Java_com_radiance_compatibility_voxy_VoxyNative_begin(JNIEnv *env, jclass, jlong epoch) {
    jni::invokeVoid(env, "Begin Voxy PT session", [&] { backend()->begin(uint64_t(epoch)); });
}
JNIEXPORT void JNICALL Java_com_radiance_compatibility_voxy_VoxyNative_end(JNIEnv *env, jclass, jlong epoch) {
    jni::invokeVoid(env, "End Voxy PT session", [&] { backend()->end(uint64_t(epoch)); });
}
JNIEXPORT void JNICALL Java_com_radiance_compatibility_voxy_VoxyNative_models(
    JNIEnv *env, jclass, jlong epoch, jlong records, jint count, jlong colours, jint colourCount) {
    jni::invokeVoid(env, "Publish Voxy model table", [&] {
        if (count < 1 || count > 65536 || colourCount < 0 || colourCount > 8 * 1024 * 1024 || !records ||
            (colourCount && !colours))
            throw std::invalid_argument("Voxy model input range");
        backend()->models(uint64_t(epoch), {reinterpret_cast<const uint32_t *>(records), size_t(count) * 16},
                          {reinterpret_cast<const uint32_t *>(colours), size_t(colourCount)});
    });
}
JNIEXPORT jboolean JNICALL Java_com_radiance_compatibility_voxy_VoxyNative_section(
    JNIEnv *env, jclass, jlong epoch, jlong key, jlong revision, jlong quads, jint count) {
    return jni::invoke<jboolean>(env, "Queue Voxy section", JNI_FALSE, [&]() -> jboolean {
        if (count < 0 || count > 8 * 65536 || (count && !quads))
            throw std::invalid_argument("Voxy section input range");
        return backend()->section(uint64_t(epoch), uint64_t(key), uint64_t(revision),
                                  {reinterpret_cast<const uint64_t *>(quads), size_t(count)}) ?
                   JNI_TRUE :
                   JNI_FALSE;
    });
}
JNIEXPORT void JNICALL Java_com_radiance_compatibility_voxy_VoxyNative_select(
    JNIEnv *env, jclass, jlong epoch, jlongArray keys, jintArray roots, jint minY, jint maxY) {
    jni::invokeVoid(env, "Publish Voxy LOD cut", [&] {
        if (!keys || !roots) throw std::invalid_argument("Voxy selection is null");
        const auto count = env->GetArrayLength(keys);
        const auto rootCount = env->GetArrayLength(roots);
        if (count > 8192 || rootCount > mcvr::voxy::RootCoverage::maxRows * 3)
            throw std::length_error("Voxy selection budget");
        std::vector<jlong> values(size_t(count), 0);
        env->GetLongArrayRegion(keys, 0, count, values.data());
        if (env->ExceptionCheck()) return;
        std::vector<uint64_t> unsignedValues(values.begin(), values.end());
        std::vector<jint> rootValues(size_t(rootCount), 0);
        env->GetIntArrayRegion(roots, 0, rootCount, rootValues.data());
        if (env->ExceptionCheck()) return;
        backend()->select(uint64_t(epoch), unsignedValues, mcvr::voxy::RootCoverage(rootValues, minY, maxY));
    });
}
JNIEXPORT jlongArray JNICALL Java_com_radiance_compatibility_voxy_VoxyNative_counters(JNIEnv *env,
                                                                                      jclass,
                                                                                      jlong epoch) {
    return jni::invoke<jlongArray>(env, "Read Voxy PT counters", nullptr, [&]() -> jlongArray {
        auto counters = backend()->counters(uint64_t(epoch));
        const std::array<jlong, 5> values{jlong(counters[0]), jlong(counters[1]), jlong(counters[2]),
                                          jlong(counters[3]), jlong(counters[4])};
        auto result = env->NewLongArray(5);
        if (result) env->SetLongArrayRegion(result, 0, 5, values.data());
        return result;
    });
}

JNIEXPORT void JNICALL Java_com_radiance_compatibility_voxy_VoxyNative_retain(JNIEnv *env,
                                                                              jclass,
                                                                              jlong epoch,
                                                                              jlongArray keys) {
    jni::invokeVoid(env, "Retire Voxy residency", [&] {
        if (!keys) throw std::invalid_argument("Voxy retained selection is null");
        const auto count = env->GetArrayLength(keys);
        if (count > 8192) throw std::length_error("Voxy retained selection budget");
        std::vector<jlong> values(size_t(count), 0);
        env->GetLongArrayRegion(keys, 0, count, values.data());
        if (env->ExceptionCheck()) return;
        const std::vector<uint64_t> unsignedValues(values.begin(), values.end());
        backend()->retain(uint64_t(epoch), unsignedValues);
    });
}
