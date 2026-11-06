#include "core/diagnostics/frame_profile.hpp"
#include "core/render/frame_execution.hpp"

#include <array>
#include <chrono>
#include <cstdlib>
#include <future>
#include <latch>
#include <stdexcept>
#include <string_view>

static void require(bool value) {
    if (!value) throw std::runtime_error("Native frame execution contract failed");
}

static std::array<uint64_t, 3> handoffTimings{};
static unsigned handoffSamples = 0;
static void observe(uint64_t frame, uint32_t domain, const char *label, uint64_t inclusive, uint64_t) noexcept {
    if (frame != 17 || domain != 0) return;
    const std::string_view name = label;
    if (name == "frame-worker.producer-blocked")
        handoffTimings[0] = inclusive;
    else if (name == "frame-worker.overlap-window")
        handoffTimings[1] = inclusive;
    else if (name == "frame-worker.execute")
        handoffTimings[2] = inclusive;
    else
        return;
    ++handoffSamples;
}

int main() {
#ifdef _WIN32
    _putenv_s("RADIANCE_NATIVE_FRAME_WORKER", "1");
#else
    setenv("RADIANCE_NATIVE_FRAME_WORKER", "1", 1);
#endif
    using namespace std::chrono_literals;
    auto &execution = mcvr::FrameExecution::instance();
    std::latch recording(1), complete(1), entering(1);
    const auto owner = std::this_thread::get_id();
    static const McvrProfileSink sink{MCVR_PROFILE_ABI, sizeof(McvrProfileSink), observe};
    require(mcvr::profile::install(&sink));
    mcvr::profile::beginFrame(17, MCVR_PROFILE_TIMING);
    execution.dispatch([&] {
        recording.count_down();
        complete.wait();
        require(std::this_thread::get_id() != owner);
        return 42;
    });
    recording.wait();
    auto producer = std::async(std::launch::async, [&] {
        entering.count_down();
        mcvr::FrameExecution::Producer outer(true), nested(true);
        return 7;
    });
    entering.wait();
    const auto blocked = producer.wait_for(25ms);
    complete.count_down();
    require(blocked == std::future_status::timeout);
    require(execution.consume() == 42);
    require(handoffSamples == 3);
    require(handoffTimings[1] > 0 && handoffTimings[1] <= handoffTimings[2]);
    require(producer.get() == 7);
    require(!execution.consume());
    mcvr::profile::beginFrame(0, 0);
    execution.dispatch([]() -> int { throw std::invalid_argument("recording failed"); });
    try {
        (void)execution.consume();
        require(false);
    } catch (const std::invalid_argument &) {}
    execution.dispatch([] { return 0; });
    require(execution.consume() == 0);
}
