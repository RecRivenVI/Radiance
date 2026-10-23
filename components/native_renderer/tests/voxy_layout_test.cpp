#include "core/render/voxy_layout.hpp"
#include <array>
#include <iostream>
#include <functional>
#include <vector>

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
    rejects([&] { mcvr::voxy::validateCut(overlap); });
    std::array<uint64_t, 2> duplicate{child.encode(), child.encode()};
    rejects([&] { mcvr::voxy::validateCut(duplicate); });
    std::array<uint64_t, 2> siblings{child.encode(), NodeKey{0, -2, 0, -1}.encode()};
    mcvr::voxy::validateCut(siblings);
    // A requested domain is not the set of resident meshes. The supported source
    // radius of 64 root cells legitimately exceeds the 8192-node residency limit.
    std::vector<int32_t> rows;
    size_t rootCount = 0;
    for (int z = -64; z <= 64; ++z) {
        int extent = 0;
        while ((extent + 1) * (extent + 1) + z * z <= 64 * 64) ++extent;
        rows.insert(rows.end(), {z, -extent, extent});
        rootCount += 2 * (2 * extent + 1);
    }
    require(rootCount > 8192);
    const mcvr::voxy::RootCoverage coverage(rows, -1, 0);
    mcvr::voxy::validateCut(siblings);
    for (int x = -66; x <= 66; ++x)
        for (int z = -66; z <= 66; ++z)
            for (int y = -2; y <= 1; ++y)
                require(coverage.contains(NodeKey{4, x, y, z}.encode()) ==
                        (y >= -1 && y <= 0 && x * x + z * z <= 64 * 64));
    rejects([&] { mcvr::voxy::RootCoverage(std::array<int32_t, 3>{0, 1, 0}, -1, 0); });
    rejects([&] { mcvr::voxy::RootCoverage(std::array<int32_t, 6>{1, 0, 0, 0, 0, 0}, -1, 0); });
    rejects([&] { mcvr::voxy::RootCoverage(std::array<int32_t, 1>{0}, -1, 0); });
    rejects([&] { mcvr::voxy::RootCoverage({}, -129, 0); });
    std::vector<uint64_t> tooMany(8193);
    bool capacityRejected = false;
    try {
        mcvr::voxy::validateCut(tooMany);
    } catch (const std::length_error &) { capacityRejected = true; }
    require(capacityRejected);
    rejects([&] { NodeKey::decode(child.encode() | 1); });
    rejects([&] { NodeKey{5, 0, 0, 0}.encode(); });
    rejects([&] { NodeKey{0, 0, 128, 0}.encode(); });
    std::cout << "Voxy key ranges, negative coordinates, exact parent relation and nonoverlapping LOD cut PASS\n";
}
