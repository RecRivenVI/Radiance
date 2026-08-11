#include <utility>
#include "collector.hpp"
#include <stdexcept>
int main() {
    using namespace radiance::audit;
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
