#include "core/render/chunk_build_worker.hpp"
#include <atomic>
#include <future>
#include <iostream>
#include <memory>
#include <stdexcept>

namespace {
void require(bool value) {
    if (!value) throw std::runtime_error("chunk worker regression");
}
std::jthread failThread(std::function<void(std::stop_token)>) {
    throw std::runtime_error("thread creation failed");
}
} // namespace
int main() {
    try {
        mcvr::ChunkBuildWorker worker;
        using State = mcvr::ChunkBuildWorker::State;
        std::atomic<int> steps = 0, failures = 0;
        auto failure = [&](std::exception_ptr) noexcept { ++failures; };
        try {
            worker.start([](auto) { return State::idle; }, failure, failThread);
            require(false);
        } catch (const std::runtime_error &) { require(!worker.running()); }
        auto owner = std::make_shared<int>(5);
        std::weak_ptr<int> weak = owner;
        std::promise<void> progressed;
        auto future = progressed.get_future();
        worker.start(
            [&, owner](auto) {
                if (++steps == 4) progressed.set_value();
                return State::busy;
            },
            failure);
        owner.reset();
        require(future.wait_for(std::chrono::seconds(2)) == std::future_status::ready);
        require(!weak.expired());
        worker.stop();
        require(!worker.running() && weak.expired() && failures == 0);
        auto stopped = steps.load();
        worker.stop();
        require(steps == stopped);
        std::promise<void> failed;
        future = failed.get_future();
        worker.start([](auto) -> State { throw std::runtime_error("preparation failed"); },
                     [&](std::exception_ptr error) noexcept {
                         try {
                             std::rethrow_exception(error);
                         } catch (const std::runtime_error &) { ++failures; } catch (...) {
                         }
                         failed.set_value();
                     });
        require(future.wait_for(std::chrono::seconds(2)) == std::future_status::ready);
        worker.stop();
        require(failures == 1);
        // Explicit stop interrupts the idle wait; no frame or further producer is required.
        worker.start([](auto) { return State::idle; }, failure);
        worker.notify();
        worker.stop();
        require(!worker.running());
        for (int turn = 0; turn < 128; ++turn) {
            worker.start([](auto) { return State::idle; }, failure);
            std::this_thread::yield();
            worker.stop();
        }
        require(failures == 1);
        std::cout
            << "continuous progress, thread creation failure, exception boundary, stop/restart and owned-input retirement passed\n";
        return 0;
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
