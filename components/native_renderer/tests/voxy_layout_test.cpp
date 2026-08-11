#include "core/render/lod_layout.hpp"
#include "core/render/scene_space.hpp"
#include <glm/gtc/matrix_transform.hpp>
#include <array>
#include <iostream>
#include <functional>
#include <vector>

int main() {
    using mcvr::lod::NodeKey;
    auto require = [](bool ok, const char *message = "Canonical LOD layout contract failed") {
        if (!ok) {
            std::cerr << message << '\n';
            throw std::runtime_error(message);
        }
    };
    require(mcvr::lod::canReuseCoverage(true, true, false, false));
    require(!mcvr::lod::canReuseCoverage(true, true, false, true));
    require(mcvr::lod::canReuseCoverage(true, true, true, true));
    require(!mcvr::lod::canReuseCoverage(false, true, true, false));
    require(!mcvr::lod::canReuseCoverage(true, false, true, false));
    // Whole-node removal must never mistake a partial near publication for coverage.
    for (uint32_t level = 0; level <= 4; ++level) {
        const int width = 2 << level;
        for (int base : {-32, 0, 32}) {
            const glm::ivec3 first(base, -16, base);
            size_t capacity = 1;
            while (capacity < size_t(width * width * width) * 2) capacity *= 2;
            auto table = [&](bool gap) {
                std::vector<glm::ivec4> cells(capacity, glm::ivec4(0));
                for (int x = 0; x < width; ++x)
                    for (int y = 0; y < width; ++y)
                        for (int z = 0; z < width; ++z) {
                            if (gap && x == width - 1 && y == width - 1 && z == width - 1) continue;
                            auto cell = first + glm::ivec3(x, y, z);
                            size_t at = mcvr::lod::coverageHash(cell) & (capacity - 1);
                            while (cells[at].w) at = (at + 1) & (capacity - 1);
                            cells[at] = glm::ivec4(cell, 1);
                        }
                return cells;
            };
            require(mcvr::lod::fullyCovered(table(false), first * 16, level));
            require(!mcvr::lod::fullyCovered(table(true), first * 16, level));
            require(!mcvr::lod::fullyCovered({}, first * 16, level));
        }
    }
    auto rejects = [&](auto function) {
        bool rejected = false;
        try {
            function();
        } catch (const std::invalid_argument &) { rejected = true; }
        require(rejected);
    };
    // A fixed world point must reproject from its previous camera-relative position.
    // Reusing this frame's translation produces false object motion while walking/flying.
    for (double base : {0.0, -30000000.0, 30000000.0}) {
        const glm::dvec3 origin(base + 512, 64, base - 256);
        const glm::dvec3 before(base + .25, 75.125, base + .5);
        const auto previous = mcvr::space::worldOriginFloatLocal(glm::mat4(1), origin, before);
        const glm::vec4 local(7.5, .25, 12.75, 1);
        const std::pair<uint64_t, uint64_t> identity{11, 0x12345678};
        const mcvr::lod::HistoryEntry history{identity, previous};
        for (glm::dvec3 delta : {glm::dvec3(.125, 0, .25), glm::dvec3(0, .5, 0), glm::dvec3(-2, .25, 1)}) {
            const auto current = mcvr::space::worldOriginFloatLocal(glm::mat4(1), origin, before + delta);
            const auto actual = history.previousTransform(identity, current) * local;
            require(glm::length(actual - previous * local) < 1e-5f, "Previous camera-relative point changed");
            require(glm::length(actual - current * local) > .1f, "Old/current camera counterexample missing");
            const auto view = glm::rotate(glm::mat4(1), .27f, glm::vec3(0, 1, 0));
            require(glm::length(view * actual - view * (previous * local)) < 1e-5f, "Previous projection changed");
            require(!history.matches({12, identity.second}));
            require(history.previousTransform({12, identity.second}, current) == current);
            require(!history.matches({identity.first, identity.second + 1}));
        }
    }
    for (uint32_t level = 0; level <= 4; ++level)
        for (int x : {-1048576, -33, -1, 0, 1, 1048575})
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
    rejects([&] { mcvr::lod::validateCut(overlap); });
    std::array<uint64_t, 2> duplicate{child.encode(), child.encode()};
    rejects([&] { mcvr::lod::validateCut(duplicate); });
    std::array<uint64_t, 2> siblings{child.encode(), NodeKey{0, -2, 0, -1}.encode()};
    mcvr::lod::validateCut(siblings);
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
    const mcvr::lod::RootCoverage coverage(rows, -1, 0);
    mcvr::lod::validateCut(siblings);
    for (int x = -66; x <= 66; ++x)
        for (int z = -66; z <= 66; ++z)
            for (int y = -2; y <= 1; ++y)
                require(coverage.contains(NodeKey{4, x, y, z}.encode()) ==
                        (y >= -1 && y <= 0 && x * x + z * z <= 64 * 64));
    rejects([&] { mcvr::lod::RootCoverage(std::array<int32_t, 3>{0, 1, 0}, -1, 0); });
    rejects([&] { mcvr::lod::RootCoverage(std::array<int32_t, 6>{1, 0, 0, 0, 0, 0}, -1, 0); });
    rejects([&] { mcvr::lod::RootCoverage(std::array<int32_t, 1>{0}, -1, 0); });
    rejects([&] { mcvr::lod::RootCoverage({}, -262145, 0); });
    std::vector<uint64_t> tooMany(8193);
    bool capacityRejected = false;
    try {
        mcvr::lod::validateCut(tooMany);
    } catch (const std::length_error &) { capacityRejected = true; }
    require(capacityRejected);
    rejects([&] { NodeKey::decode(uint64_t(7) << 61); });
    rejects([&] { NodeKey{5, 0, 0, 0}.encode(); });
    rejects([&] { NodeKey{0, 0, 262144, 0}.encode(); });
    std::cout
        << "Canonical LOD key ranges, negative coordinates, exact parent relation and nonoverlapping LOD cut PASS\n";
}
