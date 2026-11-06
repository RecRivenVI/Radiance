#include "core/render/frame_execution.hpp"

#include "core/diagnostics/frame_profile.hpp"

#include <cstdio>
#include <cstdlib>
#include <stdexcept>
#include <string_view>
#include <utility>

namespace mcvr {
thread_local unsigned FrameExecution::Producer::depth_ = 0;

FrameExecution &FrameExecution::instance() {
    static FrameExecution execution;
    return execution;
}

bool FrameExecution::enabled() {
    static const bool value = [] {
        const char *setting = std::getenv("RADIANCE_NATIVE_FRAME_WORKER");
        return setting && std::string_view(setting) == "1";
    }();
    return value;
}

FrameExecution::Producer::Producer(bool enter, const char *operation) : entered_(enter && enabled()) {
    if (!entered_) return;
    if (depth_ == 0) {
        auto &execution = instance();
        for (;;) {
            std::shared_lock access(execution.producers_);
            std::unique_lock state(execution.mutex_);
            if (!execution.active_) {
                access_ = std::move(access);
                break;
            }
            access.unlock();
            execution.waitUntilIdle(state, operation);
        }
    }
    ++depth_;
}

FrameExecution::Producer::~Producer() {
    if (entered_) --depth_;
}

void FrameExecution::dispatch(std::function<int()> work) {
    std::unique_lock access(producers_);
    std::lock_guard state(mutex_);
    if (active_ || result_ || error_) throw std::logic_error("Previous native frame has not been consumed");
    if (!worker_.joinable()) worker_ = std::thread([this] { execute(); });
    work_ = std::move(work);
    if (profile::enabled()) {
        handoffProfile_.emplace(std::this_thread::get_id(), profile::frame, profile::epoch.load());
    }
    active_ = true;
    changed_.notify_all();
}

void FrameExecution::waitUntilIdle(std::unique_lock<std::mutex> &state, const char *operation) {
    if (!active_) return;
    const bool record = handoffProfile_ && handoffProfile_->producer == std::this_thread::get_id();
    const auto began = record ? profile::now() : 0;
    if (record && handoffProfile_->firstWait == 0) handoffProfile_->firstWait = began;
    changed_.wait(state, [&] { return !active_; });
    if (record && handoffProfile_ && handoffProfile_->firstWait == began) {
        handoffProfile_->blocked += profile::now() - began;
        if (profile::enabled() && handoffProfile_->generation == profile::epoch.load()) {
            char label[80];
            std::snprintf(label, sizeof(label), "frame-worker.first-wait/%s", operation);
            const auto owner = handoffProfile_->owner;
            profile::emit(owner, owner ? 0u : 1u, label, handoffProfile_->blocked, handoffProfile_->blocked);
        }
    }
}

std::optional<int> FrameExecution::consume() {
    std::unique_lock state(mutex_);
    waitUntilIdle(state, "frame result");
    if (auto sample = std::exchange(handoffProfile_, std::nullopt);
        sample && profile::enabled() && sample->generation == profile::epoch.load()) {
        // A runnable producer may itself be idle or preempted. This interval
        // bounds the work the handoff could hide; it does not measure CPU residency.
        const auto cutoff = sample->firstWait ? std::min(sample->firstWait, sample->workerEnded) : sample->workerEnded;
        const auto overlap = cutoff - std::min(cutoff, sample->workerBegan);
        const auto domain = sample->owner ? 0u : 1u;
        profile::emit(sample->owner, domain, "frame-worker.producer-blocked", sample->blocked, sample->blocked);
        profile::emit(sample->owner, domain, "frame-worker.overlap-window", overlap, overlap);
        const auto work = sample->workerEnded - sample->workerBegan;
        profile::emit(sample->owner, domain, "frame-worker.execute", work, work);
    }
    if (auto error = std::exchange(error_, nullptr)) std::rethrow_exception(error);
    return std::exchange(result_, std::nullopt);
}

void FrameExecution::execute() {
    std::unique_lock state(mutex_);
    for (;;) {
        changed_.wait(state, [&] { return active_ || closed_; });
        if (!active_) return;
        auto work = std::move(work_);
        if (handoffProfile_) handoffProfile_->workerBegan = profile::now();
        state.unlock();
        std::optional<int> result;
        std::exception_ptr error;
        try {
            result = work();
        } catch (...) { error = std::current_exception(); }
        work = {};
        state.lock();
        if (handoffProfile_) handoffProfile_->workerEnded = profile::now();
        result_ = result;
        error_ = error;
        active_ = false;
        changed_.notify_all();
    }
}

FrameExecution::~FrameExecution() {
    {
        std::lock_guard state(mutex_);
        closed_ = true;
        changed_.notify_all();
    }
    if (worker_.joinable()) worker_.join();
}
} // namespace mcvr
