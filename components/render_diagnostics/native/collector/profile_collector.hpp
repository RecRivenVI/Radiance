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
    // Rigid-model recording can emit 19,576 events between two frame drains.
    // Keep a fixed headroom bound; overflow must still invalidate the sample.
    std::array<Event, 32768> events{};
    std::array<Event, 32768> snapshot{};
    std::array<std::atomic_bool, 32768> ready{};
    std::atomic<int> available{32768};
    std::atomic<uint64_t> written{0};
    uint64_t read = 0;
    std::mutex reader;
    std::atomic<uint64_t> dropped{0};
    void add(uint64_t owner, uint32_t domain, const char *label, uint64_t inclusive, uint64_t self) noexcept {
        // Reserve capacity before a ticket: full queues cannot leave holes, and
        // producers never wait for another worker or the render-thread drain.
        if (available.fetch_sub(1, std::memory_order_acquire) <= 0) {
            available.fetch_add(1, std::memory_order_release);
            ++dropped;
            return;
        }
        const auto slot = written.fetch_add(1, std::memory_order_relaxed) % events.size();
        {
            auto &event = events[slot];
            event.frame = owner; event.domain = domain; event.inclusive = inclusive; event.self = self;
            size_t i = 0;
            for (; label && label[i] && i < sizeof(event.label)-1; ++i) {
                const char c = label[i];
                event.label[i] = c == ',' || c == '\n' || c == '\r' ? '_' : c;
            }
            event.label[i] = 0;
        }
        ready[slot].store(true, std::memory_order_release);
    }
    std::string drain() {
        // Serialization cannot hold up worker producers. Both arrays are fixed
        // process-lived storage; there is no per-frame snapshot allocation.
        std::unique_lock consumer(reader, std::try_to_lock);
        if (!consumer.owns_lock()) return {};
        size_t length = 0;
        while (length < events.size()) {
            const auto slot = read % events.size();
            // A preempted producer retains its ticket. Read a completed prefix
            // only; a later drain can consume the rest without spinning here.
            if (!ready[slot].load(std::memory_order_acquire)) break;
            snapshot[length++] = events[slot];
            ready[slot].store(false, std::memory_order_relaxed);
            ++read;
        }
        available.fetch_add(static_cast<int>(length), std::memory_order_release);
        const auto loss = dropped.exchange(0);
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
