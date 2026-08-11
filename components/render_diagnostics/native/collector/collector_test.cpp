#include <utility>
#include "collector.hpp"
#include "audit_sink.hpp"
#include <stdexcept>
int main() {
    using namespace radiance::audit;
    static const McvrAuditSink timingOnly{MCVR_AUDIT_ABI, sizeof(McvrAuditSink), MCVR_AUDIT_TIMING,
        allocation, timing, wantsFrame, frame};
    if (!mcvr::audit::install(&timingOnly)
        || !mcvr::audit::enabled(MCVR_AUDIT_ALLOCATIONS | MCVR_AUDIT_TIMING)
        || mcvr::audit::enabled(MCVR_AUDIT_ALLOCATIONS))
        throw std::runtime_error("timing telemetry must not require allocation tags");
    timing(1, 1);
    McvrAuditFrame basic{1024, 3, 1920, 1080, 3, 0};
    mcvr::audit::sink.load()->frame(&basic);
    auto basicReport = drain();
    if (basicReport.find("vma-bytes=1024") == std::string::npos
        || basicReport.find("metric1-ms=1/1/1") == std::string::npos
        || !mcvr::diag::allocTraceStats().empty())
        throw std::runtime_error("basic frame metrics lost without allocation maps");
    allocation(1, "buffer", 1024, "test");
    allocation(1, "buffer", 256, "test");
    allocation(0, "buffer", 1024, "test");
    allocation(0, "buffer", 500, "pre-attach");
    timing(1, 2); timing(1, 4); timing(99, 1);
    McvrAuditFrame value{256, 1, 1280, 720, 3, 0};
    frame(&value);
    auto report = drain();
    if (report.find("metric1-ms=3/4/2") == std::string::npos
        || report.find("vma-bytes=256") == std::string::npos || !drain().empty())
        throw std::runtime_error("collector snapshot mismatch");
    if (mcvr::diag::allocTraceStats().at("buffer@test").liveBytes != 256)
        throw std::runtime_error("allocation lifetime mismatch");
    frame(&value); frame(&value);
    if (drain().find("dropped-reports=1") == std::string::npos)
        throw std::runtime_error("bounded snapshot did not account overwrite");
}
