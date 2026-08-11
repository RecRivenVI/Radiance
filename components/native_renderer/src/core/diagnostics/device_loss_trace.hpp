#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <string_view>
#include <vector>
#include <vulkan/vulkan_core.h>

namespace mcvr::diagnostics::device_loss {
struct Event {
    uint64_t sequence = UINT64_MAX, observedFrame = 0;
    int64_t timeMicros = 0;
    std::array<char, 64> operation{};
    int32_t result = 0;
    uint32_t value0 = 0, value1 = 0;
    uint64_t object = 0, related = 0, bytes = 0, queue = 0;
};
struct Slot {
    std::atomic_flag busy = ATOMIC_FLAG_INIT;
    Event event;
};
inline constexpr size_t capacity = 4096;
inline std::array<Slot, capacity> events{};
inline std::atomic<uint64_t> nextSequence{0}, dropped{0}, observedFrame{0};
constexpr uint32_t passFingerprint(std::string_view name) noexcept {
    uint32_t value = 2166136261u;
    for (unsigned char byte : name) value = (value ^ byte) * 16777619u;
    return value;
}
inline bool enabled() noexcept {
    static const bool value = [] {
        const char *flag = std::getenv("MCVR_DEVICE_LOSS_TRACE");
        return flag && std::string_view(flag) == "1";
    }();
    return value;
}
// Fixed memory and per-slot try-lock: a busy wrap drops a record rather than
// blocking rendering. Owning the label prevents dangling diagnostic strings.
inline void note(std::string_view operation,
                 VkResult result = VK_SUCCESS,
                 uint32_t value0 = 0,
                 uint32_t value1 = 0,
                 uint64_t object = 0,
                 uint64_t related = 0,
                 uint64_t bytes = 0,
                 uint64_t queue = 0) noexcept {
    if (!enabled()) return;
    const auto sequence = nextSequence.fetch_add(1, std::memory_order_relaxed);
    auto &slot = events[sequence % capacity];
    if (slot.busy.test_and_set(std::memory_order_acquire)) {
        ++dropped;
        return;
    }
    Event event;
    event.sequence = sequence;
    event.observedFrame = observedFrame.load(std::memory_order_relaxed);
    event.timeMicros =
        std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now().time_since_epoch())
            .count();
    std::copy_n(operation.data(), std::min(operation.size(), event.operation.size() - 1), event.operation.data());
    event.result = int32_t(result);
    event.value0 = value0;
    event.value1 = value1;
    event.object = object;
    event.related = related;
    event.bytes = bytes;
    event.queue = queue;
    // A delayed producer must not replace a newer wrap of this slot.
    if (slot.event.sequence == UINT64_MAX || slot.event.sequence < sequence)
        slot.event = event;
    else
        ++dropped;
    slot.busy.clear(std::memory_order_release);
}
inline std::vector<Event> snapshotForTest() {
    std::vector<Event> result;
    const auto end = nextSequence.load(std::memory_order_acquire);
    const auto begin = end > capacity ? end - capacity : 0;
    result.reserve(size_t(end - begin));
    for (auto sequence = begin; sequence < end; ++sequence) {
        auto &slot = events[sequence % capacity];
        if (slot.busy.test_and_set(std::memory_order_acquire)) continue;
        const auto event = slot.event;
        slot.busy.clear(std::memory_order_release);
        if (event.sequence == sequence) result.push_back(event);
    }
    return result;
}
inline void dump(const char *cause, VkResult result) noexcept {
    if (!enabled()) return;
    try {
        note(cause, result);
        const auto snapshot = snapshotForTest();
        std::ofstream out("radiance-device-loss-trace.log", std::ios::app);
        out << "BEGIN CPU_ONLY cause=" << cause << " result=" << int32_t(result) << " retained=" << snapshot.size()
            << " dropped=" << dropped.load() << '\n';
        for (const auto &e : snapshot)
            out << e.sequence << ' ' << e.timeMicros << " observedFrame=" << e.observedFrame << ' '
                << e.operation.data() << " result=" << e.result << " value0=" << e.value0 << " value1=" << e.value1
                << " object=" << e.object << " related=" << e.related << " bytes=" << e.bytes << " queue=" << e.queue
                << '\n';
        out << "END\n";
    } catch (...) { /* Optional I/O cannot replace the published fatal. */
    }
}
inline void resetForTest() noexcept {
    // Quiescent tests only.
    nextSequence = 0;
    dropped = 0;
    for (auto &slot : events) {
        slot.event = Event{};
        slot.busy.clear();
    }
}
} // namespace mcvr::diagnostics::device_loss
