#include "bounded_wait.hpp"
#include <stdexcept>
int main() {
    for (auto limit : {0u, 1u, 13u, 5000u, 100000u}) {
        uint64_t clock=0;unsigned calls=0;
        const auto result=local_capture::wait(limit,[&]{++calls;return local_capture::Status{1,true};},
            [&]{return clock;},[&](uint32_t ms){clock+=ms;});
        if(result!=1 || clock!=std::min(limit,5000u) || calls>251)
            throw std::runtime_error("status timeout exceeded bounded deadline");
    }
    for(auto terminal:{2u,4u,5u}){
        uint64_t clock=0;unsigned calls=0;
        const auto result=local_capture::wait(5000,[&]{return ++calls<3?local_capture::Status{1,true}:local_capture::Status{terminal,terminal!=5};},
            [&]{return clock;},[&](uint32_t ms){clock+=ms;});
        if(result!=terminal||clock!=40||calls!=3)throw std::runtime_error("terminal/error status did not end wait");
    }
}
