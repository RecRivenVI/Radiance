#include "audit_sink.hpp"
#include "frame_profile.hpp"
#include "state_bridge_audit.hpp"
#ifdef _WIN32
#    define AUDIT_EXPORT extern "C" __declspec(dllexport)
#else
#    define AUDIT_EXPORT extern "C" __attribute__((visibility("default")))
#endif
AUDIT_EXPORT int mcvrInstallAuditSink(const McvrAuditSink *sink) noexcept {
    return mcvr::audit::install(sink) ? 1 : 0;
}
AUDIT_EXPORT int mcvrInstallProfileSink(const McvrProfileSink *sink) noexcept {
    return mcvr::profile::install(sink) ? 1 : 0;
}
AUDIT_EXPORT void mcvrProfileFrame(uint64_t frame, int flags) noexcept {
    mcvr::state_audit::flush();
    mcvr::profile::beginFrame(frame, static_cast<uint32_t>(flags));
}
