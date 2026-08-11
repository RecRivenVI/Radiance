#include "core/render/modules/world/dlss/dlss_module.hpp"
#include "core/middleware/jni_exception.hpp"
#include "com_radiance_client_option_Options.h"

#include "core/all_extern.hpp"
#include "core/render/buffers.hpp"
#include "core/render/chunks.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"
#include <algorithm>
#include <cmath>

extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetDisplaySettings(JNIEnv *env,
                                                                                                   jclass,
                                                                                                   jboolean hdr,
                                                                                                   jfloat white,
                                                                                                   jfloat uiWhite,
                                                                                                   jfloat peak,
                                                                                                   jboolean bloom,
                                                                                                   jfloat strength,
                                                                                                   jboolean write) {
    jni::invokeVoid(env, "JNI Options.nativeSetDisplaySettings", [&] {
        if (!std::isfinite(white) || !std::isfinite(uiWhite) || !std::isfinite(peak) || !std::isfinite(strength))
            throw std::invalid_argument("Display values must be finite");
        auto &o = Renderer::options;
        const bool outputChanged = o.hdrEnabled != bool(hdr);
        o.hdrEnabled = hdr;
        o.displayWhiteNits = std::clamp(white, 80.f, 400.f);
        o.uiWhiteNits = uiWhite <= 0 ? 0 : std::clamp(uiWhite, 80.f, 1000.f);
        o.displayPeakNits = peak <= 0 ? 0 : std::clamp(peak, 80.f, 10000.f);
        o.bloomEnabled = bloom;
        o.bloomStrength = std::clamp(strength, 0.f, 1.f);
        if (outputChanged && (write || Renderer::is_initialized())) o.presentationChanged = true;
    });
}

extern "C" JNIEXPORT jstring JNICALL Java_com_radiance_client_option_Options_nativeDisplayStatus(JNIEnv *env, jclass) {
    return jni::invoke<jstring>(env, "JNI Options.nativeDisplayStatus", nullptr, [&]() -> jstring {
        if (!Renderer::is_initialized()) return env->NewStringUTF("SDR");
        const auto &p = Renderer::instance().framework()->swapchain()->displayProfile();
        const std::string name = p.encoding == mcvr::display::Encoding::Pq    ? "HDR10" :
                                 p.encoding == mcvr::display::Encoding::ScRgb ? "scRGB" :
                                                                                "SDR";
        return env->NewStringUTF((name + (p.hdr() ? " / " + std::to_string(int(p.peakNits)) + " nit / UI " +
                                                        std::to_string(int(p.uiWhite)) + " nit" :
                                                    ""))
                                     .c_str());
    });
}
#include "core/render/dlss_settings.hpp"
#include "core/render/textures.hpp"
#include "core/render/world.hpp"

JNIEXPORT void
    JNICALL Java_com_radiance_client_option_Options_nativeSetMaxFps(JNIEnv *env, jclass, jint maxFps, jboolean write) {
    jni::invokeVoid(env, "JNI com.radiance.client.option.Options.nativeSetMaxFps",
                    [&] { Renderer::options.maxFps = maxFps; });
}

JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetInactivityFpsLimit(JNIEnv *env,
                                                                                           jclass,
                                                                                           jint inactivityFpsLimit,
                                                                                           jboolean write) {
    jni::invokeVoid(env, "JNI com.radiance.client.option.Options.nativeSetInactivityFpsLimit",
                    [&] { Renderer::options.inactivityFpsLimit = inactivityFpsLimit; });
}

JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetVsync(JNIEnv *env,
                                                                              jclass,
                                                                              jboolean vsync,
                                                                              jboolean write) {
    jni::invokeVoid(env, "JNI com.radiance.client.option.Options.nativeSetVsync", [&] {
        if (Renderer::options.vsync != static_cast<bool>(vsync)) {
            Renderer::options.vsync = vsync;
            // With an early renderer the swapchain already exists when GAME reads options.
            if (write || Renderer::is_initialized()) Renderer::options.presentationChanged = true;
        }
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetChunkBuildingBatchSize(
    JNIEnv *env, jclass, jint chunkBuildingBatchSize, jboolean write) {
    jni::invokeVoid(env, "JNI com.radiance.client.option.Options.nativeSetChunkBuildingBatchSize", [&] {
        Renderer::options.chunkBuildingBatchSize = chunkBuildingBatchSize;
        if (write) Renderer::instance().world()->chunks()->resetScheduler();
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetChunkBuildingTotalBatches(
    JNIEnv *env, jclass, jint chunkBuildingTotalBatches, jboolean write) {
    jni::invokeVoid(env, "JNI com.radiance.client.option.Options.nativeSetChunkBuildingTotalBatches", [&] {
        Renderer::options.chunkBuildingTotalBatches = chunkBuildingTotalBatches;
        if (write) Renderer::instance().world()->chunks()->resetScheduler();
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetCollectChunkEmission(
    JNIEnv *env, jclass, jboolean collectChunkEmission, jboolean write) {
    jni::invokeVoid(env, "JNI com.radiance.client.option.Options.nativeSetCollectChunkEmission", [&] {
        (void)write;
        bool collect = static_cast<bool>(collectChunkEmission);
        if (Renderer::options.collectChunkEmission == collect) { return; }

        Renderer::options.collectChunkEmission = collect;
        if (!Renderer::is_initialized()) { return; }

        auto world = Renderer::instance().world();
        if (world != nullptr && world->chunks() != nullptr) { world->chunks()->setCollectChunkEmission(collect); }
        if (!collect) {
            auto textures = Renderer::instance().textures();
            if (textures != nullptr) { textures->releaseEmission(); }
        }
    });
}

JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetDlssModels(
    JNIEnv *env, jclass, jint sr, jint rr, jint fg, jboolean frameGeneration, jboolean write) {
    jni::invokeVoid(env, "JNI com.radiance.client.option.Options.nativeSetDlssModels", [&] {
        if (!mcvr::dlss::validModels(sr, rr, fg)) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Unsupported DLSS model selection");
            return;
        }
        const bool changed = Renderer::options.dlssSrModel != sr || Renderer::options.dlssRrModel != rr ||
                             Renderer::options.dlssFgModel != fg ||
                             Renderer::options.dlssFrameGeneration != bool(frameGeneration);
        Renderer::options.dlssSrModel = sr;
        Renderer::options.dlssRrModel = rr;
        Renderer::options.dlssFgModel = fg;
        if (Renderer::options.dlssFrameGeneration != bool(frameGeneration))
            Renderer::options.presentationChanged = true;
        Renderer::options.dlssFrameGeneration = frameGeneration;
        if (changed && Renderer::is_initialized()) Renderer::options.needRecreate = true;
    });
}

JNIEXPORT jboolean JNICALL Java_com_radiance_client_option_Options_nativeIsDlssFrameGenerationAvailable(JNIEnv *env,
                                                                                                        jclass) {
    return jni::invoke<jboolean>(env, "JNI com.radiance.client.option.Options.nativeIsDlssFrameGenerationAvailable",
                                 JNI_FALSE, [&]() -> jboolean { return DLSSModule::fgAvailable; });
}

extern "C" JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetReflexMode(JNIEnv *env,
                                                                                              jclass,
                                                                                              jint mode) {
    jni::invokeVoid(env, "JNI com.radiance.client.option.Options.nativeSetReflexMode", [&] {
        if (mode < 0 || mode > 2) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid Reflex mode");
            return;
        }
        Renderer::options.reflexMode = mode;
    });
}
