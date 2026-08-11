#pragma once

#include <algorithm>
#include <atomic>
#include <cstdlib>
#include <chrono>
#include <condition_variable>
#include <exception>
#include <functional>
#include <mutex>
#include <thread>
#include <utility>
#include <vector>

namespace mcvr {

// Only the calling thread runs this callback. Build workers never touch window or frame state.
class ParallelWaitScope {
    inline static thread_local ParallelWaitScope *active_ = nullptr;
    ParallelWaitScope *previous_;
    std::function<void(const char *, size_t, size_t)> callback_;

  public:
    explicit ParallelWaitScope(std::function<void(const char *, size_t, size_t)> callback)
        : previous_(active_), callback_(std::move(callback)) {
        active_ = this;
    }
    ~ParallelWaitScope() {
        active_ = previous_;
    }
    ParallelWaitScope(const ParallelWaitScope &) = delete;
    ParallelWaitScope &operator=(const ParallelWaitScope &) = delete;
    static ParallelWaitScope *active() {
        return active_;
    }
    void tick(const char *phase, size_t done, size_t total) {
        auto previous = std::exchange(active_, nullptr);
        try {
            callback_(phase, done, total);
        } catch (...) {
            active_ = previous;
            throw;
        }
        active_ = previous;
    }
};

namespace detail {

    template <typename Fn, typename Launcher>
    void parallelForWithLauncher(size_t count,
                                 uint32_t threadCount,
                                 Fn &&fn,
                                 Launcher &&launcher,
                                 const char *phase = "Preparing renderer resources") {
        if (count == 0) return;
        threadCount = std::max(1u, std::min<uint32_t>(threadCount, static_cast<uint32_t>(count)));
        auto pump = ParallelWaitScope::active();
        if ((threadCount <= 1 || count == 1) && pump == nullptr) {
            for (size_t i = 0; i < count; ++i) fn(i);
            return;
        }

        std::atomic<size_t> nextIndex{0};
        std::atomic<bool> cancelled{false};
        std::atomic<size_t> completed{0};
        std::atomic<uint32_t> exited{0};
        std::condition_variable changed;
        std::mutex waitMutex;
        std::exception_ptr firstException;
        std::mutex exceptionMutex;
        std::vector<std::thread> workers;
        workers.reserve(threadCount);

        auto worker = [&]() {
            while (!cancelled.load(std::memory_order_acquire)) {
                const size_t index = nextIndex.fetch_add(1, std::memory_order_relaxed);
                if (index >= count) break;
                try {
                    fn(index);
                    completed.fetch_add(1, std::memory_order_release);
                } catch (...) {
                    {
                        std::lock_guard lock(exceptionMutex);
                        if (firstException == nullptr) firstException = std::current_exception();
                    }
                    cancelled.store(true, std::memory_order_release);
                }
            }
            exited.fetch_add(1, std::memory_order_release);
            changed.notify_one();
        };

        try {
            if (pump) pump->tick(phase, 0, count);
            for (uint32_t i = 0; i < threadCount; ++i) { workers.emplace_back(launcher(worker)); }
            while (pump && exited.load(std::memory_order_acquire) < workers.size()) {
                pump->tick(phase, completed.load(std::memory_order_acquire), count);
                std::unique_lock waitLock(waitMutex);
                changed.wait_for(waitLock, std::chrono::milliseconds(16));
            }
        } catch (...) {
            const auto creationFailure = std::current_exception();
            cancelled.store(true, std::memory_order_release);
            for (auto &thread : workers) {
                if (thread.joinable()) thread.join();
            }
            std::rethrow_exception(creationFailure);
        }

        for (auto &thread : workers) {
            if (thread.joinable()) thread.join();
        }
        if (firstException != nullptr) std::rethrow_exception(firstException);
        if (pump) pump->tick(phase, completed.load(std::memory_order_acquire), count);
    }

} // namespace detail

inline uint32_t parallelThreadCount(const char *envName = "MCVR_SHADER_PACK_BUILD_THREADS") {
    const uint32_t hardwareThreads = std::max(1u, std::thread::hardware_concurrency());
    const char *rawValue = std::getenv(envName);
    if (rawValue != nullptr && rawValue[0] != '\0') {
        char *end = nullptr;
        long parsed = std::strtol(rawValue, &end, 10);
        if (end != rawValue && parsed > 0) { return static_cast<uint32_t>(std::min<long>(parsed, hardwareThreads)); }
    }
    return std::min(8u, hardwareThreads);
}

template <typename Fn>
void parallelFor(size_t count,
                 Fn &&fn,
                 const char *envName = "MCVR_SHADER_PACK_BUILD_THREADS",
                 const char *phase = "Preparing renderer resources") {
    detail::parallelForWithLauncher(
        count, parallelThreadCount(envName), std::forward<Fn>(fn),
        [](auto worker) { return std::thread(std::move(worker)); }, phase);
}

} // namespace mcvr
