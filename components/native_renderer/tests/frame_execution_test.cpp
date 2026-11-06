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

static std::array<uint64_t, 4> handoffTimings{};
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
    else if (name == "frame-worker.worker-wake")
        handoffTimings[3] = inclusive;
    else
        return;
    ++handoffSamples;
}

int main() {
#ifdef _WIN32
    _putenv_s("RADIANCE_NATIVE_FRAME_WORKER", "1");
    _putenv_s("RADIANCE_A_OWNERSHIP_CHECK", "1");
#else
    setenv("RADIANCE_NATIVE_FRAME_WORKER", "1", 1);
    setenv("RADIANCE_A_OWNERSHIP_CHECK", "1", 1);
#endif
    using namespace std::chrono_literals;
    auto &execution = mcvr::FrameExecution::instance();
    std::latch recording(1), complete(1), entering(1);
    int currentSlot = 0, otherSlot = 1;
    const auto owner = std::this_thread::get_id();
    static const McvrProfileSink sink{MCVR_PROFILE_ABI, sizeof(McvrProfileSink), observe};
    require(mcvr::profile::install(&sink));
    mcvr::profile::beginFrame(17, MCVR_PROFILE_TIMING);
    execution.dispatch(&currentSlot, [&] {
        recording.count_down();
        complete.wait();
        require(std::this_thread::get_id() != owner);
        require(execution.workerRecordingSlot() == &currentSlot);
        execution.checkAccess("submitted frame", &currentSlot);
        try {
            execution.checkAccess("another frame", &otherSlot);
        } catch (const std::logic_error &) { return 42; }
        require(false);
        return 42;
    });
    recording.wait();
    require(execution.workerRecordingSlot() == nullptr);
    bool rejected = false;
    try {
        execution.checkAccess("sealed frame", &currentSlot);
    } catch (const std::logic_error &) { rejected = true; }
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
    require(rejected && handoffSamples == 4);
    require(handoffTimings[1] > 0 && handoffTimings[1] <= handoffTimings[2]);
    require(handoffTimings[3] > 0);
    require(producer.get() == 7);
    require(!execution.consume());
    execution.checkAccess("reclaimed frame", &currentSlot);
    mcvr::profile::beginFrame(0, 0);
    execution.dispatch(&currentSlot, []() -> int { throw std::invalid_argument("recording failed"); });
    try {
        (void)execution.consume();
        require(false);
    } catch (const std::invalid_argument &) {}
    execution.dispatch(&currentSlot, [] { return 0; });
    require(execution.consume() == 0);
}
