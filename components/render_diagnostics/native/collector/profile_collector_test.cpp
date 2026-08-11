#include "profile_collector.hpp"
#include <stdexcept>
#include <thread>
int main() {
    radiance::audit::profile::Queue queue;
    queue.add(77,0,"entity,pack",50,20);
    if(queue.drain()!="77,0,entity_pack,50,20\n") throw std::runtime_error("record lost");
    std::thread a([&]{for(int i=0;i<6000;++i)queue.add(0,1,"worker",2,1);});
    std::thread b([&]{for(int i=0;i<6000;++i)queue.add(0,1,"worker",2,1);});
    a.join();b.join();
    auto data=queue.drain();
    if(data.find("0,9,dropped,3808,0\n")==std::string::npos || !queue.drain().empty())
        throw std::runtime_error("bounded concurrent queue failed");
}
