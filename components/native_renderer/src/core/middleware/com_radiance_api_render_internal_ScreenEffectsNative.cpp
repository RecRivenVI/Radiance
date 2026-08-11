#include "com_radiance_api_render_internal_ScreenEffectsNative.h"
#include "core/middleware/jni_exception.hpp"
#include "core/render/renderer.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/pipeline.hpp"
#include "core/render/modules/ui_module.hpp"
#include "common/render_abi.hpp"

namespace {
std::shared_ptr<UIModule> module() {
    auto framework = Renderer::instance().framework();
    return framework && framework->isRunning() && framework->pipeline() ? framework->pipeline()->uiModule() : nullptr;
}
} // namespace
JNIEXPORT jlong JNICALL Java_com_radiance_api_render_internal_ScreenEffectsNative_generation(JNIEnv *env, jclass) {
    return jni::invoke<jlong>(env, "Query screen-program generation", 0, [] {
        auto owner = module();
        return owner ? owner->effectGeneration : 0;
    });
}
JNIEXPORT jint JNICALL Java_com_radiance_api_render_internal_ScreenEffectsNative_registerProgram(
    JNIEnv *env, jclass, jstring key, jbyteArray input, jint count) {
    return jni::invoke<jint>(env, "Register compiled screen program", -1, [&] {
        auto owner = module();
        if (!owner || !key || !input) throw std::invalid_argument("No active screen program owner/input");
        const auto bytes = env->GetArrayLength(input);
        if (bytes < 20 || bytes % 4 || bytes > 1024 * 1024 || count < 1 || count > 3)
            throw std::invalid_argument("Screen SPIR-V input budget");
        std::vector<uint32_t> words(bytes / 4);
        env->GetByteArrayRegion(input, 0, bytes, reinterpret_cast<jbyte *>(words.data()));
        if (env->ExceptionCheck()) return -1;
        const char *text = env->GetStringUTFChars(key, nullptr);
        if (!text) return -1;
        std::string name(text);
        env->ReleaseStringUTFChars(key, text);
        return int(owner->registerEffectProgram(std::move(name), std::move(words), uint32_t(count)));
    });
}
JNIEXPORT void JNICALL Java_com_radiance_api_render_internal_ScreenEffectsNative_releaseProgram(JNIEnv *env,
                                                                                                jclass,
                                                                                                jlong generation,
                                                                                                jint id) {
    jni::invokeVoid(env, "Release compiled screen program", [&] {
        auto owner = module();
        if (owner && owner->effectGeneration == uint64_t(generation)) owner->releaseEffectProgram(uint32_t(id));
    });
}
JNIEXPORT jboolean JNICALL Java_com_radiance_api_render_internal_ScreenEffectsNative_submit(
    JNIEnv *env, jclass, jlong generation, jintArray description, jfloatArray parameters) {
    return jni::invoke<jboolean>(env, "Submit screen effect graph", JNI_FALSE, [&]() -> jboolean {
        auto owner = module();
        if (!owner) return JNI_FALSE;
        if (owner->effectGeneration != uint64_t(generation) || !description || !parameters)
            throw std::invalid_argument("Screen effect generation/input mismatch");
        auto length = env->GetArrayLength(description);
        if (length < 6 || length > 6 + 8 * 2 + 32 * 8) throw std::invalid_argument("Screen plan packet budget");
        std::vector<jint> values(length);
        env->GetIntArrayRegion(description, 0, length, values.data());
        if (env->ExceptionCheck()) return JNI_FALSE;
        const int targets = values[2], passes = values[3];
        if (values[0] != mcvr::abi::EFFECT_PLAN_VERSION || values[1] < 0 || values[1] > 2 || values[5] < 0 ||
            values[5] > 1 || targets < 1 || targets > 8 || passes < 1 || passes > 32 ||
            length != 6 + 2 * targets + 8 * passes || env->GetArrayLength(parameters) != passes * 4)
            throw std::invalid_argument("Screen plan version/layout mismatch");
        std::vector<jfloat> constants(passes * 4);
        env->GetFloatArrayRegion(parameters, 0, passes * 4, constants.data());
        if (env->ExceptionCheck()) return JNI_FALSE;
        mcvr::effects::Plan plan;
        plan.filter = mcvr::effects::Filter(values[5]);
        plan.targets.clear();
        plan.stage = values[1] == 0 ? mcvr::effects::Stage::WorldPost : mcvr::effects::Stage::GuiPost;
        plan.fullScreenBlur = values[1] == 2;
        plan.output = uint32_t(values[4]);
        size_t at = 6;
        for (int i = 0; i < targets; i++) {
            if (values[at] <= 0 || values[at + 1] <= 0) throw std::invalid_argument("Invalid screen target extent");
            plan.targets.push_back({uint32_t(values[at]), uint32_t(values[at + 1])});
            at += 2;
        }
        for (int i = 0; i < passes; i++) {
            if (values[at + 2] < 1 || values[at + 2] > 3) throw std::invalid_argument("Invalid effect input count");
            mcvr::effects::Pass pass{uint32_t(values[at]), uint32_t(values[at + 1]), {}};
            pass.filter = mcvr::effects::Filter(values[at + 3]);
            for (int n = 0; n < values[at + 2]; n++) pass.inputs.push_back(uint32_t(values[at + 4 + n]));
            std::copy_n(constants.begin() + i * 4, 4, pass.parameters.begin());
            plan.passes.push_back(std::move(pass));
            at += 8;
        }
        auto framework = Renderer::instance().framework();
        auto context = framework->safeAcquireCurrentContext();
        auto view = framework->pipeline()->acquirePipelineContext(context);
        if (!view || !view->uiModuleContext) return JNI_FALSE;
        view->uiModuleContext->executeEffects(plan);
        return JNI_TRUE;
    });
}
