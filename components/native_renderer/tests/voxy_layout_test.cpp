#include "core/render/voxy_layout.hpp"
#include <array>
#include <iostream>
#include <functional>

int main() {
    using mcvr::voxy::NodeKey;
    auto require = [](bool ok) {
        if (!ok) throw std::runtime_error("Voxy layout contract failed");
    };
    auto rejects = [&](auto function) {
        bool rejected = false;
        try {
            function();
        } catch (const std::invalid_argument &) { rejected = true; }
        require(rejected);
    };
    for (uint32_t level = 0; level <= 4; ++level)
        for (int x : {-8388608, -33, -1, 0, 1, 8388607})
            for (int y : {-128, -1, 0, 127}) {
                NodeKey input{level, x, y, -x - 1};
                auto decoded = NodeKey::decode(input.encode());
                require(decoded.level == level && decoded.x == x && decoded.y == y && decoded.z == -x - 1);
                if (level < 4) {
                    auto parent = decoded.parent();
                    require(parent.x == (x < 0 ? (x - 1) / 2 : x / 2));
                }
            }
    const auto child = NodeKey{0, -1, 0, -1};
    const auto parent = child.parent();
    std::array<uint64_t, 2> overlap{child.encode(), parent.encode()};
    rejects([&] { mcvr::voxy::validateCut(overlap, {}); });
    std::array<uint64_t, 2> duplicate{child.encode(), child.encode()};
    rejects([&] { mcvr::voxy::validateCut(duplicate, {}); });
    std::array<uint64_t, 2> siblings{child.encode(), NodeKey{0, -2, 0, -1}.encode()};
    mcvr::voxy::validateCut(siblings, {});
    rejects([&] { NodeKey::decode(child.encode() | 1); });
    rejects([&] { NodeKey{5, 0, 0, 0}.encode(); });
    rejects([&] { NodeKey{0, 0, 128, 0}.encode(); });
    rejects([&] { mcvr::voxy::validateCut({}, std::span(&overlap[0], 1)); });
    std::cout << "Voxy key ranges, negative coordinates, exact parent relation and nonoverlapping LOD cut PASS\n";
}
