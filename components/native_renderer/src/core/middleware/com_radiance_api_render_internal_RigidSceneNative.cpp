#include "com_radiance_api_render_internal_RigidSceneNative.h"
#include "core/middleware/jni_exception.hpp"
#include "core/render/entities.hpp"
#include "core/render/renderer.hpp"
extern "C" JNIEXPORT void JNICALL
Java_com_radiance_api_render_internal_RigidSceneNative_releaseGeometry(JNIEnv *env, jclass, jlong identity) {
    jni::invokeVoid(env, "Retire source geometry", [&] {
        if (identity <= 0) throw std::invalid_argument("Invalid geometry identity");
        if (auto world = Renderer::instance().world()) world->entities()->releaseRigidGeometry(uint64_t(identity));
    });
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_radiance_api_render_internal_RigidSceneNative_submit(JNIEnv *env,
                                                              jclass,
                                                              jint version,
                                                              jint stride,
                                                              jint bytes,
                                                              jlong changes,
                                                              jint changeCount,
                                                              jlong groups,
                                                              jint groupCount,
                                                              jint bindingVersion,
                                                              jint bindingBytes,
                                                              jlong bindings,
                                                              jint bindingCount,
                                                              jlong selected,
                                                              jint selectedCount,
                                                              jlong removed,
                                                              jint removedCount) {
    return jni::invoke<jlongArray>(env, "Submit persistent instance changes", nullptr, [&]() -> jlongArray {
        mcvr::rigid::validateBatch(version, stride, bytes, changeCount);
        mcvr::rigid::validateBindingBatch(bindingVersion, bindingBytes, bindingCount);
        if (selectedCount < 0 || removedCount < 0 || removedCount > 65536 || groupCount < 0 ||
            (changeCount && !changes) || (bindingCount && !bindings) || (selectedCount && !selected) ||
            (removedCount && !removed))
            throw std::invalid_argument("Invalid persistent input span");
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Persistent input without world");
        auto refused = world->entities()->updateRigidInstances(
            {reinterpret_cast<const mcvr::rigid::SubmissionRecord *>(changes), size_t(changeCount)},
            reinterpret_cast<const char *const *>(groups), size_t(groupCount),
            {reinterpret_cast<const mcvr::rigid::ModelBinding *>(bindings), size_t(bindingCount)},
            {reinterpret_cast<const uint64_t *>(selected), size_t(selectedCount)},
            {reinterpret_cast<const uint64_t *>(removed), size_t(removedCount)});
        if (refused.empty()) return nullptr;
        std::vector<jlong> values(refused.begin(), refused.end());
        auto result = env->NewLongArray(jsize(values.size()));
        if (result) env->SetLongArrayRegion(result, 0, jsize(values.size()), values.data());
        return result;
    });
}
