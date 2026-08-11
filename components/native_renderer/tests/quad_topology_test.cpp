#include "core/render/quad_topology.hpp"
#include <iostream>
#include <numeric>
#include <random>
#include <set>

using namespace mcvr::topology;
void require(bool value) {
    if (!value) throw std::runtime_error("Quad topology regression");
}
// Independent quadratic oracle for the original <=64-polygon admission path.
// Keep input validation and early-return order observable as well as the result.
template <size_t N, typename Fetch>
bool referenceReversePair(size_t count, Fetch fetch) {
    if (count % N) throw std::invalid_argument("Incomplete primitive");
    std::vector<std::array<Corner, N>> earlier;
    for (size_t at = 0; at < count; at += N) {
        std::array<Corner, N> current;
        for (size_t i = 0; i < N; ++i) {
            const auto point = fetch(at + i);
            for (float value : point)
                if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite position");
            current[i] = bits(point);
        }
        bool repeated = false;
        for (size_t i = 0; i < N; ++i)
            for (size_t j = 0; j < i; ++j) repeated |= current[i] == current[j];
        if (repeated) continue;
        for (const auto &previous : earlier)
            for (size_t shift = 0; shift < N; ++shift) {
                bool match = true;
                for (size_t i = 0; i < N; ++i) match &= current[i] == previous[(shift + N - i) % N];
                if (match) return true;
            }
        earlier.push_back(current);
    }
    return false;
}
struct CollisionHash {
    template <typename Key>
    size_t operator()(const Key &) const noexcept {
        return 0;
    }
};
template <size_t N>
void compareReverseProbe(const std::vector<Point> &points) {
    struct Outcome {
        int result;
        size_t reads;
        bool operator==(const Outcome &) const = default;
    };
    const auto observe = [&](auto probe) {
        Outcome out{0, 0};
        try {
            out.result = probe([&](size_t i) {
                ++out.reads;
                return points.at(i);
            });
        } catch (const std::invalid_argument &) { out.result = 2; }
        return out;
    };
    const auto expected = observe([&](auto fetch) { return referenceReversePair<N>(points.size(), fetch); });
    require(observe([&](auto fetch) { return hasReversePolygonPair<N>(points.size(), fetch); }) == expected);
    require(observe([&](auto fetch) { return hasReversePolygonPair<N>(points.size(), fetch, CollisionHash{}); }) ==
            expected);
}
template <size_t N>
unsigned admissionCases() {
    unsigned cases = 0;
    std::array<Point, N> corners;
    for (size_t i = 0; i < N; ++i) corners[i] = {float(i), float(i * i), float(i & 1)};
    std::array<size_t, N> first;
    std::iota(first.begin(), first.end(), 0);
    do {
        std::array<size_t, N> second;
        std::iota(second.begin(), second.end(), 0);
        do {
            std::vector<Point> points;
            for (auto i : first) points.push_back(corners[i]);
            for (auto i : second) points.push_back(corners[i]);
            compareReverseProbe<N>(points);
            ++cases;
        } while (std::next_permutation(second.begin(), second.end()));
    } while (std::next_permutation(first.begin(), first.end()));
    std::mt19937 random(0x4218 + N);
    for (unsigned trial = 0; trial < 2000; ++trial) {
        std::vector<Point> points;
        const size_t count = random() % 65;
        for (size_t face = 0; face < count; ++face) {
            const size_t group = random() % 12;
            const size_t source = face ? random() % face : 0;
            std::array<size_t, N> order;
            std::iota(order.begin(), order.end(), 0);
            std::shuffle(order.begin(), order.end(), random);
            const unsigned variant = random() % 5;
            for (size_t i = 0; i < N; ++i) {
                Point p = face && variant == 0 ? points[source * N + order[i]] : corners[order[i]];
                if (variant != 0) p[0] += float(group * 8);
                if (variant == 2 && i == N - 1) p = points[face * N];
                if (variant == 3 && i == 0) p[0] = std::nextafter(p[0], 1000.0f);
                points.push_back(p);
            }
        }
        compareReverseProbe<N>(points);
        ++cases;
    }
    // Full small table, displaced keys, and a reverse partner late in the scan.
    for (size_t count : {size_t{0}, size_t{1}, size_t{63}, size_t{64}}) {
        std::vector<Point> points;
        for (size_t face = 0; face < count; ++face)
            for (auto p : corners) {
                p[0] += float(face * 8);
                points.push_back(p);
            }
        compareReverseProbe<N>(points);
        if (count > 1) {
            for (size_t i = 0; i < N; ++i) points[(count - 1) * N + i] = points[N - 1 - i];
            compareReverseProbe<N>(points);
        }
    }
    std::vector<Point> points(corners.begin(), corners.end());
    points[0][2] = -0.0f;
    for (size_t i = 0; i < N; ++i) points.push_back(points[N - 1 - i]);
    compareReverseProbe<N>(points);
    points[2 * N - 1][2] = 0.0f;
    compareReverseProbe<N>(points);
    points[2 * N - 1][2] = std::numeric_limits<float>::denorm_min();
    compareReverseProbe<N>(points);
    // A complete pair returns before reading a later invalid face, as before.
    points.assign(corners.begin(), corners.end());
    for (size_t i = 0; i < N; ++i) points.push_back(points[N - 1 - i]);
    for (auto p : corners) points.push_back(p);
    for (float invalid : {std::numeric_limits<float>::quiet_NaN(), std::numeric_limits<float>::infinity()}) {
        points.back()[0] = invalid;
        compareReverseProbe<N>(points);
        points[0][0] = invalid;
        compareReverseProbe<N>(points);
        points[0] = corners[0];
    }
    points.resize(N - 1);
    compareReverseProbe<N>(points);
    return cases;
}
using Triangle = std::array<Corner, 3>;
std::multiset<Triangle>
triangles(const std::vector<Point> &points, const std::vector<uint32_t> &indices, size_t begin) {
    std::multiset<Triangle> out;
    for (size_t t = begin; t < begin + 6; t += 3) {
        Triangle triangle{bits(points[indices[t]]), bits(points[indices[t + 1]]), bits(points[indices[t + 2]])};
        std::sort(triangle.begin(), triangle.end());
        out.insert(triangle);
    }
    return out;
}
int main() {
    const auto probeCases = admissionCases<3>() + admissionCases<4>();
    std::array<Point, 4> corners{{{0, 0, 0}, {1, 0, .1f}, {1, 1, 0}, {0, 1, 0}}};
    unsigned cases = 0;
    for (bool planar : {false, true})
        for (bool reversed : {false, true})
            for (size_t rotation = 0; rotation < 4; ++rotation) {
                std::vector<Point> points(corners.begin(), corners.end());
                if (planar) points[1][2] = 0;
                for (size_t i = 0; i < 4; ++i) points.push_back(points[(rotation + (reversed ? 4 - i : i)) % 4]);
                const auto original = sequentialQuads(points.size());
                auto result = reconcile(points, original, true, true);
                require(hasReversePolygonPair<4>(points.size(), [&](size_t i) { return points[i]; }) == reversed);
                require(std::equal(original.begin(), original.begin() + 6, result.indices.begin()));
                if (!reversed) {
                    require(result.report.sameWinding == 1 && result.report.pairedQuads == 0);
                } else {
                    require(result.report.pairedQuads == 2);
                    if (!planar)
                        require(triangles(points, result.indices, 0) == triangles(points, result.indices, 6));
                    else
                        require(result.indices == original && result.report.correctedQuads == 0);
                    require(reconcile(points, original, true, true).indices == result.indices);
                }
                ++cases;
            }
    {
        std::vector<Point> points(corners.begin(), corners.end());
        for (int i = 3; i >= 0; --i) points.push_back(corners[i]);
        points[4][0] = std::nextafter(points[4][0], 1.0f);
        auto result = reconcile(points, sequentialQuads(points.size()), true, true);
        require(result.report.pairedQuads == 0 && result.report.correctedQuads == 0);
    }
    {
        std::vector<Point> points(corners.begin(), corners.end());
        for (int i = 3; i >= 0; --i) points.push_back(corners[i]);
        auto original = sequentialQuads(points.size());
        auto source = reconcile(points, original, true, false);
        auto repaired = reconcile(points, original, true, true);
        require(source.indices == original && source.report.correctedQuads == 0);
        require(repaired.report.correctedQuads == 1);
        require(!compatibleHistory(source.primitiveFlags[2], repaired.primitiveFlags[2]));
        require(compatibleHistory(repaired.primitiveFlags[2], repaired.primitiveFlags[2]));
        require(reconcile(points, original, false, true).indices == original);
        auto tetrahedron = reconcileAuthored(points, original, false, true);
        require(tetrahedron.indices == original && tetrahedron.report.pairedQuads == 0);
        require(reconcileAuthored(points, original, true, true).report.correctedQuads == 1);
        points.insert(points.end(), corners.begin(), corners.end());
        require(reconcile(points, sequentialQuads(points.size()), true, true).report.ambiguous == 1);
    }
    {
        std::vector<Point> points{{0, 0, 0}, {1, 0, 0}, {1, 0, 0}, {0, 1, 0}};
        require(reconcile(points, sequentialQuads(4), true, true).report.degenerate == 1);
        points[2][0] = std::numeric_limits<float>::quiet_NaN();
        require(reconcile(points, sequentialQuads(4), true, true).report.nonfinite == 1);
        bool rejectedNaN = false;
        try {
            hasReversePolygonPair<4>(points.size(), [&](size_t i) { return points[i]; });
        } catch (const std::invalid_argument &) { rejectedNaN = true; }
        require(rejectedNaN);
    }
    {
        std::vector<Point> points{{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 1, 0}, {1, 0, 0}, {0, 0, 0}};
        std::vector<uint32_t> indices{0, 1, 2, 3, 4, 5};
        auto result = reconcile(points, indices, false, true);
        require(result.report.pairedTriangles == 2 && result.indices == indices);
        std::vector<uint32_t> shared{0, 1, 2, 0, 2, 1};
        const std::array<uint32_t, 2> different{0, 3};
        auto flags = attachPrimitiveFlags(points, shared, different);
        require(shared[0] != shared[3] && points[shared[0]] == points[shared[3]]);
        require(flags[shared[0]] == 0 && flags[shared[3]] == 3);
    }
    {
        std::vector<Point> large;
        for (int q = 0; q < 80; ++q)
            for (auto p : corners) {
                p[0] += q * 3;
                large.push_back(p);
            }
        for (int j = 3; j >= 0; --j) large.push_back(large[j]);
        require(hasReversePolygonPair<4>(large.size(), [&](size_t i) { return large[i]; }));
    }
    bool rejected = false;
    try {
        sequentialQuads(5);
    } catch (const std::invalid_argument &) { rejected = true; }
    require(rejected);
    std::cout << probeCases << " exhaustive/randomized admission cases, including forced hash collisions; " << cases
              << " cyclic planar/warped/orientation cases; exact bits, ownership ambiguity and history checks passed\n";
}
