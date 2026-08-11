#include "core/diagnostics/state_bridge_audit.hpp"
#include <stdexcept>
#include <string>
#include <vector>
struct Sample {
    uint64_t owner, count, bytes;
    std::string label;
};
static std::vector<Sample> samples;
static void sample(uint64_t owner, uint32_t domain, const char *label, uint64_t count, uint64_t bytes) {
    if (domain != 5) throw std::runtime_error("not a counter");
    samples.push_back({owner, count, bytes, label});
}
static uint64_t value(const char *label, uint64_t owner) {
    uint64_t count = 0;
    for (const auto &s : samples)
        if (s.label == label && s.owner == owner) {
            if (s.bytes) throw std::runtime_error("counts must not masquerade as upload bytes");
            count += s.count;
        }
    return count;
}
static void require(bool v) {
    if (!v) throw std::runtime_error("state audit contract");
}
int main() {
    using namespace mcvr;
    const McvrProfileSink observer{MCVR_PROFILE_ABI, sizeof(McvrProfileSink), sample};
    require(profile::install(&observer));
    require(!state_audit::enabled());
    profile::beginFrame(11, MCVR_PROFILE_TIMING);
    require(state_audit::enabled());
    state_audit::record(state_audit::depthWrite, false, true);
    state_audit::record(state_audit::depthWrite, true, true);
    state_audit::context();
    state_audit::query();
    state_audit::bind(false, true);
    state_audit::flush();
    require(value("state-request/depth-write", 11) == 2 && value("state-change/depth-write", 11) == 1);
    require(value("state-idle/depth-write", 11) == 2 && value("state-bridge/context-acquisitions", 11) == 1);
    require(value("state-bridge/queries", 11) == 1 && value("state-bridge/active-pass-ends", 11) == 1);
    state_audit::flush();
    require(value("state-request/depth-write", 11) == 2);
    profile::beginFrame(12, MCVR_PROFILE_TIMING);
    state_audit::record(state_audit::cullMode, true, false);
    state_audit::flush();
    require(value("state-request/cull-mode", 12) == 1 && value("state-request/cull-mode", 11) == 0);
    profile::beginFrame(0, 0);
    require(!state_audit::enabled());
    state_audit::context();
    state_audit::query();
    state_audit::bind(true, true);
    state_audit::flush();
    require(value("state-bridge/context-acquisitions", 0) == 0);
}
