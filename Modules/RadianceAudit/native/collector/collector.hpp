#pragma once
#include "audit_api.h"
#include "allocation_stats.hpp"
#include <array>
#include <chrono>
#include <mutex>
#include <sstream>
#include <string>
#include <utility>

namespace radiance::audit {
struct Timing { double sum = 0, maximum = 0; uint64_t count = 0; };
// Allocated on first use inside a protected callback; process-lived for late destruction.
struct CollectorState {
    std::mutex mutex;
    std::array<Timing, 13> timings{};
    std::string pending;
    uint64_t droppedReports = 0;
    std::chrono::steady_clock::time_point previous = std::chrono::steady_clock::now();
    uint64_t presents = 0;
    double seconds = 0;
};
inline CollectorState &state() { static auto *value = new CollectorState; return *value; }
inline void allocation(int created, const char *kind, uint64_t bytes, const char *tag) noexcept {
    if (!kind || !tag) return;
    if (created) {
        mcvr::diag::AllocTraceTagScope scope(tag);
        mcvr::diag::recordAllocCreate(kind, bytes);
    } else mcvr::diag::recordAllocDestroy(kind, bytes, tag);
}
inline void timing(uint32_t metric, double milliseconds) noexcept {
    try {
        auto &s = state();
        std::lock_guard lock(s.mutex);
        if (metric >= s.timings.size()) return;
        auto &value = s.timings[metric];
        value.sum += milliseconds;
        value.maximum = std::max(value.maximum, milliseconds);
        ++value.count;
    } catch (...) { ++mcvr::diag::dropped; }
}
inline int wantsFrame() noexcept {
    try {
        auto &s = state();
        // Called only on the presentation owner thread.
        ++s.presents;
        auto now = std::chrono::steady_clock::now();
        s.seconds = std::chrono::duration<double>(now - s.previous).count();
        if (s.seconds < 1) return 0;
        s.previous = now;
        return 1;
    } catch (...) { ++mcvr::diag::dropped; return 0; }
}
inline void frame(const McvrAuditFrame *value) noexcept {
    if (!value) return;
    try {
        const auto allocations = mcvr::diag::allocTraceSummaryPerSecond();
        auto &s = state();
        std::lock_guard lock(s.mutex);
        if (!s.pending.empty()) ++s.droppedReports;
        std::ostringstream output;
        output << "native-present-calls=" << s.presents << " window-s=" << s.seconds
               << " vma-bytes=" << value->allocationBytes << " vma-count=" << value->allocationCount
               << " extent=" << value->width << 'x' << value->height
               << " result=" << value->presentResult << " dropped-reports=" << s.droppedReports
               << " dropped-events=" << mcvr::diag::dropped.load() << ' ' << allocations;
        for (size_t i = 0; i < s.timings.size(); ++i) {
            const auto &t = s.timings[i];
            if (t.count) output << " metric" << i << "-ms=" << t.sum/t.count << '/' << t.maximum << '/' << t.count;
        }
        s.pending = output.str(); // Single bounded snapshot, never an unbounded event queue.
        s.timings = {};
        s.presents = 0;
    } catch (...) { ++mcvr::diag::dropped; }
}
inline std::string drain() {
    auto &s = state();
    std::lock_guard lock(s.mutex);
    return std::exchange(s.pending, {});
}
}
