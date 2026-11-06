#include "core/diagnostics/frame_profile.hpp"
#include "core/diagnostics/table_delta.hpp"
#include <stdexcept>
#include <vector>
#include <string>
#include <thread>
#include <mutex>

struct Sample {
    uint64_t owner, inclusive, self;
    uint32_t domain;
    std::string label;
};
static std::mutex mutex;
static std::vector<Sample> samples;
static uint64_t timeNs;
static uint64_t clockNs() noexcept {
    return timeNs;
}
static void sample(uint64_t owner, uint32_t domain, const char *label, uint64_t inclusive, uint64_t self) {
    std::lock_guard lock(mutex);
    samples.push_back({owner, inclusive, self, domain, label});
}
static void require(bool condition) {
    if (!condition) throw std::runtime_error("profile contract");
}
int main() {
    {
        const std::vector<uint32_t> a{1, 2, 3, 4}, b{1, 9, 3, 4, 5};
        const auto delta = mcvr::profile::tableDelta(mcvr::sceneTable<uint32_t>(a), mcvr::sceneTable<uint32_t>(b), 4);
        require(delta.before == 4 && delta.after == 5 && delta.changed == 1 && delta.prefix == 1 && delta.suffix == 2);
        // Segment boundaries may split records. Count a changed record exactly once.
        auto raw = std::as_bytes(std::span(b));
        mcvr::SceneTable split{raw.first(5), {}, raw.subspan(5)};
        const auto segmented = mcvr::profile::tableDelta(mcvr::sceneTable<uint32_t>(a), split, 4);
        require(segmented.changed == 1 && segmented.prefix == 1 && segmented.suffix == 2);
        const auto shorter = mcvr::profile::tableDelta(mcvr::sceneTable<uint32_t>(b), mcvr::sceneTable<uint32_t>(a), 4);
        require(shorter.changed == 1 && shorter.before - shorter.after == 1);
        require(mcvr::profile::tableDelta({}, mcvr::sceneTable<uint32_t>(a), 4).after == 4);
        const auto same = mcvr::profile::tableDelta(split, split, 4);
        require(same.changed == 0 && same.prefix == 5 && same.suffix == 5);
    }
    const McvrProfileSink observer{MCVR_PROFILE_ABI, sizeof(McvrProfileSink), sample};
    require(mcvr::profile::install(&observer));
    const McvrProfileSink stale{1, sizeof(McvrProfileSink), sample};
    require(!mcvr::profile::install(&stale));
    mcvr::profile::beginFrame(70, MCVR_PROFILE_TIMING);
    require(mcvr::profile::enabled() && !mcvr::profile::contentAuditEnabled() &&
            !mcvr::profile::residencyCensusEnabled());
    require(!mcvr::profile::flowAuditEnabled());
    mcvr::profile::beginFrame(70, MCVR_PROFILE_TIMING | MCVR_PROFILE_FLOW_AUDIT);
    require(mcvr::profile::flowAuditEnabled() && !mcvr::profile::residencyCensusEnabled());
    mcvr::profile::beginFrame(70, MCVR_PROFILE_TIMING | MCVR_PROFILE_CONTENT_AUDIT | MCVR_PROFILE_RESIDENCY_CENSUS);
    require(mcvr::profile::contentAuditEnabled() && mcvr::profile::residencyCensusEnabled());
    mcvr::profile::beginFrame(70, MCVR_PROFILE_CONTENT_AUDIT);
    require(!mcvr::profile::enabled() && !mcvr::profile::contentAuditEnabled());
    { mcvr::profile::Scope disabled("disabled", clockNs); }
    require(samples.empty());
    mcvr::profile::active = true;
    mcvr::profile::frame = 71;
    try {
        mcvr::profile::Scope root("root", clockNs);
        timeNs = 10;
        {
            mcvr::profile::Scope child("child", clockNs);
            timeNs = 35;
        }
        timeNs = 50;
        throw std::runtime_error("task failure");
    } catch (const std::runtime_error &) {}
    require(samples.size() == 2 && samples[0].inclusive == 25 && samples[1].inclusive == 50 && samples[1].self == 25);
    require(samples[0].owner == 71 && samples[1].domain == 0);
    std::thread worker([] { mcvr::profile::Scope span("worker"); });
    worker.join();
    require(samples.back().owner == 0 && samples.back().domain == 1);
    {
        mcvr::profile::Scope independent("after-exception", clockNs);
        timeNs = 60;
    }
    require(samples.back().self == 10);
    // Phase transitions must partition their parent, including nested work and
    // exception unwinding. They must not retain a pointer to a destroyed scope.
    samples.clear();
    timeNs = 0;
    try {
        mcvr::profile::Scope root("phased-parent", clockNs);
        mcvr::profile::Phases phases("prepare", clockNs);
        timeNs = 10;
        {
            mcvr::profile::Scope nested("nested", clockNs);
            timeNs = 30;
        }
        timeNs = 40;
        phases.next("record");
        timeNs = 65;
        throw std::runtime_error("original");
    } catch (const std::runtime_error &error) { require(std::string(error.what()) == "original"); }
    require(samples.size() == 4);
    require(samples[1].label == "prepare" && samples[1].inclusive == 40 && samples[1].self == 20);
    require(samples[2].label == "record" && samples[2].self == 25);
    require(samples[3].inclusive == 65 && samples[3].self == 0);
    require(samples[0].self + samples[1].self + samples[2].self + samples[3].self == 65);
    samples.clear();
    timeNs = 0;
    {
        mcvr::profile::Scope root("aggregated-parent", clockNs);
        mcvr::profile::Accumulated format("format"), topology("topology");
        for (int i = 0; i < 3; ++i) {
            mcvr::profile::Phases phases(format, clockNs);
            timeNs += 10;
            phases.next(topology);
            timeNs += 20;
        }
        require(samples.empty()); // No per-geometry observer calls.
    }
    require(samples.size() == 3 && samples[0].label == "topology" && samples[0].self == 60);
    require(samples[1].label == "format" && samples[1].self == 30);
    require(samples[2].inclusive == 90 && samples[2].self == 0);
    samples.clear();
    {
        mcvr::profile::Accumulated stale("stale");
        {
            mcvr::profile::Scope span(stale, clockNs);
            timeNs += 10;
        }
        ++mcvr::profile::epoch;
    }
    require(samples.empty());
    mcvr::profile::active = false;
    const auto count = samples.size();
    {
        mcvr::profile::Phases phases("disabled", clockNs);
        timeNs = 90;
        phases.next("also-disabled");
    }
    require(samples.size() == count);
    samples.clear();
    mcvr::profile::beginFrame(72, MCVR_PROFILE_TIMING);
    { mcvr::profile::AllocationScope dormant("allocation", 1024, clockNs); }
    require(samples.empty());
    mcvr::profile::beginFrame(73, MCVR_PROFILE_TIMING | MCVR_PROFILE_ALLOCATION_TIMING);
    timeNs = 0;
    {
        mcvr::profile::Scope parent("parent", clockNs);
        {
            mcvr::profile::AllocationScope measured("allocation", 4096, clockNs);
            timeNs = 20;
        }
        timeNs = 30;
    }
    require(samples.size() == 3 && samples[0].domain == 5 && samples[0].inclusive == 1 && samples[0].self == 4096);
    require(samples[1].domain == 0 && samples[1].inclusive == 20 && samples[2].self == 10);
}
