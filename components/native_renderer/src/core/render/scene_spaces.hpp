#pragma once

#include "pose_interpolation.hpp"

#include <glm/glm.hpp>
#include <glm/gtc/quaternion.hpp>
#include <cmath>
#include <cstdint>
#include <stdexcept>
#include <optional>
#include <unordered_map>

namespace mcvr::scene {
struct SpacePose {
    glm::dmat4 matrix{1.0};
    glm::mat3 normal{1.0f};
    struct Lighting {
        bool configured = false;
        int scene = 0;
        float sky = 1;
        glm::dmat4 matrix{1};
        bool operator==(const Lighting &) const = default;
    } lighting;
    struct SectionColour {
        glm::vec4 plane{0}, target{0};
        bool operator==(const SectionColour &) const = default;
    } sectionColour;
};

// Render-thread-owned spatial identities. Geometry and appearance do not live in this table.
// Composition is cached once per mutation, rather than separately for every member.
class Spaces {
    struct Node {
        uint64_t parent;
        SpacePose local;
        SpacePose::Lighting lighting;
        SpacePose::SectionColour sectionColour;
        mutable SpacePose composed;
        uint64_t localRevision = 0;
        mutable uint64_t composedAt = 0, parentAt = 0, composedRevision = 0;
        std::optional<PoseMotion> motion;
        double sampleTime = 0;
    };
    std::unordered_map<uint64_t, Node> nodes_;
    std::unordered_map<uint64_t, SpacePose> retired_;
    uint64_t revision_ = 1;
    mutable uint64_t composition_ = 0;
    const SpacePose root_{};

    const SpacePose &resolve(uint64_t id, size_t depth) const {
        if (!id) return root_;
        if (depth > nodes_.size()) throw std::logic_error("Cyclic scene space hierarchy");
        auto &node = nodes_.at(id);
        const auto &parent = resolve(node.parent, depth + 1);
        const auto parentVersion = node.parent ? nodes_.at(node.parent).composedRevision : 0;
        if (node.composedAt != node.localRevision || node.parentAt != parentVersion) {
            node.composed = {parent.matrix * node.local.matrix, parent.normal * node.local.normal};
            if (node.lighting.configured) {
                node.composed.lighting = node.lighting;
                if (node.lighting.scene == 0) node.composed.lighting.matrix = node.composed.matrix;
            } else if (parent.lighting.configured) {
                node.composed.lighting = parent.lighting;
                node.composed.lighting.matrix *= node.local.matrix;
            }
            node.composed.sectionColour = node.sectionColour.target.w != 0 ? node.sectionColour : parent.sectionColour;
            node.composedAt = node.localRevision;
            node.parentAt = parentVersion;
            node.composedRevision = ++composition_;
        }
        return node.composed;
    }

  public:
    bool contains(uint64_t id) const {
        return !id || nodes_.contains(id);
    }

    void put(uint64_t id, uint64_t parent, const SpacePose &pose) {
        if (!id || id == parent || !contains(parent)) throw std::invalid_argument("Invalid scene space parent");
        for (uint64_t ancestor = parent; ancestor; ancestor = nodes_.at(ancestor).parent)
            if (ancestor == id) throw std::invalid_argument("Scene space cannot parent itself indirectly");
        for (int c = 0; c < 4; ++c)
            for (int r = 0; r < 4; ++r)
                if (!std::isfinite(pose.matrix[c][r])) throw std::invalid_argument("Non-finite scene space pose");
        for (int c = 0; c < 3; ++c)
            for (int r = 0; r < 3; ++r)
                if (!std::isfinite(pose.normal[c][r])) throw std::invalid_argument("Non-finite scene space normal");
        if (pose.matrix[0][3] != 0 || pose.matrix[1][3] != 0 || pose.matrix[2][3] != 0 || pose.matrix[3][3] != 1)
            throw std::invalid_argument("Scene space must be affine");
        auto found = nodes_.find(id);
        if (found != nodes_.end() && !found->second.motion && found->second.parent == parent &&
            found->second.local.matrix == pose.matrix && found->second.local.normal == pose.normal)
            return;
        if (found == nodes_.end())
            nodes_.emplace(id, Node{parent, pose, {}});
        else {
            found->second.parent = parent;
            found->second.local = pose;
            found->second.motion.reset();
        }
        nodes_.at(id).localRevision = ++revision_;
    }

