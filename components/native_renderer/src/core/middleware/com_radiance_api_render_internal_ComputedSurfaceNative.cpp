#include "com_radiance_api_render_internal_ComputedSurfaceNative.h"
#include "jni_exception.hpp"
#include "core/render/renderer.hpp"
#include "core/render/entities.hpp"
#include <vector>

JNIEXPORT jlong JNICALL Java_com_radiance_api_render_internal_ComputedSurfaceNative_program(JNIEnv *env,
                                                                                            jclass,
                                                                                            jlong gen,
                                                                                            jbyteArray input) {
    return jni::invoke<jlong>(env, "Create surface program", 0, [&]() -> jlong {
        if (!input) throw std::invalid_argument("Null surface SPIR-V");
        int bytes = env->GetArrayLength(input);
        if (bytes < 20 || bytes % 4 || bytes > 1024 * 1024) throw std::invalid_argument("Surface SPIR-V size");
        std::vector<uint32_t> words(bytes / 4);
        env->GetByteArrayRegion(input, 0, bytes, reinterpret_cast<jbyte *>(words.data()));
        if (env->ExceptionCheck()) return 0;
        return Renderer::instance().world()->entities()->surfaceProgram(gen, words);
    });
}
JNIEXPORT jlong JNICALL Java_com_radiance_api_render_internal_ComputedSurfaceNative_data(
    JNIEnv *env, jclass, jlong gen, jlong address, jint bytes) {
    return jni::invoke<jlong>(env, "Create surface input", 0, [&]() -> jlong {
        if (bytes <= 0) throw std::invalid_argument("Surface input size");
        return Renderer::instance().world()->entities()->surfaceData(gen, reinterpret_cast<void *>(address),
                                                                     size_t(bytes));
    });
}
JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_ComputedSurfaceNative_release(JNIEnv *env,
                                                                                           jclass,
                                                                                           jlong gen,
                                                                                           jlong handle) {
    jni::invokeVoid(env, "Release surface resource", [&] {
        if (auto world = Renderer::instance().world()) world->entities()->releaseSurface(gen, handle);
    });
}
JNIEXPORT jboolean JNICALL Java_com_radiance_api_render_internal_ComputedSurfaceNative_submit(JNIEnv *env,
                                                                                              jclass,
                                                                                              jlong world,
                                                                                              jlong frame,
                                                                                              jlong gen,
                                                                                              jlong stage,
                                                                                              jint kind,
                                                                                              jlong program,
                                                                                              jlong source,
                                                                                              jlong contacts,
                                                                                              jlong params,
                                                                                              jint bytes,
                                                                                              jint noise,
                                                                                              jint field,
                                                                                              jint work,
                                                                                              jint vertices,
                                                                                              jint indices,
                                                                                              jint identity,
                                                                                              jdouble x,
                                                                                              jdouble y,
                                                                                              jdouble z) {
    return jni::invoke<jboolean>(env, "Queue computed surface", JNI_FALSE, [&]() -> jboolean {
        if (work <= 0 || vertices < 0 || indices < 0 || bytes <= 0)
            throw std::invalid_argument("Negative surface dispatch extent");
        return Renderer::instance().world()->entities()->queueSurface(
            world, frame, gen, stage, kind, program, source, contacts, reinterpret_cast<void *>(params), size_t(bytes),
            noise, field, uint32_t(work), uint32_t(vertices), uint32_t(indices), identity, x, y, z);
    });
}
