#pragma once
#include <chrono>
#include <deque>
#include <optional>

namespace mcvr::view {
// One clock per SDK view, independent of swapchain slot reuse.
class FrameClock {
  public:
    using Clock = std::chrono::steady_clock;
    float sample(Clock::time_point now) {
        if (!last_) {
            last_ = now;
            return 16.67f;
        }
        float delta = std::chrono::duration<float, std::milli>(now - *last_).count();
        last_ = now;
        if (delta < 1.0f || delta > 100.0f) delta = 16.67f;
        samples_.push_back(delta);
        if (samples_.size() > 10) samples_.pop_front();
        float sum = 0.0f;
        for (float value : samples_) sum += value;
        return sum / samples_.size();
    }
    void reset() {
        last_.reset();
        samples_.clear();
    }

  private:
    std::optional<Clock::time_point> last_;
    std::deque<float> samples_;
};
} // namespace mcvr::view
