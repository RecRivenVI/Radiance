#pragma once
#include "profile_api.h"
#include <array>
#include <algorithm>
#include <atomic>
#include <charconv>
#include <cstring>
#include <mutex>
#include <string>

namespace radiance::audit::profile {
struct Event { uint64_t frame, inclusive, self; uint32_t domain; char label[80]; };
struct Queue {
    std::mutex mutex;
    std::array<Event, 8192> events{};
    std::array<Event, 8192> snapshot{};
    std::mutex reader;
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
        // Serialization cannot hold up worker producers. Both arrays are fixed
        // process-lived storage; there is no per-frame snapshot allocation.
        std::lock_guard consumer(reader);
        size_t length;
        uint64_t loss;
        {
            std::lock_guard lock(mutex);
            length = count;
            std::copy_n(events.begin(), length, snapshot.begin());
            count = 0;
            loss = dropped.exchange(0);
        }
        std::string out;
        out.reserve(length * 128);
        auto number = [&](uint64_t value) {
            char buffer[32];
            const auto result = std::to_chars(buffer, buffer + sizeof(buffer), value);
            out.append(buffer, result.ptr);
        };
        for (size_t i = 0; i < length; ++i) {
            const auto &e = snapshot[i];
            number(e.frame); out += ',';
            number(e.domain); out += ','; out += e.label; out += ',';
            number(e.inclusive); out += ','; number(e.self); out += '\n';
        }
        if (loss) { out += "0,9,dropped,"; number(loss); out += ",0\n"; }
        return out;
    }
};
inline Queue &queue() { static auto *value = new Queue; return *value; }
inline void sample(uint64_t owner, uint32_t domain, const char *label, uint64_t inclusive, uint64_t self) noexcept {
    try { queue().add(owner, domain, label, inclusive, self); } catch (...) { /* No renderer failure from optional observer. */ }
}
}
