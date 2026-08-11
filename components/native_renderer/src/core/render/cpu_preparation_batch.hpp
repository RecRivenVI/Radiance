#pragma once

#include <algorithm>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <exception>
#include <functional>
#include <limits>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <utility>
#include <vector>

namespace mcvr {
// Synchronous frame-local work only. Returning (including by exception) means
// every worker has released the borrowed inputs; no task survives publication.
class CpuPreparationBatch {
  public:
    using ThreadFactory = std::thread (*)(std::function<void()>);

  private:
    std::mutex dispatch_, mutex_;
    std::condition_variable ready_, completed_;
    std::vector<std::thread> workers_;
    std::function<void(size_t)> operation_;
    std::atomic<size_t> next_{0};
    std::atomic<size_t> backgroundJobs_{0};
    std::atomic<size_t> threadCount_{0};
    unsigned concurrency_;
    ThreadFactory threadFactory_;
    size_t count_ = 0, remaining_ = 0, failedIndex_ = 0;
    uint64_t generation_ = 0;
    bool stopping_ = false;
    std::exception_ptr failure_;

    void execute(bool background = false) noexcept {
        size_t completed = 0;
        for (;;) {
            const size_t first = next_.fetch_add(4, std::memory_order_relaxed);
            if (first >= count_) {
                if (background) backgroundJobs_.fetch_add(completed, std::memory_order_relaxed);
                return;
            }
            for (size_t i = first; i < std::min(first + 4, count_); ++i) {
                ++completed;
                try {
                    operation_(i);
                } catch (...) {
                    std::lock_guard lock(mutex_);
                    if (i < failedIndex_) {
                        failedIndex_ = i;
                        failure_ = std::current_exception();
                    }
                }
            }
        }
    }

    void work() noexcept {
        uint64_t observed = 0;
        std::unique_lock lock(mutex_);
        for (;;) {
            ready_.wait(lock, [&] { return stopping_ || generation_ != observed; });
            if (stopping_) return;
            observed = generation_;
            lock.unlock();
            execute(true);
            lock.lock();
            if (--remaining_ == 0) completed_.notify_one();
        }
    }

    void stop() noexcept {
        {
            std::lock_guard lock(mutex_);
            stopping_ = true;
        }
        ready_.notify_all();
        for (auto &worker : workers_)
            if (worker.joinable()) worker.join();
        workers_.clear();
        threadCount_.store(0, std::memory_order_relaxed);
    }

    void start() {
        if (!workers_.empty()) return;
        try {
            workers_.reserve(concurrency_ - 1);
            for (unsigned i = 1; i < concurrency_; ++i) workers_.emplace_back(threadFactory_([this] { work(); }));
            threadCount_.store(workers_.size(), std::memory_order_relaxed);
        } catch (...) {
            stop();
            std::lock_guard lock(mutex_);
            stopping_ = false;
            throw;
        }
    }

  public:
    static std::thread launchThread(std::function<void()> operation) {
        return std::thread(std::move(operation));
    }

    explicit CpuPreparationBatch(unsigned concurrency, ThreadFactory factory = launchThread)
        : concurrency_(concurrency), threadFactory_(factory) {
        if (concurrency < 1 || concurrency > 4) throw std::invalid_argument("CPU preparation concurrency: 1..4");
        if (!factory) throw std::invalid_argument("CPU preparation thread factory is null");
    }
    CpuPreparationBatch(const CpuPreparationBatch &) = delete;
    CpuPreparationBatch &operator=(const CpuPreparationBatch &) = delete;
    ~CpuPreparationBatch() {
        std::lock_guard dispatch(dispatch_);
        stop();
    }

    size_t backgroundJobs() const noexcept {
        return backgroundJobs_.load(std::memory_order_relaxed);
    }

    size_t backgroundThreadCount() const noexcept {
        return threadCount_.load(std::memory_order_relaxed);
    }

    void run(size_t count, const std::function<void(size_t)> &operation) {
        if (count > std::numeric_limits<size_t>::max() - 4)
            throw std::length_error("CPU preparation batch size overflow");
        std::lock_guard dispatch(dispatch_);
        if (concurrency_ == 1 || count < 2) {
            backgroundJobs_.store(0, std::memory_order_relaxed);
            for (size_t i = 0; i < count; ++i) operation(i);
            return;
        }
        start();
        {
            std::lock_guard lock(mutex_);
            operation_ = operation;
            count_ = count;
            next_.store(0, std::memory_order_relaxed);
            backgroundJobs_.store(0, std::memory_order_relaxed);
            failedIndex_ = std::numeric_limits<size_t>::max();
            failure_ = nullptr;
            remaining_ = workers_.size();
            ++generation_;
        }
        ready_.notify_all();
        execute();
        std::exception_ptr failure;
        {
            std::unique_lock lock(mutex_);
            completed_.wait(lock, [&] { return remaining_ == 0; });
            operation_ = {};
            failure = std::exchange(failure_, nullptr);
        }
        if (failure) std::rethrow_exception(failure);
    }
};
} // namespace mcvr
