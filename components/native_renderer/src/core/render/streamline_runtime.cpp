#include "core/logging.hpp"
#include "streamline_runtime.hpp"
#include "renderer.hpp"
#include "render_framework.hpp"
#include "core/diagnostics/audit_sink.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "core/diagnostics/fg_timing.hpp"
#include "core/render/frame_execution.hpp"
#include <cstring>
#include <cmath>
#include <iostream>
#include <utility>
#ifdef _WIN32
#    include <sl_security.h>
#    include <sl_helpers.h>
#endif

namespace mcvr {
StreamlineRuntime &StreamlineRuntime::get() {
    static StreamlineRuntime runtime;
    return runtime;
}
bool StreamlineRuntime::check(sl::Result result, const char *operation) {
    if (result == sl::Result::eOk) return true;
    mcvr::log::error("StreamlineRuntime")
        << operation << " failed: result=" << sl::getResultAsStr(result) << " (" << int(result) << ')' << std::endl;
    return false;
}
bool StreamlineRuntime::initialize(const std::filesystem::path &directory) {
    if (initialized_) return true;
#ifdef _WIN32
    const auto path = std::filesystem::absolute(directory / "sl.interposer.dll");
    if (!sl::security::verifyEmbeddedSignature(path.c_str())) {
        mcvr::log::error("StreamlineRuntime")
            << "Interposer is missing or has an invalid signature: " << path << std::endl;
        return false;
    }
    auto module =
        LoadLibraryExW(path.c_str(), nullptr, LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    if (!module) return false;
    library_ = module;
    auto init = reinterpret_cast<PFun_slInit *>(GetProcAddress(module, "slInit"));
    shutdown_ = reinterpret_cast<PFun_slShutdown *>(GetProcAddress(module, "slShutdown"));
    instanceProc_ = reinterpret_cast<PFN_vkGetInstanceProcAddr>(GetProcAddress(module, "vkGetInstanceProcAddr"));
    deviceProc_ = reinterpret_cast<PFN_vkGetDeviceProcAddr>(GetProcAddress(module, "vkGetDeviceProcAddr"));
#    define LOAD(name)                                                                                                 \
        name = reinterpret_cast<PFun_##name *>(GetProcAddress(module, #name));                                         \
        if (!name) return false;
    LOAD(slGetFeatureFunction)
    LOAD(slGetFeatureRequirements)
    LOAD(slIsFeatureSupported)
    LOAD(slGetNewFrameToken)
    LOAD(slSetConstants)
    LOAD(slSetTagForFrame)
    LOAD(slEvaluateFeature)
    LOAD(slFreeResources)
    LOAD(slSetFeatureLoaded)
#    undef LOAD
    if (!init || !shutdown_ || !instanceProc_ || !deviceProc_) return false;
    const auto pluginPath = std::filesystem::absolute(directory).wstring();
    const wchar_t *paths[]{pluginPath.c_str()};
    const sl::Feature features[]{sl::kFeatureDLSS, sl::kFeatureDLSS_RR, sl::kFeatureDLSS_G, sl::kFeatureReflex,
                                 sl::kFeaturePCL};
    sl::Preferences preferences{};
    preferences.renderAPI = sl::RenderAPI::eVulkan;
    preferences.engine = sl::EngineType::eCustom;
    preferences.engineVersion = "MCVR-0.1.5";
    preferences.projectId = "c4ce574c-74e1-4b9b-a76c-6ff964cbba85";
    preferences.flags = sl::PreferenceFlags::eDisableCLStateTracking | sl::PreferenceFlags::eUseManualHooking |
                        sl::PreferenceFlags::eUseFrameBasedResourceTagging;
    preferences.pathsToPlugins = paths;
    preferences.numPathsToPlugins = 1;
    preferences.featuresToLoad = features;
    preferences.numFeaturesToLoad = std::size(features);
    preferences.logMessageCallback = [](sl::LogType type, const char *message) {
        try {
            const auto level = type == sl::LogType::eError ? mcvr::log::Level::Error :
                               type == sl::LogType::eWarn  ? mcvr::log::Level::Warn :
                                                             mcvr::log::Level::Info;
            mcvr::log::stream(level, "StreamlineRuntime", level == mcvr::log::Level::Info ? std::cout : std::cerr)
                << "[Streamline:" << int(type) << "] " << (message ? message : "") << std::endl;
        } catch (...) {
            // Never let a C++ exception cross the Streamline callback boundary.
        }
    };
    if (!check(init(preferences, sl::kSDKVersion), "slInit")) return false;
    initialized_ = true;
    vkCreateInstance = reinterpret_cast<PFN_vkCreateInstance>(instanceProc_(nullptr, "vkCreateInstance"));
    mcvr::log::info("StreamlineRuntime") << "Streamline " << SL_VERSION_MAJOR << '.' << SL_VERSION_MINOR << '.'
                                         << SL_VERSION_PATCH << " initialized; OTA disabled" << std::endl;
    return true;
#else
    return false;
#endif
}
void StreamlineRuntime::hookInstance(VkInstance instance) {
    if (!initialized_) return;
#define HOOK(name) name = reinterpret_cast<PFN_##name>(instanceProc_(instance, #name));
    // The create-device proxy looks up the owning instance in the enumeration
    // proxy's map; bypassing enumeration leaves SL with a null VkInstance.
    HOOK(vkEnumeratePhysicalDevices)
    HOOK(vkCreateDevice)
    HOOK(vkDestroySurfaceKHR)
    HOOK(vkDestroyInstance)
#ifdef _WIN32
    HOOK(vkCreateWin32SurfaceKHR)
#endif
#undef HOOK
}
void StreamlineRuntime::hookDevice(VkDevice device, VkPhysicalDevice physical) {
    if (!initialized_) return;
    physical_ = physical;
#define HOOK(name) name = reinterpret_cast<PFN_##name>(deviceProc_(device, #name));
    HOOK(vkCreateSwapchainKHR)
    HOOK(vkDestroySwapchainKHR)
    HOOK(vkGetSwapchainImagesKHR)
    HOOK(vkAcquireNextImageKHR)
    HOOK(vkAcquireNextImage2KHR)
    HOOK(vkQueuePresentKHR)
    HOOK(vkDeviceWaitIdle)
    HOOK(vkDestroyDevice)
#undef HOOK
#define FEATURE(feature, name)                                                                                         \
    {                                                                                                                  \
        void *function{};                                                                                              \
        if (slGetFeatureFunction(feature, #name, function) == sl::Result::eOk)                                         \
            name = reinterpret_cast<PFun_##name *>(function);                                                          \
    }
    FEATURE(sl::kFeatureDLSS, slDLSSSetOptions)
    FEATURE(sl::kFeatureDLSS, slDLSSGetOptimalSettings)
    FEATURE(sl::kFeatureDLSS_RR, slDLSSDSetOptions)
    FEATURE(sl::kFeatureDLSS_RR, slDLSSDGetOptimalSettings)
    FEATURE(sl::kFeatureDLSS_G, slDLSSGSetOptions)
    FEATURE(sl::kFeatureDLSS_G, slDLSSGGetState)
    FEATURE(sl::kFeatureReflex, slReflexSetOptions)
    FEATURE(sl::kFeatureReflex, slReflexGetState)
    FEATURE(sl::kFeatureReflex, slReflexSleep)
    FEATURE(sl::kFeaturePCL, slPCLSetMarker)
#undef FEATURE
    for (const auto &[feature, name] : std::initializer_list<std::pair<sl::Feature, const char *>>{
             {sl::kFeatureDLSS, "DLSS-SR"}, {sl::kFeatureDLSS_RR, "DLSS-RR"}}) {
        sl::FeatureRequirements requirements{};
        const auto requirementsResult = slGetFeatureRequirements(feature, requirements);
        if (requirementsResult == sl::Result::eOk) {
            mcvr::log::info("StreamlineRuntime") << name << " maxNumViewports=" << requirements.maxNumViewports
                                                 << " requiredTags=" << requirements.numRequiredTags << std::endl;
        } else {
            check(requirementsResult, name);
        }
    }
    // DLSS-G owns a proxy swapchain whenever its plugin is loaded, even while
    // generation mode is off. Keep it unloaded until the user actually enables
    // FG; presentation recreation will load it before creating the new swapchain.
    setFGLoaded(Renderer::options.dlssFrameGeneration);
}
bool StreamlineRuntime::supported(sl::Feature feature) const {
    if (!initialized_ || !physical_) return false;
    sl::AdapterInfo adapter{};
    adapter.vkPhysicalDevice = physical_;
    return slIsFeatureSupported(feature, adapter) == sl::Result::eOk;
}
void StreamlineRuntime::shutdown() {
    if (initialized_) check(shutdown_(), "slShutdown");
    initialized_ = false;
    physical_ = nullptr;
    token_ = nullptr;
    nextToken_ = nullptr;
    phase_ = nextPhase_ = FramePhase::Unstarted;
    nextFrameIndex_ = 0;
    lastLatencyReport_ = 0;
    reflexMode_ = -1;
    // Keep the interposer loaded while Vulkan objects and their proxy functions exist.
}
sl::FrameToken *StreamlineRuntime::newFrameToken() {
    std::unique_lock lock(tokenCreation_, std::defer_lock);
    {
        profile::Scope wait("streamline.frame-token-lock");
        lock.lock();
    }
    profile::Scope timing("streamline.frame-token");
    sl::FrameToken *token = nullptr;
    const uint32_t frameIndex = ++nextFrameIndex_;
    return check(slGetNewFrameToken(token, &frameIndex), "slGetNewFrameToken") ? token : nullptr;
}
sl::FrameToken *StreamlineRuntime::frame() {
    if (const auto *slot = static_cast<const FrameworkContext *>(FrameExecution::workerRecordingSlot()))
        return slot->streamlineToken;
    if (initialized_ && !token_) token_ = newFrameToken();
    return token_;
}
bool StreamlineRuntime::prepareNextFrame(int mode, uint32_t fpsLimit) {
    const uint32_t limitUs = mode > 0 && fpsLimit > 0 ? 1000000u / fpsLimit : 0;
    // Option changes are serialized with rendering. Sleep and PCL accept an explicit token.
    if (!initialized_ || nextToken_ || mode != reflexMode_ || limitUs != reflexFrameLimitUs_ || !slReflexSleep)
        return false;
    nextToken_ = newFrameToken();
    if (!nextToken_) return false;
    nextPhase_ = FramePhase::Unstarted;
    profile::Scope sleep("streamline.reflex-sleep-next");
    const auto paceStart = profile::enabled() ? profile::now() : 0;
    nextPacing_ = check(slReflexSleep(*nextToken_), "slReflexSleep") && mode > 0;
    if (paceStart) FrameExecution::noteProducerPacing(paceStart, profile::now());
    return true;
}
void StreamlineRuntime::beginSimulation(bool prepared) {
    if (!initialized_) return;
    auto &token = prepared ? nextToken_ : token_;
    auto &phase = prepared ? nextPhase_ : phase_;
    // Loading and connection transitions can enter runTick again before a present.
    if (!token || phase != FramePhase::Unstarted) token = newFrameToken();
    phase = FramePhase::Simulating;
    mark(sl::PCLMarker::eSimulationStart, token);
}

void StreamlineRuntime::beginRenderSubmission() {
    if (!initialized_ || phase_ == FramePhase::Rendering) return;
    // A nested tick can present and prepare a new frame before its caller resumes.
    if (phase_ == FramePhase::Unstarted) beginSimulation();
    mark(sl::PCLMarker::eSimulationEnd, token_);
    mark(sl::PCLMarker::eRenderSubmitStart, token_);
    phase_ = FramePhase::Rendering;
}
bool StreamlineRuntime::activatePreparedFrame() {
    if (!nextToken_) return false;
    token_ = std::exchange(nextToken_, nullptr);
    phase_ = std::exchange(nextPhase_, FramePhase::Unstarted);
    pacing_ = nextPacing_;
    return true;
}
void StreamlineRuntime::beginFrame(int mode, uint32_t fpsLimit) {
    profile::Scope timing("streamline.begin-frame");
    if (!initialized_) return;
    if (activatePreparedFrame()) return;
    pacing_ = false;
    token_ = nullptr;
    phase_ = FramePhase::Unstarted;
    if (!frame()) return;
    if (slReflexSetOptions && supported(sl::kFeatureReflex)) {
        sl::ReflexOptions options{};
        options.mode = static_cast<sl::ReflexMode>(mode);
        options.frameLimitUs = mode > 0 && fpsLimit > 0 ? 1000000u / fpsLimit : 0;
        // Reapply after swapchain replacement, even when the selected mode is unchanged.
        if (reflexMode_ != mode || reflexFrameLimitUs_ != options.frameLimitUs) {
            profile::Scope configure("streamline.reflex-set-options");
            if (!check(slReflexSetOptions(options), "slReflexSetOptions")) return;
            if (reflexMode_ != mode)
                mcvr::log::info("StreamlineRuntime") << "Effective Reflex mode=" << mode << std::endl;
            reflexMode_ = mode;
            reflexFrameLimitUs_ = options.frameLimitUs;
        }
        if (slReflexSleep) {
            profile::Scope sleep("streamline.reflex-sleep");
            pacing_ = check(slReflexSleep(*token_), "slReflexSleep") && mode > 0;
        }
    }
}
void StreamlineRuntime::marker(sl::PCLMarker marker) {
    profile::Scope timing("streamline.marker");
    if (slPCLSetMarker) mark(marker, frame());
}

void StreamlineRuntime::mark(sl::PCLMarker marker, sl::FrameToken *token) {
    if (!slPCLSetMarker || !token) return;
    const bool accepted = check(slPCLSetMarker(marker, *token), "slPCLSetMarker");
    static const bool trace = [] {
        const auto *flag = std::getenv("RADIANCE_A_PCL_TRACE");
        return flag && std::string_view(flag) == "1";
    }();
    if (trace) {
        const auto *slot = static_cast<const FrameworkContext *>(FrameExecution::workerRecordingSlot());
        mcvr::log::info("StreamlineRuntime")
            << "A5_PCL frame=" << uint32_t(*token) << " marker=" << uint32_t(marker)
            << " slot=" << (slot ? slot->frameSerial : 0) << " accepted=" << accepted << std::endl;
    }
}
void StreamlineRuntime::reportLatency() {
    if (!slReflexGetState || !audit::enabled(MCVR_AUDIT_TIMING)) return;
    sl::ReflexState state{};
    if (slReflexGetState(state) != sl::Result::eOk || !state.latencyReportAvailable) return;
    auto last = lastLatencyReport_;
    for (const auto &report : state.frameReport) {
        if (report.frameID <= lastLatencyReport_ || !report.gpuRenderEndTime) continue;
        if (report.inputSampleTime && report.gpuRenderEndTime >= report.inputSampleTime)
            fgdiag::add(fgdiag::PcLatency, double(report.gpuRenderEndTime - report.inputSampleTime) / 1000.0);
        if (report.simStartTime && report.gpuRenderEndTime >= report.simStartTime)
            fgdiag::add(fgdiag::SimulationToGpu, double(report.gpuRenderEndTime - report.simStartTime) / 1000.0);
        last = std::max(last, report.frameID);
    }
    lastLatencyReport_ = last;
}

uint32_t StreamlineRuntime::allocateViewport() {
    return nextViewport_++;
}
OptionalFeatureLoadResult StreamlineRuntime::setFGLoaded(bool loaded) {
    switch (planOptionalFeatureLoad(initialized_, slSetFeatureLoaded != nullptr, fgLoaded_, loaded)) {
        case OptionalFeatureLoadAction::NoChange: return OptionalFeatureLoadResult::Unchanged;
        case OptionalFeatureLoadAction::SatisfiedWithoutRuntime:
            return OptionalFeatureLoadResult::SatisfiedWithoutRuntime;
        case OptionalFeatureLoadAction::Unavailable: return OptionalFeatureLoadResult::Unavailable;
        case OptionalFeatureLoadAction::InvokeRuntime: break;
    }
    if (!check(slSetFeatureLoaded(sl::kFeatureDLSS_G, loaded), loaded ? "load FG" : "unload FG")) {
        return OptionalFeatureLoadResult::Failed;
    }
    fgLoaded_ = loaded;
    slDLSSGSetOptions = nullptr;
    slDLSSGGetState = nullptr;
    if (loaded && slGetFeatureFunction) {
        void *setOptions{};
        void *getState{};
        if (slGetFeatureFunction(sl::kFeatureDLSS_G, "slDLSSGSetOptions", setOptions) == sl::Result::eOk) {
            slDLSSGSetOptions = reinterpret_cast<PFun_slDLSSGSetOptions *>(setOptions);
        }
        if (slGetFeatureFunction(sl::kFeatureDLSS_G, "slDLSSGGetState", getState) == sl::Result::eOk) {
            slDLSSGGetState = reinterpret_cast<PFun_slDLSSGGetState *>(getState);
        }
    }
    mcvr::log::info("StreamlineRuntime") << "DLSS-G plugin " << (loaded ? "loaded" : "unloaded") << std::endl;
    return OptionalFeatureLoadResult::Changed;
}
void StreamlineRuntime::disableFG(bool retainResources) {
    if (initialized_ && fgLoaded_ && slDLSSGSetOptions && supported(sl::kFeatureDLSS_G)) {
        sl::DLSSGOptions options{};
        if (retainResources) options.flags = sl::DLSSGFlags::eRetainResourcesWhenOff;
        check(slDLSSGSetOptions(sl::ViewportHandle(0), options), "FG off");
        if (auto *token = frame()) {
            const sl::ResourceTag tags[]{
                {nullptr, sl::kBufferTypeDepth, sl::ResourceLifecycle::eValidUntilPresent},
                {nullptr, sl::kBufferTypeMotionVectors, sl::ResourceLifecycle::eValidUntilPresent},
                {nullptr, sl::kBufferTypeHUDLessColor, sl::ResourceLifecycle::eValidUntilPresent},
                {nullptr, sl::kBufferTypeUIAlpha, sl::ResourceLifecycle::eValidUntilPresent}};
            check(slSetTagForFrame(*token, sl::ViewportHandle(0), tags, 4, nullptr), "clear FG tags");
        }
    }
}
uint32_t StreamlineRuntime::presentedFrames() {
    if (!initialized_ || !fgLoaded_ || !slDLSSGGetState || !supported(sl::kFeatureDLSS_G)) return 0;
    sl::DLSSGState state{};
    if (!check(slDLSSGGetState(sl::ViewportHandle(0), state, nullptr), "FG state")) return 0;
    if (mcvr::audit::enabled(MCVR_AUDIT_CAPTURE))
        mcvr::log::info("StreamlineRuntime")
            << "FG_EVIDENCE token=" << (frame() ? uint32_t(*frame()) : 0) << " status=" << uint32_t(state.status)
            << " presented=" << state.numFramesActuallyPresented << std::endl;
    return state.numFramesActuallyPresented;
}
sl::float4x4 slMatrix(const glm::mat4 &matrix) {
    sl::float4x4 out{};
    // GLM column-major column-vector storage is SL row-major row-vector storage.
    std::memcpy(&out, &matrix[0][0], sizeof(out));
    return out;
}
sl::Constants slConstants(glm::uvec2 size,
                          glm::vec2 jitter,
                          const glm::mat4 &view,
                          const glm::mat4 &p,
                          const glm::mat4 &toPrevious,
                          bool reset) {
    sl::Constants c{};
    c.cameraViewToClip = slMatrix(p);
    c.clipToCameraView = slMatrix(glm::inverse(p));
    c.clipToLensClip = slMatrix(glm::mat4(1));
    c.clipToPrevClip = slMatrix(toPrevious);
    c.prevClipToClip = slMatrix(glm::inverse(toPrevious));
    c.jitterOffset = {-jitter.x, -jitter.y};
    c.mvecScale = {1.f / size.x, 1.f / size.y};
    c.cameraPinholeOffset = {0, 0};
    const auto inverse = glm::inverse(view);
    c.cameraPos = {inverse[3].x, inverse[3].y, inverse[3].z};
    c.cameraRight = {inverse[0].x, inverse[0].y, inverse[0].z};
    c.cameraUp = {inverse[1].x, inverse[1].y, inverse[1].z};
    c.cameraFwd = {-inverse[2].x, -inverse[2].y, -inverse[2].z};
    c.cameraNear = std::abs(p[3][2] / p[2][2]);
    c.cameraFar = std::abs((p[3][2] - p[3][3]) / (p[2][2] - p[2][3]));
    if (!std::isfinite(c.cameraFar)) c.cameraFar = 100000.f;
    c.cameraFOV = 2 * std::atan(1 / std::abs(p[1][1]));
    c.cameraAspectRatio = std::abs(p[1][1] / p[0][0]);
    c.depthInverted = sl::eFalse;
    c.cameraMotionIncluded = sl::eTrue;
    c.motionVectors3D = sl::eFalse;
    c.reset = reset ? sl::eTrue : sl::eFalse;
    c.orthographicProjection = std::abs(p[2][3]) < .5f ? sl::eTrue : sl::eFalse;
    c.motionVectorsInvalidValue = 65504.f;
    return c;
}
} // namespace mcvr
