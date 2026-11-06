#pragma once

#include <glm/glm.hpp>
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <limits>
#include <memory>
#include <numeric>
#include <span>
#include <stdexcept>
#include <vector>

namespace mcvr::medium {
struct Box {
    glm::dvec3 lo{std::numeric_limits<double>::infinity()}, hi{-std::numeric_limits<double>::infinity()};
    void add(glm::dvec3 p) {
        lo = glm::min(lo, p);
        hi = glm::max(hi, p);
    }
    void add(const Box &b) {
        if (b.valid()) {
            add(b.lo);
            add(b.hi);
        }
    }
    bool valid() const {
        return glm::all(glm::lessThanEqual(lo, hi));
    }
    bool overlaps(const Box &b) const {
        return valid() && b.valid() && glm::all(glm::lessThanEqual(lo, b.hi)) && glm::all(glm::lessThanEqual(b.lo, hi));
    }
    Box padded(double amount) const {
        return valid() ? Box{lo - glm::dvec3(amount), hi + glm::dvec3(amount)} : *this;
    }
};
inline Box transformed(const Box &box, const glm::dmat4 &matrix) {
    Box result;
    if (box.valid())
        for (unsigned i = 0; i != 8; ++i)
            result.add(glm::dvec3(matrix * glm::dvec4((i & 1) ? box.hi.x : box.lo.x, (i & 2) ? box.hi.y : box.lo.y,
                                                      (i & 4) ? box.hi.z : box.lo.z, 1)));
    return result;
}
// Subtract double origins before rotation; never multiply absolute float positions.
inline glm::dmat4 relativePose(const glm::dmat4 &from, const glm::dmat4 &to) {
    const auto inverse = glm::inverse(glm::dmat3(from));
    glm::dmat4 result(inverse * glm::dmat3(to));
    result[3] = glm::dvec4(inverse * (glm::dvec3(to[3]) - glm::dvec3(from[3])), 1);
    return result;
}
inline double linearStretch(const glm::dmat3 &matrix) {
    double result = 0;
    for (int row = 0; row < 3; ++row)
        result = std::max(result, std::abs(matrix[0][row]) + std::abs(matrix[1][row]) + std::abs(matrix[2][row]));
    return result;
}
inline double poseUncertainty(const glm::dmat4 &from, const glm::dmat4 &to) {
    // Independently rounded far-world translations may not reconstruct an exact
    // plane. This envelope retains a GPU candidate; it never declares contact.
    double coordinate = 1;
    for (int i = 0; i < 3; ++i) coordinate = std::max({coordinate, std::abs(from[3][i]), std::abs(to[3][i])});
    return coordinate * std::numeric_limits<double>::epsilon() * 8 * linearStretch(glm::inverse(glm::dmat3(from)));
}
inline double numericalPadding(const Box &box) {
    if (!box.valid()) return 0;
    double coordinate = 1;
    for (int i = 0; i < 3; ++i) coordinate = std::max({coordinate, std::abs(box.lo[i]), std::abs(box.hi[i])});
    return coordinate * std::numeric_limits<double>::epsilon() * 8 + 1e-10;
}
struct Triangle {
    std::array<glm::dvec3, 3> points;
    uint32_t geometry = 0, primitive = 0;
    bool sameMaterialSheet = false;
    uint32_t sheetPair = 0;
    Box bounds() const {
        Box b;
        for (auto p : points) b.add(p);
        return b;
    }
};
struct Link {
    uint32_t geometry, primitive, otherGeometry, otherPrimitive;
    bool operator==(const Link &) const = default;
};
class Mesh {
    struct Node {
        Box box;
        uint32_t first = 0, count = 0, left = 0, right = 0;
    };
    std::vector<Node> nodes_;
    std::vector<uint32_t> order_;
    uint32_t buildNode(uint32_t first, uint32_t count) {
        const auto index = uint32_t(nodes_.size());
        nodes_.push_back({});
        Box box;
        for (uint32_t i = first; i != first + count; ++i) box.add(triangles[order_[i]].bounds());
        nodes_[index].box = box;
        if (count <= 8) {
            nodes_[index].first = first;
            nodes_[index].count = count;
            return index;
        }
        const auto extent = box.hi - box.lo;
        const int axis = extent.x > extent.y ? (extent.x > extent.z ? 0 : 2) : (extent.y > extent.z ? 1 : 2);
        const auto middle = first + count / 2;
        std::nth_element(order_.begin() + first, order_.begin() + middle, order_.begin() + first + count,
                         [&](auto a, auto b) {
                             const auto x = triangles[a].bounds(), y = triangles[b].bounds();
                             return x.lo[axis] + x.hi[axis] < y.lo[axis] + y.hi[axis];
                         });
        const auto left = buildNode(first, count / 2), right = buildNode(middle, count - count / 2);
        nodes_[index].left = left;
        nodes_[index].right = right;
        return index;
    }
    template <class Visit>
    void queryNode(uint32_t index, const Box &box, Visit &visit) const {
        const auto &node = nodes_[index];
        if (!node.box.overlaps(box)) return;
        if (node.count) {
            for (uint32_t i = node.first; i != node.first + node.count; ++i)
                if (triangles[order_[i]].bounds().overlaps(box)) visit(order_[i]);
        } else {
            queryNode(node.left, box, visit);
            queryNode(node.right, box, visit);
        }
    }

