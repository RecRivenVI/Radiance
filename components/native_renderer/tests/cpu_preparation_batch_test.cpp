#include "core/render/cpu_preparation_batch.hpp"
#include "core/render/geometry_topology.hpp"
#include <barrier>
#include <cstring>
#include <iostream>
#include <numeric>
#include <string>

void require(bool value) {
    if (!value) throw std::runtime_error("CPU preparation regression");
}

std::atomic<int> launches{0}, liveThreads{0};
std::thread failSecondLaunch(std::function<void()> operation) {
    if (launches++ == 1) throw std::runtime_error("thread creation failure");
    return std::thread([operation = std::move(operation)] {
        ++liveThreads;
        operation();
        --liveThreads;
    });
}

int main() {
    {
        mcvr::CpuPreparationBatch batch(4, failSecondLaunch);
        std::atomic<int> calls{0};
        try {
            batch.run(64, [&](size_t) { ++calls; });
            require(false);
        } catch (const std::runtime_error &e) { require(std::string(e.what()) == "thread creation failure"); }
        require(calls == 0 && liveThreads == 0 && batch.backgroundThreadCount() == 0);
        batch.run(64, [&](size_t) { ++calls; });
        require(calls == 64 && batch.backgroundThreadCount() == 3);
    }
    {
        mcvr::CpuPreparationBatch batch(4);
        batch.run(4, [](size_t) {});
        require(batch.backgroundThreadCount() == 0);
        batch.run(8, [](size_t) {});
        require(batch.backgroundThreadCount() == 1);
        batch.run(16, [](size_t) {});
        require(batch.backgroundThreadCount() == 3);
        std::barrier entered(2);
        batch.run(8, [&](size_t i) {
            if (i % 4 == 0) entered.arrive_and_wait();
        });
        require(batch.backgroundJobs() == 4);
    }
    // Equal sampled texels do not establish a shared material. Compare authored
    // atlas regions before packing, including reverse/rotated UVs and triangles.
    for (bool quads : {false, true}) {
        using V = vk::VertexFormat::PBRVertex;
        std::vector<V> source(8);
        for (int i = 0; i < 4; ++i) {
            source[i].pos = {float(i == 1 || i == 2), float(i >= 2), 0};
            source[i].useTexture = 1;
            source[i].textureID = 9;
            source[i].useColorLayer = 1;
            source[i].colorLayer = glm::vec4(1);
            source[i].textureUV = {float(i == 1 || i == 2) * .0625f, float(i >= 2) * .0625f};
            source[7 - i] = source[i];
        }
        auto indices = quads ? mcvr::topology::sequentialQuads(8) : std::vector<uint32_t>{0, 1, 2, 5, 6, 7};
        auto check = [&](bool same) {
            auto vertices = source;
            auto output = indices;
            auto prepared = mcvr::geometry::prepare(vertices, output, quads);
            require(prepared.report.pairedQuads || prepared.report.pairedTriangles);
            for (size_t i = 0; i < output.size(); i += 3)
                require(((prepared.vertexFlags.at(output[i]) & mcvr::topology::sameMaterialSheet) != 0u) == same);
            require(prepared.sheetPairs.empty() != same);
            if (same)
                for (auto pair : prepared.sheetPairs) require(pair != 0 && pair == prepared.sheetPairs.front());
        };
        check(true);
        for (int i = 4; i < 8; ++i) source[i].textureUV.x += .125f;
        check(false);
        for (int i = 4; i < 8; ++i) source[i].textureUV.x -= .125f;
        source[5].colorLayer.r = .8f;
        check(false);
    }
    require(liveThreads == 0);
    using namespace mcvr::topology;
    for (unsigned concurrency : {1u, 2u, 4u}) {
        mcvr::CpuPreparationBatch batch(concurrency);
        require(batch.backgroundThreadCount() == 0);
        batch.run(0, [&](size_t) { require(false); });
        batch.run(1, [&](size_t i) { require(i == 0); });
        require(batch.backgroundThreadCount() == 0);
        using Vertex = vk::VertexFormat::PBRVertex;
        std::vector<std::vector<Vertex>> inputs, expectedVertices, actualVertices;
        std::vector<std::vector<uint32_t>> expectedIndices, actualIndices;
        std::vector<mcvr::geometry::PreparedTopology> expected, actual(128);
        const auto indices = sequentialQuads(8);
        for (size_t i = 0; i < actual.size(); ++i) {
            const float bend = (int(i % 3) - 1) * 0.1f;
            std::vector<Point> p{{0, 0, 0}, {1, 0, bend}, {1, 1, 0}, {0, 1, bend}};
            for (int j = 3; j >= 0; --j) p.push_back(p[j]);
            if (i & 1)
                for (auto &v : p) v[0] = -v[0];
            std::vector<Vertex> vertices;
            for (size_t j = 0; j < p.size(); ++j) {
                Vertex v;
                std::memset(&v, 0, sizeof(v));
                v.pos = {p[j][0], p[j][1], p[j][2]};
                v.norm = {0, 0, j < 4 ? 1.0f : -1.0f};
                v.useColorLayer = v.useTexture = 1;
                v.colorLayer = {i / 128.0f, j / 8.0f, 0.5f, 0.7f};
                v.textureUV = {float(j & 1), float((j >> 1) & 1)};
                v.textureID = static_cast<uint32_t>(i);
                v.postBase = {float(i), 17, -8};
                vertices.push_back(v);
            }
            inputs.push_back(vertices);
            expectedVertices.push_back(vertices);
            expectedIndices.push_back(indices);
            expected.push_back(mcvr::geometry::prepare(expectedVertices.back(), expectedIndices.back(), true));
        }
        for (int frame = 0; frame < 25; ++frame) {
            std::vector<std::atomic<int>> calls(actual.size());
            actualVertices = inputs;
            actualIndices.assign(inputs.size(), indices);
            batch.run(actual.size(), [&](size_t i) {
                ++calls[i];
                actual[i] = mcvr::geometry::prepare(actualVertices[i], actualIndices[i], true);
            });
            for (size_t i = 0; i < actual.size(); ++i) {
                require(calls[i] == 1);
                require(actualIndices[i] == expectedIndices[i]);
                require(actual[i].vertexFlags == expected[i].vertexFlags);
                require(actualVertices[i].size() == expectedVertices[i].size());
                require(std::memcmp(actualVertices[i].data(), expectedVertices[i].data(),
                                    actualVertices[i].size() * sizeof(Vertex)) == 0);
                require(actual[i].report.correctedQuads == expected[i].report.correctedQuads);
                require(actual[i].report.pairedQuads == expected[i].report.pairedQuads);
            }
        }
        if (concurrency > 1) {
            std::barrier entered(static_cast<std::ptrdiff_t>(concurrency));
            batch.run(concurrency * 4, [&](size_t i) {
                if (i % 4 == 0) entered.arrive_and_wait();
            });
            require(batch.backgroundJobs() == (concurrency - 1) * 4);
        }
        std::atomic<size_t> completed{0};
        try {
            batch.run(64, [&](size_t i) {
                ++completed;
                if (i == 7) throw std::runtime_error("first failure");
                if (i == 39) throw std::runtime_error("later failure");
            });
            require(false);
        } catch (const std::runtime_error &e) { require(std::string(e.what()) == "first failure"); }
        require(completed == (concurrency == 1 ? 8 : 64));
        completed = 0;
        batch.run(19, [&](size_t) { ++completed; });
        require(completed == 19);
        batch.run(0, [&](size_t) { require(false); });
        completed = 0;
        std::thread a([&] { batch.run(17, [&](size_t) { ++completed; }); });
        std::thread b([&] { batch.run(21, [&](size_t) { ++completed; }); });
        a.join();
        b.join();
        require(completed == 38);
    }
    std::cout << "CPU preparation: deterministic topology/history ordering, exact-once, real parallel execution, "
                 "drained failure, reuse, concurrent callers and shutdown passed\n";
}
