#include "profile_collector.hpp"
#include <stdexcept>
#include <thread>
#include <memory>
#include <sstream>
#include <vector>
int main() {
    auto storage = std::make_unique<radiance::audit::profile::Queue>();
    auto &queue = *storage;
    queue.add(77,0,"entity,pack",50,20);
    if(queue.drain()!="77,0,entity_pack,50,20\n") throw std::runtime_error("record lost");
    for (int i=0;i<19576;++i) queue.add(77,0,"rigid-burst",2,1);
    const auto burst = queue.drain();
    if (std::count(burst.begin(), burst.end(), '\n') != 19576
        || burst.find(",9,dropped,") != std::string::npos)
        throw std::runtime_error("Measured rigid-recording burst exceeds collector capacity");
    const auto each = (queue.events.size() + 3808) / 2;
    std::thread a([&]{for(size_t i=0;i<each;++i)queue.add(0,1,"worker",2,1);});
    std::thread b([&]{for(size_t i=0;i<each;++i)queue.add(0,1,"worker",2,1);});
    a.join();b.join();
    auto data=queue.drain();
    if(data.find("0,9,dropped,3808,0\n")==std::string::npos || !queue.drain().empty())
        throw std::runtime_error("bounded concurrent queue failed");
    std::atomic<bool> done{false};
    std::thread producer([&] { for (int i = 0; i < 1000; ++i) queue.add(i, 0, "owned", i, i); done = true; });
    std::string streamed;
    while (!done.load()) streamed += queue.drain();
    producer.join();
    streamed += queue.drain();
    size_t lines = 0;
    for (char c : streamed) if (c == '\n') ++lines;
    if (lines != 1000 || streamed.find(",9,dropped,") != std::string::npos)
        throw std::runtime_error("concurrent draining lost or duplicated an event");
    std::atomic<bool> readerHeld{false}, releaseReader{false};
    std::thread busyReader([&] {
        std::lock_guard held(queue.reader);
        readerHeld = true;
        while (!releaseReader.load()) std::this_thread::yield();
    });
    while (!readerHeld.load()) std::this_thread::yield();
    const auto deferred = queue.drain();
    queue.add(91, 0, "while-reading", 3, 2);
    releaseReader = true;
    busyReader.join();
    if (!deferred.empty()) throw std::runtime_error("busy reader must defer without waiting");
    if (queue.drain() != "91,0,while-reading,3,2\n")
        throw std::runtime_error("reader contention must neither block producers nor discard records");
    constexpr size_t workers = 8, perWorker = 4096;
    std::atomic<size_t> finished{0};
    std::vector<std::thread> producers;
    for (size_t worker = 0; worker < workers; ++worker)
        producers.emplace_back([&, worker] {
            for (size_t i = 0; i < perWorker; ++i) {
                const auto id = worker * perWorker + i;
                queue.add(id, 0, "parallel", id, id);
            }
            ++finished;
        });
    streamed.clear();
    while (finished.load() != workers) {
        streamed += queue.drain();
        std::this_thread::yield();
    }
    for (auto &worker : producers) worker.join();
    streamed += queue.drain();
    std::vector<bool> seen(workers * perWorker);
    size_t recorded = 0, lost = 0;
    std::istringstream input(streamed);
    std::string row;
    while (std::getline(input, row)) {
        std::replace(row.begin(), row.end(), ',', ' ');
        std::istringstream columns(row);
        uint64_t id, inclusive, self;
        uint32_t domain;
        std::string label;
        if (!(columns >> id >> domain >> label >> inclusive >> self))
            throw std::runtime_error("partial concurrent record");
        if (domain == 9 && label == "dropped") { lost += inclusive; continue; }
        if (id >= seen.size() || seen[id] || inclusive != id || self != id)
            throw std::runtime_error("overwritten or duplicate concurrent record");
        seen[id] = true;
        ++recorded;
    }
    if (recorded + lost != workers * perWorker || !queue.drain().empty())
        throw std::runtime_error("every concurrent event must be recorded once or explicitly counted as lost");
}
