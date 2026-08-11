#include "com_radiance_api_render_internal_SpaceSceneNative.h"
#include "core/middleware/jni_exception.hpp"
#include "core/render/renderer.hpp"
#include "core/render/world.hpp"
#include "core/render/chunks.hpp"
#include <glm/gtc/type_ptr.hpp>

extern "C" JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_SpaceSceneNative_sectionColour(
    JNIEnv *env, jclass, jlong id, jfloatArray plane, jfloatArray target) {
    jni::invokeVoid(env, "Update section colour gradient", [&] {
        if (!plane || !target || env->GetArrayLength(plane) != 4 || env->GetArrayLength(target) != 4)
            throw std::invalid_argument("Invalid section colour span");
        float p[4], t[4];
        env->GetFloatArrayRegion(plane, 0, 4, p);
        env->GetFloatArrayRegion(target, 0, 4, t);
        if (env->ExceptionCheck()) return;
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Section colour without world");
        world->spaces().sectionColour(id, {glm::make_vec4(p), glm::make_vec4(t)});
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_SpaceSceneNative_put(
    JNIEnv *env, jclass, jlong id, jlong parent, jdoubleArray matrix, jfloatArray normal) {
    jni::invokeVoid(env, "Update source-owned scene space", [&] {
        if (!matrix || !normal || env->GetArrayLength(matrix) != 16 || env->GetArrayLength(normal) != 9)
            throw std::invalid_argument("Invalid scene space matrix span");
        double poseData[16];
        float normalData[9];
        env->GetDoubleArrayRegion(matrix, 0, 16, poseData);
        env->GetFloatArrayRegion(normal, 0, 9, normalData);
        if (env->ExceptionCheck()) return;
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Scene space update without world");
        world->spaces().put(id, parent, {glm::make_mat4(poseData), glm::make_mat3(normalData)});
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_SpaceSceneNative_remove(JNIEnv *env,
                                                                                                jclass,
                                                                                                jlong id) {
    jni::invokeVoid(env, "Retire source-owned scene space", [&] {
        if (auto world = Renderer::instance().world()) {
            std::optional<mcvr::scene::SpacePose> pose;
            if (id && world->spaces().contains(id)) pose = world->spaces().at(id);
            world->spaces().remove(id);
            if (pose) world->chunks()->retireHeldSpace(id, *pose);
        }
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_SpaceSceneNative_motion(JNIEnv *env,
                                                                                                jclass,
                                                                                                jlong id,
                                                                                                jlong parent,
                                                                                                jdoubleArray before,
                                                                                                jdoubleArray after,
                                                                                                jdouble start,
                                                                                                jdouble end,
                                                                                                jdouble time) {
    jni::invokeVoid(env, "Set source pose endpoints", [&] {
        if (!before || !after || env->GetArrayLength(before) != 10 || env->GetArrayLength(after) != 10)
            throw std::invalid_argument("Invalid source endpoint spans");
        double first[10], last[10];
        env->GetDoubleArrayRegion(before, 0, 10, first);
        env->GetDoubleArrayRegion(after, 0, 10, last);
        if (env->ExceptionCheck()) return;
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Source pose without world");
        world->spaces().motion(
            id, parent, {mcvr::scene::PoseEndpoint::decode(first), mcvr::scene::PoseEndpoint::decode(last), start, end},
            time);
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_SpaceSceneNative_sample(JNIEnv *env,
                                                                                                jclass,
                                                                                                jlong id,
                                                                                                jdouble time) {
    jni::invokeVoid(env, "Sample source pose", [&] {
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Source pose without world");
        world->spaces().sample(id, time);
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_SpaceSceneNative_lighting(
    JNIEnv *env, jclass, jlong id, jint scene, jfloat sky, jdoubleArray matrix) {
    jni::invokeVoid(env, "Update spatial lighting inheritance", [&] {
        if (!matrix || env->GetArrayLength(matrix) != 16) throw std::invalid_argument("Invalid lighting pose span");
        double data[16];
        env->GetDoubleArrayRegion(matrix, 0, 16, data);
        if (env->ExceptionCheck()) return;
        auto world = Renderer::instance().world();
        if (!world) throw std::logic_error("Lighting space without world");
        world->spaces().lighting(id, {true, scene, sky, glm::make_mat4(data)});
    });
}
