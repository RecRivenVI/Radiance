#pragma once

#include "core/diagnostics/device_loss_trace.hpp"
#include <mutex>

namespace mcvr::diagnostics::as_lifetime {
// CPU ownership/recording evidence only; never represents completed GPU work.
enum class Kind : uint32_t { create, destroy, instance, build };
struct Event {
    uint64_t sequence{}, object{}, address{}, related{}, detail{};
    Kind kind{};
};
inline constexpr size_t capacity = 131072;
inline std::array<Event, capacity> events{};
inline uint64_t nextSequence = 0;
inline std::mutex mutex;

inline bool membershipEnabled() noexcept {
    if (!device_loss::enabled()) return false;
    // Per-instance membership is a heavier, separately enabled investigation.
    static const bool membership = [] {
        const auto *v = std::getenv("MCVR_AS_MEMBERSHIP_TRACE");
        return v && std::string_view(v) == "1";
    }();
    return membership;
}

inline void note(Kind kind, uint64_t object, uint64_t address, uint64_t related = 0, uint64_t detail = 0) noexcept {
    if (!device_loss::enabled() || (kind == Kind::instance && !membershipEnabled())) return;
    try {
        std::lock_guard lock(mutex);
        const auto sequence = nextSequence++;
        events[sequence % capacity] = {sequence, object, address, related, detail, kind};
    } catch (...) { /* Never interfere with resource destruction/fatal publication. */
    }
}

inline void dump(const char *path) noexcept {
    if (!device_loss::enabled()) return;
    try {
        std::lock_guard lock(mutex);
        std::ofstream out(path, std::ios::trunc);
        const auto begin = nextSequence > capacity ? nextSequence - capacity : 0;
        out << "CPU_ONLY begin=" << begin << " end=" << nextSequence << " kind:0=create,1=destroy,2=instance,3=build\n";
        for (auto i = begin; i < nextSequence; ++i) {
            const auto &e = events[i % capacity];
            out << std::dec << e.sequence << ' ' << unsigned(e.kind) << ' ' << std::hex << e.object << ' ' << e.address
                << ' ' << e.related << ' ' << e.detail << '\n';
        }
    } catch (...) { /* Diagnostic failure cannot replace the original error. */
    }
}
} // namespace mcvr::diagnostics::as_lifetime
