#pragma once
#include "profile_api.h"
#include <array>
#include <atomic>
#include <cstring>
#include <mutex>
#include <sstream>

namespace radiance::audit::profile {
struct Event { uint64_t frame, inclusive, self; uint32_t domain; char label[80]; };
struct Queue {
    std::mutex mutex;
    std::array<Event, 8192> events{};
    size_t count = 0;
    std::atomic<uint64_t> dropped{0};
    void add(uint64_t owner, uint32_t domain, const char *label, uint64_t inclusive, uint64_t self) noexcept {
        try {
            std::lock_guard lock(mutex);
            if (count == events.size()) { ++dropped; return; }
            auto &event = events[count++];
            event.frame = owner; event.domain = domain; event.inclusive = inclusive; event.self = self;
            size_t i = 0;
            for (; label && label[i] && i < sizeof(event.label)-1; ++i) {
                const char c = label[i];
                event.label[i] = c == ',' || c == '\n' || c == '\r' ? '_' : c;
            }
            event.label[i] = 0;
        } catch (...) { ++dropped; }
    }
    std::string drain() {
        std::lock_guard lock(mutex);
        std::ostringstream out;
        for (size_t i = 0; i < count; ++i) {
            const auto &e = events[i];
            out << e.frame << ',' << e.domain << ',' << e.label << ',' << e.inclusive << ',' << e.self << '\n';
        }
        if (auto loss = dropped.exchange(0)) out << "0,9,dropped," << loss << ",0\n";
        count = 0;
        return out.str();
    }
};
inline Queue &queue() { static auto *value = new Queue; return *value; }
inline void sample(uint64_t owner, uint32_t domain, const char *label, uint64_t inclusive, uint64_t self) noexcept {
    try { queue().add(owner, domain, label, inclusive, self); } catch (...) { /* No renderer failure from optional observer. */ }
}
}