    void motion(uint64_t id, uint64_t parent, const PoseMotion &motion, double time) {
        motion.validate();
        const auto matrix = motion.at(time);
        put(id, parent, {matrix, glm::mat3(glm::transpose(glm::inverse(glm::dmat3(matrix))))});
        auto &node = nodes_.at(id);
        node.motion = motion;
        node.sampleTime = time;
    }

    void sample(uint64_t id, double time) {
        auto &node = nodes_.at(id);
        if (!node.motion) throw std::invalid_argument("Scene space has no source motion");
        if (time == node.sampleTime) return;
        const auto matrix = node.motion->at(time);
        const auto normal = glm::mat3(glm::transpose(glm::inverse(glm::dmat3(matrix))));
        for (int c = 0; c < 3; ++c)
            for (int r = 0; r < 3; ++r)
                if (!std::isfinite(normal[c][r])) throw std::invalid_argument("Invalid sampled source normal");
        node.sampleTime = time;
        if (matrix == node.local.matrix) return;
        node.local.matrix = matrix;
        node.local.normal = normal;
        node.localRevision = ++revision_;
    }

    void lighting(uint64_t id, const SpacePose::Lighting &lighting) {
        if (!id || !contains(id) || lighting.scene < -1 || !std::isfinite(lighting.sky) || lighting.sky < 0 ||
            lighting.sky > 1)
            throw std::invalid_argument("Invalid scene space lighting");
        for (int c = 0; c < 4; ++c)
            for (int r = 0; r < 4; ++r)
                if (!std::isfinite(lighting.matrix[c][r])) throw std::invalid_argument("Non-finite lighting space");
        auto &node = nodes_.at(id);
        if (node.lighting == lighting) return;
        node.lighting = lighting;
        node.localRevision = ++revision_;
    }

    const SpacePose &at(uint64_t id) const {
        if (id && !nodes_.contains(id)) return retired_.at(id);
        return resolve(id, 0);
    }

    void sectionColour(uint64_t id, const SpacePose::SectionColour &colour) {
        if (!id || !contains(id)) throw std::invalid_argument("Invalid section colour space");
        for (int i = 0; i < 4; ++i)
            if (!std::isfinite(colour.plane[i]) || !std::isfinite(colour.target[i]) || colour.target[i] < 0)
                throw std::invalid_argument("Invalid section colour gradient");
        auto &node = nodes_.at(id);
        if (node.sectionColour == colour) return;
        node.sectionColour = colour;
        node.localRevision = ++revision_;
    }

    uint64_t version(uint64_t id) const {
        resolve(id, 0);
        return id ? nodes_.at(id).composedRevision : 0;
    }

    void remove(uint64_t id) {
        // Source scopes remove their members and descendants before removing a parent.
        for (const auto &[child, node] : nodes_)
            if (node.parent == id) throw std::logic_error("Scene space still owns child spaces");
        if (nodes_.contains(id)) {
            // A source can disappear after this frame sampled its member requests. Keep only
            // the composed value, not a live parent dependency or permission for new submissions.
            retired_.insert_or_assign(id, resolve(id, 0));
            nodes_.erase(id);
            ++revision_;
        }
    }

    void beginFrame() {
        retired_.clear();
    }

    void clear() {
        nodes_.clear();
        retired_.clear();
        ++revision_;
    }
    size_t size() const {
        return nodes_.size();
    }
    uint64_t revision() const {
        return revision_;
    }
};
} // namespace mcvr::scene
