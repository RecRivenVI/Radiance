#pragma once

#include "core/render/chunk_scene_metadata.hpp"
#include "core/render/scene_space.hpp"
#include <set>

namespace vk {
class BLAS;
}

namespace mcvr {
// All access is under Chunks::mutex. Each world owns a different publication set,
// so an old slot/worker cannot invalidate a new world's snapshot (including ABA).
class ChunkScenePublications {
    uint64_t revision_ = 0;
    std::set<uint32_t> active_;

  public:
    void changed(uint32_t slot, bool present) {
        if (present)
            active_.insert(slot);
        else
            active_.erase(slot);
        ++revision_;
    }
    uint64_t revision() const {
        return revision_;
    }
    const auto &active() const {
        return active_;
    }
};

// Camera-independent, immutable, ordered primary-chunk suffix. Offsets are local
// to this suffix, not to the changing entity/rigid/Flywheel prefix of a frame.
struct StaticChunkScene {
    struct Instance {
        uint32_t slot, geometryOffset, groupOffset;
        glm::ivec3 origin;
        VkGeometryInstanceFlagsKHR flags;
        std::shared_ptr<vk::BLAS> blas;
    };
    std::vector<Instance> instances;
    std::vector<std::shared_ptr<const ChunkSceneMetadata>> owners;
    std::vector<uint32_t> offsets;
    std::vector<hitgroups::Id> groups;
    std::vector<uint64_t> indices, positions, materials, lastIndices, lastPositions;
    std::vector<vk::VertexFormat::InstanceAppearance> appearances;

    void reserve(size_t instanceCount, size_t geometryCount) {
        instances.reserve(instanceCount);
        owners.reserve(instanceCount);
        offsets.reserve(instanceCount);
        groups.reserve(instanceCount + geometryCount);
        indices.reserve(geometryCount);
        positions.reserve(geometryCount);
        materials.reserve(geometryCount);
        lastIndices.reserve(geometryCount);
        lastPositions.reserve(geometryCount);
        appearances.reserve(geometryCount);
    }

    size_t storageBytes() const {
        return instances.capacity() * sizeof(Instance) + owners.capacity() * sizeof(owners[0]) +
               offsets.capacity() * sizeof(uint32_t) + groups.capacity() * sizeof(hitgroups::Id) +
               (indices.capacity() + positions.capacity() + materials.capacity() + lastIndices.capacity() +
                lastPositions.capacity()) *
                   sizeof(uint64_t) +
               appearances.capacity() * sizeof(appearances[0]);
    }

    void add(uint32_t slot,
             glm::ivec3 origin,
             std::shared_ptr<vk::BLAS> blas,
             const std::shared_ptr<const ChunkSceneMetadata> &metadata) {
        if (!metadata || !blas) throw std::invalid_argument("Unpublished static chunk");
        if (indices.size() > UINT32_MAX - metadata->indices.size() ||
            groups.size() > UINT32_MAX - metadata->groups.size())
            throw std::overflow_error("Static chunk scene offsets exhausted");
        const VkTransformMatrixKHR identity{{{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}}};
        instances.push_back({slot, uint32_t(indices.size()), uint32_t(groups.size()), origin,
                             metadata->faces.instanceFlags(identity), std::move(blas)});
        owners.push_back(metadata);
        offsets.push_back(uint32_t(indices.size()));
        metadata->append(false, groups, indices, positions, materials, lastIndices, lastPositions, appearances);
    }

    void appendHeader(uint32_t geometryBase,
                      std::vector<uint32_t> &frameOffsets,
                      std::vector<hitgroups::Id> &frameGroups) const {
        if (geometryBase > UINT32_MAX - indices.size()) throw std::overflow_error("Scene geometry offsets exhausted");
        for (auto offset : offsets) frameOffsets.push_back(geometryBase + offset);
        frameGroups.insert(frameGroups.end(), groups.begin(), groups.end());
    }

    void append(uint32_t geometryBase,
                std::vector<uint32_t> &frameOffsets,
                std::vector<hitgroups::Id> &frameGroups,
                std::vector<uint64_t> &frameIndices,
                std::vector<uint64_t> &framePositions,
                std::vector<uint64_t> &frameMaterials,
                std::vector<uint64_t> &frameLastIndices,
                std::vector<uint64_t> &frameLastPositions,
                std::vector<vk::VertexFormat::InstanceAppearance> &frameAppearances) const {
        appendHeader(geometryBase, frameOffsets, frameGroups);
        frameIndices.insert(frameIndices.end(), indices.begin(), indices.end());
        framePositions.insert(framePositions.end(), positions.begin(), positions.end());
        frameMaterials.insert(frameMaterials.end(), materials.begin(), materials.end());
        frameLastIndices.insert(frameLastIndices.end(), lastIndices.begin(), lastIndices.end());
        frameLastPositions.insert(frameLastPositions.end(), lastPositions.begin(), lastPositions.end());
        frameAppearances.insert(frameAppearances.end(), appearances.begin(), appearances.end());
    }

    template <class DefineInstance>
    void appendInstances(glm::dvec3 camera,
                         uint32_t instanceBase,
                         uint32_t groupBase,
                         std::vector<glm::mat4> &lastTransforms,
                         DefineInstance &&define) const {
        for (size_t i = 0; i < instances.size(); ++i) {
            const auto &instance = instances[i];
            const auto translation = glm::dvec3(instance.origin) - camera;
            glm::mat4 previous(1);
            previous[3] = glm::vec4(glm::vec3(translation), 1);
            const auto transform = mcvr::space::affineRows<VkTransformMatrixKHR>(previous);
            define(transform, instanceBase + uint32_t(i), groupBase + instance.groupOffset, instance.flags,
                   instance.blas);
            // Static chunks intentionally use current camera-relative transform
            // and zero previous geometry addresses, as in the uncached path.
            lastTransforms.push_back(previous);
        }
    }
};

class StaticChunkSceneCache {
    std::shared_ptr<const ChunkScenePublications> publications_;
    uint64_t revision_ = 0, builds_ = 0;
    std::shared_ptr<const StaticChunkScene> value_;

  public:
    template <class Factory>
    const std::shared_ptr<const StaticChunkScene> &get(const std::shared_ptr<ChunkScenePublications> &publications,
                                                       Factory &&factory) {
        if (!value_ || publications_ != publications || revision_ != publications->revision()) {
            auto next = factory(publications->active());
            if (!next) throw std::invalid_argument("Missing static chunk scene snapshot");
            value_ = std::move(next);
            publications_ = publications;
            revision_ = publications->revision();
            ++builds_;
        }
        return value_;
    }
    uint64_t builds() const {
        return builds_;
    }
    void reset() {
        value_.reset();
        publications_.reset();
    }
};
} // namespace mcvr
