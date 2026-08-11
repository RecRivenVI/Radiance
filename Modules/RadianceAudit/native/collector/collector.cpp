#include <utility>
#include "collector.hpp"
#include <jni.h>
#include <windows.h>

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