  public:
    std::vector<Triangle> triangles;
    std::vector<Link> internal;
    std::vector<uint32_t> primitiveCounts;
    Box bounds;
    bool complete = true;
    void build() {
        nodes_.clear();
        order_.resize(triangles.size());
        std::iota(order_.begin(), order_.end(), 0u);
        bounds = {};
        for (const auto &t : triangles) bounds.add(t.bounds());
        if (!triangles.empty()) buildNode(0, uint32_t(triangles.size()));
    }
    template <class Visit>
    void query(const Box &box, Visit visit) const {
        if (!nodes_.empty()) queryNode(0, box, visit);
    }
    size_t bytes() const {
        return sizeof(*this) + triangles.capacity() * sizeof(Triangle) + nodes_.capacity() * sizeof(Node) +
               internal.capacity() * sizeof(Link) + (order_.capacity() + primitiveCounts.capacity()) * sizeof(uint32_t);
    }
};

inline bool authoredSheetPair(const Triangle &a, const Triangle &b) {
    if (!a.sameMaterialSheet || !b.sameMaterialSheet || a.geometry != b.geometry) return false;
    // Planar reverse quads may retain opposite diagonals. Their verified
    // source pair is stronger evidence than equality of three triangle corners.
    if (a.sheetPair && a.sheetPair == b.sheetPair) return true;
    for (auto p : a.points)
        if (std::find(b.points.begin(), b.points.end(), p) == b.points.end()) return false;
    return true;
}
inline bool surfaceOverlap(const Triangle &a, const Triangle &b, double uncertainty = 0) {
    auto normal = glm::cross(a.points[1] - a.points[0], a.points[2] - a.points[0]);
    const auto length = glm::length(normal);
    if (!(length > 0)) return false;
    normal /= length;
    double scale = 1;
    for (auto p : a.points) scale = std::max(scale, glm::length(p - a.points[0]));
    for (auto p : b.points) scale = std::max(scale, glm::length(p - a.points[0]));
    for (auto p : b.points)
        if (std::abs(glm::dot(p - a.points[0], normal)) > 1e-12 * scale + std::sqrt(3.0) * uncertainty) return false;
    const auto n = glm::abs(normal);
    const int drop = n.x > n.y ? (n.x > n.z ? 0 : 2) : (n.y > n.z ? 1 : 2);
    const auto project = [drop](glm::dvec3 p) { return glm::dvec2(p[(drop + 1) % 3], p[(drop + 2) % 3]); };
    const auto cross = [](glm::dvec2 x, glm::dvec2 y) { return x.x * y.y - x.y * y.x; };
    std::array<glm::dvec2, 10> polygon{}, next{};
    for (int i = 0; i < 3; ++i) polygon[i] = project(a.points[i] - a.points[0]);
    std::array<glm::dvec2, 3> clip;
    for (int i = 0; i < 3; ++i) clip[i] = project(b.points[i] - a.points[0]);
    const double sign = cross(clip[1] - clip[0], clip[2] - clip[0]) >= 0 ? 1 : -1;
    unsigned count = 3;
    for (unsigned edge = 0; edge < 3 && count; ++edge) {
        unsigned total = 0;
        const auto x = clip[edge], d = clip[(edge + 1) % 3] - x;
        for (unsigned i = 0; i < count; ++i) {
            const auto p = polygon[i], q = polygon[(i + 1) % count];
            const double dp = sign * cross(d, p - x), dq = sign * cross(d, q - x);
            if (dp >= 0) next[total++] = p;
            if ((dp >= 0) != (dq >= 0)) next[total++] = p + (q - p) * (dp / (dp - dq));
        }
        polygon = next;
        count = total;
    }
    double area = 0;
    for (unsigned i = 0; i < count; ++i) area += cross(polygon[i], polygon[(i + 1) % count]);
    return std::abs(area) > 1e-18 * scale * scale;
}

inline std::vector<Link>
contacts(const Mesh &a, const Mesh &b, const glm::dmat4 &bToA, bool internal = false, double uncertainty = 0) {
    std::vector<Link> result;
    if (!a.complete || !b.complete) return result;
    const auto aToB = glm::inverse(bToA);
    const double padding = 1e-10 + uncertainty * linearStretch(glm::dmat3(aToB));
    for (uint32_t i = 0; i < a.triangles.size(); ++i) {
        const auto &x = a.triangles[i];
        b.query(transformed(x.bounds(), aToB).padded(padding), [&](uint32_t j) {
            if (internal && j <= i) return;
            auto y = b.triangles[j];
            if (internal && authoredSheetPair(x, y)) return;
            for (auto &p : y.points) p = glm::dvec3(bToA * glm::dvec4(p, 1));
            if (surfaceOverlap(x, y, uncertainty)) {
                result.push_back({x.geometry, x.primitive, y.geometry, y.primitive});
                if (internal) result.push_back({y.geometry, y.primitive, x.geometry, x.primitive});
            }
        });
    }
    return result;
}
} // namespace mcvr::medium
