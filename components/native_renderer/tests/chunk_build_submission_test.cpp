#include "core/render/chunk_build_submission.hpp"
#include "core/render/quad_topology.hpp"
#include <chrono>
#include <exception>
#include <future>
#include <iostream>
#include <thread>

using namespace std::chrono_literals;
void require(bool value) {
    if (!value) throw std::runtime_error("Chunk submission regression");
}
struct Owner {
    int64_t desiredVersion = -1, latestVersion = 0;
    int published = 7;
};
struct Fixture {
    std::recursive_mutex mutex;
    mcvr::ExternalChunkHandleTable handles;
    std::vector<std::shared_ptr<Owner>> owners{std::make_shared<Owner>()};
    Fixture() {
        handles.reset(1);
    }
    template <typename Prepare, typename Publish>
    bool submit(int64_t handle, int64_t version, Prepare prepare, Publish publish) {
        return mcvr::chunkInput::submit(mutex, handles, owners, handle, version, prepare, publish);
    }
};
struct Gate {
    std::promise<void> entered, resume;
    std::future<void> resumed = resume.get_future();
    void pause() {
        entered.set_value();
        resumed.get();
    }
};
auto prepareMesh() {
    using namespace mcvr::topology;
    std::vector<Point> points{{0, 0, 0}, {1, 0, .1f}, {1, 1, 0},   {0, 1, 0},
                              {0, 1, 0}, {1, 1, 0},   {1, 0, .1f}, {0, 0, 0}};
    return reconcileAuthored(points, sequentialQuads(points.size()), true, true);
}
template <typename Change>
void staleCase(int64_t handle, int64_t version, Change change, Fixture &fixture) {
    Gate gate;
    bool published = false;
    auto worker = std::async(std::launch::async, [&] {
        return fixture.submit(
            handle, version,
            [&](const auto &) {
                auto mesh = prepareMesh();
                gate.pause();
                return mesh;
            },
            [&](const auto &, auto) { published = true; });
    });
    const bool entered = gate.entered.get_future().wait_for(2s) == std::future_status::ready;
    bool available = false;
    std::exception_ptr failure;
    try {
        if (entered) {
            std::unique_lock reader(fixture.mutex, std::try_to_lock);
            available = reader.owns_lock();
            if (available) change(fixture);
        }
    } catch (...) { failure = std::current_exception(); }
    gate.resume.set_value();
    bool accepted = worker.get();
    if (failure) std::rethrow_exception(failure);
    require(entered && available && !accepted && !published);
}
int main() {
    {
        Fixture f;
        Gate gate;
        const auto expected = prepareMesh();
        auto worker = std::async(std::launch::async, [&] {
            return f.submit(
                0, 1,
                [&](const auto &) {
                    auto mesh = prepareMesh();
                    gate.pause();
                    return mesh;
                },
                [&](const auto &ticket, auto mesh) {
                    require(ticket.version == 1 && mesh.indices == expected.indices &&
                            mesh.primitiveFlags == expected.primitiveFlags);
                    require(mesh.report.correctedQuads == 1 && mesh.report.pairedQuads == 2);
                    bool available = true;
                    std::thread reader([&] {
                        std::unique_lock lock(f.mutex, std::try_to_lock);
                        available = lock.owns_lock();
                    });
                    reader.join();
                    require(!available); // Publication, unlike preparation, is serialized.
                    f.owners[0]->published = 11;
                });
        });
        bool entered = gate.entered.get_future().wait_for(2s) == std::future_status::ready;
        std::unique_lock reader(f.mutex, std::try_to_lock);
        bool available = reader.owns_lock();
        if (available) reader.unlock();
        gate.resume.set_value();
        bool accepted = worker.get();
        require(entered && available && accepted && f.owners[0]->published == 11);
    }
    {
        Fixture f;
        staleCase(
            0, 1,
            [](auto &state) {
                require(state.submit(
                    0, 2, [](const auto &) { return 99; },
                    [&](const auto &, int value) { state.owners[0]->published = value; }));
            },
            f);
        require(f.owners[0]->published == 99);
    }
    {
        Fixture f;
        std::weak_ptr old = f.owners[0];
        staleCase(
            0, 1,
            [&](auto &state) {
                state.handles.reset(1);
                state.owners[0] = std::make_shared<Owner>();
                state.owners[0]->desiredVersion = 1;
                require(old.expired()); // CPU work must not retain an old owner's GPU state.
            },
            f);
        require(f.owners[0]->published == 7);
    }
    {
        Fixture f;
        auto allocation = f.handles.allocate(uint32_t(f.owners.size()));
        f.owners.push_back(std::make_shared<Owner>());
        staleCase(
            allocation.handle, 1,
            [&](auto &state) {
                require(state.handles.release(allocation.handle).has_value());
                auto reused = state.handles.allocate(uint32_t(state.owners.size()));
                require(reused.reused && reused.slot == allocation.slot);
                state.owners[reused.slot] = std::make_shared<Owner>();
                state.owners[reused.slot]->desiredVersion = 1;
            },
            f);
    }
    {
        Fixture f;
        staleCase(
            0, 1,
            [](auto &state) {
                state.handles.reset(0);
                state.owners.clear();
            },
            f);
    }
    {
        Fixture f;
        staleCase(
            0, -1,
            [](auto &state) {
                require(state.submit(
                    0, -1, [](const auto &) { return 88; },
                    [&](const auto &, int value) { state.owners[0]->published = value; }));
            },
            f);
        require(f.owners[0]->published == 88 && f.owners[0]->latestVersion == 2);
    }
    {
        Fixture f;
        bool threw = false;
        try {
            f.submit(
                0, 1, [](const auto &) -> int { throw std::runtime_error("prepare failed"); },
                [](const auto &, int) { throw std::logic_error("Must not publish"); });
        } catch (const std::runtime_error &error) { threw = std::string(error.what()) == "prepare failed"; }
        require(threw && f.owners[0]->published == 7);
        require(f.submit(
            0, 1, [](const auto &) { return 8; }, [&](const auto &, int value) { f.owners[0]->published = value; }));
        bool prepared = false;
        require(!f.submit(
            0, 0,
            [&](const auto &) {
                prepared = true;
                return 0;
            },
            [](const auto &, int) {}));
        require(!prepared && f.owners[0]->published == 8);
        try {
            f.submit(
                0, 2, [](const auto &) { return 2; },
                [](const auto &, int) { throw std::runtime_error("publish failed"); });
            require(false);
        } catch (const std::runtime_error &error) { require(std::string(error.what()) == "publish failed"); }
        require(f.submit(
            0, 3, [](const auto &) { return 9; }, [&](const auto &, int value) { f.owners[0]->published = value; }));
    }
    {
        Fixture f;
        f.owners[0]->latestVersion = std::numeric_limits<int64_t>::max();
        bool overflow = false;
        try {
            f.submit(0, -1, [](const auto &) { return 0; }, [](const auto &, int) {});
        } catch (const std::overflow_error &) { overflow = true; }
        require(overflow && f.owners[0]->published == 7);
    }
    std::cout
        << "Unlocked preparation, serialized publication, topology, stale/reuse/reset/close, legacy ordering and exceptions passed\n";
}
