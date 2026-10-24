#pragma once

#include <chrono>
#include <condition_variable>
#include <exception>
#include <functional>
#include <mutex>
#include <thread>
#include <stdexcept>
#include <type_traits>

namespace mcvr {
// The owner joins outside its publication lock. No input or Vulkan pool may be
// destroyed until stop returns, including when thread creation or a step fails.
class ChunkBuildWorker {
  public:
    enum class State { stop, idle, busy };
    using Step = std::function<State(std::stop_token)>;
    using Factory = std::jthread (*)(std::function<void(std::stop_token)>);

  private:
    std::mutex mutex_;
    std::condition_variable ready_;
    std::jthread thread_;
    bool requested_ = false;

  public:
    static std::jthread launch(std::function<void(std::stop_token)> operation) {
        return std::jthread(std::move(operation));
    }
    ~ChunkBuildWorker() {
        stop();
    }
    bool running() const {
        return thread_.joinable();
    }
    template <class Failure>
    void start(Step step, Failure failure, Factory factory = launch) {
        static_assert(std::is_nothrow_invocable_v<Failure, std::exception_ptr>);
        if (running()) return;
        if (!step || !factory) throw std::invalid_argument("Empty chunk worker callback");
        {
            std::lock_guard lock(mutex_);
            requested_ = false;
        }
        thread_ = factory([this, step = std::move(step), failure = std::move(failure)](std::stop_token stop) {
            try {
                while (!stop.stop_requested()) {
                    auto state = step(stop);
                    if (state == State::stop) return;
                    std::unique_lock lock(mutex_);
                    auto wake = [&] { return stop.stop_requested() || requested_; };
                    if (state == State::busy)
                        ready_.wait_for(lock, std::chrono::milliseconds(1), wake);
                    else
                        ready_.wait(lock, wake);
                    requested_ = false;
                }
            } catch (...) { failure(std::current_exception()); }
        });
    }
    void notify() {
        {
            std::lock_guard lock(mutex_);
            requested_ = true;
        }
        ready_.notify_one();
    }
    void stop() {
        if (!running()) return;
        {
            // Pair the stop predicate with the idle wait mutex. Otherwise stop
            // can notify between its predicate check and the actual wait.
            std::lock_guard lock(mutex_);
            thread_.request_stop();
            requested_ = true;
        }
        ready_.notify_all();
        thread_.join();
    }
};
} // namespace mcvr
