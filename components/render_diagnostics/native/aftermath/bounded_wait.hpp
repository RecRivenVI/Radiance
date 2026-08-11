#pragma once
#include <algorithm>
#include <cstdint>
namespace local_capture {
struct Status { uint32_t value; bool success; };
// Polls SDK status only. Fake clocks exercise the actual deadline logic without a GPU.
template<class Poll, class Clock, class Sleep>
uint32_t wait(uint32_t requestedMs, Poll poll, Clock now, Sleep sleep) {
    const uint32_t budget = std::min(requestedMs, 5000u);
    const auto start = now();
    for (;;) {
        const auto status = poll();
        if (!status.success || status.value == 2 || status.value == 4) return status.value;
        const auto elapsed = now() - start;
        if (elapsed >= budget) return status.value;
        sleep(std::min<uint32_t>(20, budget - static_cast<uint32_t>(elapsed)));
    }
}
}
