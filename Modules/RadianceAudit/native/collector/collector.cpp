#include <utility>
#include "collector.hpp"
#include "profile_collector.hpp"
#include <jni.h>
#include <windows.h>

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
