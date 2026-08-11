#include "profile_collector.hpp"
#include <stdexcept>
#include <thread>
#include <memory>
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
}
