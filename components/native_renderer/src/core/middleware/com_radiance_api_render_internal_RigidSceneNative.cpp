#include "com_radiance_api_render_internal_RigidSceneNative.h"
#include "core/diagnostics/frame_profile.hpp"
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
        if (mcvr::profile::enabled()) {
            const auto ids = uint64_t(selectedCount) + uint64_t(removedCount);
            const auto data = uint64_t(bytes) + uint64_t(bindingBytes) + ids * sizeof(uint64_t);
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.instances.jni-submit", 1, data);
        }
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
extern "C" JNIEXPORT void JNICALL
Java_com_radiance_api_render_internal_RigidSceneNative_retain(JNIEnv *env, jclass, jlong group, jlong ids, jint count) {
    jni::invokeVoid(env, "Retain instance selection", [&] {
        if (group == 0 || count < 0 || count > 65536 || (count && !ids))
            throw std::invalid_argument("Invalid retained selection");
        // Ending a group after its world has closed is a no-op, not an error.
        if (auto world = Renderer::instance().world())
            world->entities()->retainRigidSelection(uint64_t(group),
                                                    {reinterpret_cast<const uint64_t *>(ids), size_t(count)});
        else if (count)
            throw std::logic_error("Retained selection without world");
    });
}
extern "C" JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_RigidSceneNative_clearRetained(JNIEnv *env,
                                                                                                       jclass) {
    jni::invokeVoid(env, "Clear retained instance selections", [&] {
        if (auto world = Renderer::instance().world()) world->entities()->clearRetainedRigidSelections();
    });
}
extern "C" JNIEXPORT jint JNICALL Java_com_radiance_api_render_internal_RigidSceneNative_selectRetained(JNIEnv *env,
                                                                                                        jclass) {
    return jni::invoke<jint>(env, "Select retained instances", 0, [&]() -> jint {
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Retained selection without world");
        return jint(world->entities()->selectRetainedRigidInstances());
    });
}
