#include <utility>
#include "collector.hpp"
#include "profile_collector.hpp"
#include <jni.h>
#include <windows.h>
#include <string>
#include <vector>
#ifdef RADIANCE_AUDIT_RENDERDOC_CAPTURE
#include <renderdoc_app.h>
#endif

#ifdef RADIANCE_AUDIT_RENDERDOC_CAPTURE
static RENDERDOC_API_1_6_0 *renderDocApi = nullptr;
static uint32_t renderDocCapturesBefore = 0;
static bool resolveRenderDoc() {
    if (renderDocApi) return true;
    HMODULE module = GetModuleHandleW(L"renderdoc.dll"); // Borrowed Vulkan capture layer; never LoadLibrary.
    if (!module) return false;
    auto getApi = reinterpret_cast<pRENDERDOC_GetAPI>(GetProcAddress(module, "RENDERDOC_GetAPI"));
    return getApi && getApi(eRENDERDOC_API_Version_1_6_0,
        reinterpret_cast<void **>(&renderDocApi)) == 1 && renderDocApi;
}
#endif

extern "C" JNIEXPORT jstring JNICALL Java_com_radiance_audit_NativeDiagnostics_queryRenderDocStatus(
    JNIEnv *env, jclass) {
#ifdef RADIANCE_AUDIT_RENDERDOC_CAPTURE
    if (!resolveRenderDoc()) return env->NewStringUTF("capture-layer-or-API-unavailable");
    int major = 0, minor = 0, patch = 0;
    renderDocApi->GetAPIVersion(&major, &minor, &patch);
    std::string status = "ready-api-" + std::to_string(major) + "."
        + std::to_string(minor) + "." + std::to_string(patch);
    return env->NewStringUTF(status.c_str());
#else
    return env->NewStringUTF("capture-not-compiled");
#endif
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_radiance_audit_NativeDiagnostics_beginRenderDocCapture(
    JNIEnv *env, jclass, jstring templatePath) {
#ifdef RADIANCE_AUDIT_RENDERDOC_CAPTURE
    if (!resolveRenderDoc() || !templatePath || renderDocApi->IsFrameCapturing()) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(templatePath, nullptr);
    if (!path) return JNI_FALSE;
    renderDocCapturesBefore = renderDocApi->GetNumCaptures();
    renderDocApi->SetCaptureFilePathTemplate(path);
    env->ReleaseStringUTFChars(templatePath, path);
    renderDocApi->StartFrameCapture(nullptr, nullptr); // One Vulkan device/window in this isolated menu process.
    return renderDocApi->IsFrameCapturing() ? JNI_TRUE : JNI_FALSE;
#else
    (void)env; (void)templatePath;
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL Java_com_radiance_audit_NativeDiagnostics_endRenderDocCapture(
    JNIEnv *env, jclass) {
#ifdef RADIANCE_AUDIT_RENDERDOC_CAPTURE
    if (!renderDocApi || !renderDocApi->IsFrameCapturing()
        || !renderDocApi->EndFrameCapture(nullptr, nullptr)) return nullptr;
    const uint32_t count = renderDocApi->GetNumCaptures();
    if (count <= renderDocCapturesBefore) return nullptr;
    uint32_t length = 0;
    if (!renderDocApi->GetCapture(count - 1, nullptr, &length, nullptr) || length == 0
        || length > 32768) return nullptr;
    std::vector<char> path(length + 1, '\0');
    if (!renderDocApi->GetCapture(count - 1, path.data(), &length, nullptr)) return nullptr;
    return env->NewStringUTF(path.data());
#else
    (void)env;
    return nullptr;
#endif
}

static McvrProfileFrame profileFrame = nullptr;
extern "C" JNIEXPORT jboolean JNICALL Java_com_radiance_audit_NativeDiagnostics_installProfile(JNIEnv *, jclass) {
    HMODULE core = GetModuleHandleW(L"core.dll");
    if (!core) return JNI_FALSE;
    auto install = reinterpret_cast<McvrInstallProfileSink>(GetProcAddress(core, "mcvrInstallProfileSink"));
    auto frame = reinterpret_cast<McvrProfileFrame>(GetProcAddress(core, "mcvrProfileFrame"));
    if (!install || !frame) return JNI_FALSE;
    static const McvrProfileSink sink{MCVR_PROFILE_ABI, sizeof(McvrProfileSink), radiance::audit::profile::sample};
    if (!install(&sink)) return JNI_FALSE;
    profileFrame = frame;
    return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL Java_com_radiance_audit_NativeDiagnostics_profileFrame(JNIEnv *, jclass, jlong frame, jboolean active) {
    if (profileFrame) profileFrame(static_cast<uint64_t>(frame), active);
}
extern "C" JNIEXPORT jstring JNICALL Java_com_radiance_audit_NativeDiagnostics_drainProfile(JNIEnv *env, jclass) {
    try {
        auto text = radiance::audit::profile::queue().drain();
        return text.empty() ? nullptr : env->NewStringUTF(text.c_str());
    } catch (...) { ++mcvr::diag::dropped; return nullptr; }
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_radiance_audit_NativeDiagnostics_install(
    JNIEnv *, jclass, jint flags) {
    static McvrAuditSink sink{MCVR_AUDIT_ABI, sizeof(McvrAuditSink), 0,
        radiance::audit::allocation, radiance::audit::timing,
        radiance::audit::wantsFrame, radiance::audit::frame};
    static bool installed = false;
    if (installed) return sink.flags == static_cast<uint32_t>(flags);
    HMODULE core = GetModuleHandleW(L"core.dll"); // Borrowed, never FreeLibrary.
    if (!core) return JNI_FALSE;
    auto install = reinterpret_cast<McvrInstallAuditSink>(GetProcAddress(core, "mcvrInstallAuditSink"));
    if (!install) return JNI_FALSE;
    HMODULE pinned = nullptr;
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_PIN,
            reinterpret_cast<LPCWSTR>(&Java_com_radiance_audit_NativeDiagnostics_install), &pinned))
        return JNI_FALSE;
    sink.flags = flags;
    installed = install(&sink) != 0;
    return installed ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jstring JNICALL Java_com_radiance_audit_NativeDiagnostics_drain(
    JNIEnv *env, jclass) {
    try {
        auto text = radiance::audit::drain();
        return text.empty() ? nullptr : env->NewStringUTF(text.c_str());
    } catch (...) {
        // Collection failed, not the renderer. Return no report and expose the drop count next time.
        ++mcvr::diag::dropped;
        return nullptr;
    }
}
