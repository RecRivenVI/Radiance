#pragma once

#include "core/render/chunk_scene_metadata.hpp"
#include "core/render/scene_space.hpp"
#include "core/render/scene_table.hpp"
#include <map>
#include <set>
#include <array>
#include <algorithm>
#include <type_traits>

namespace vk {
class BLAS;
}

namespace mcvr {
// All access is under Chunks::mutex. Each world owns a different publication set,
// so an old slot/worker cannot invalidate a new world's snapshot (including ABA).
class ChunkScenePublications {
    uint64_t revision_ = 0;

  public:
    static constexpr uint32_t pageSize = 64;
    struct Page {
        uint64_t revision = 0;
        std::set<uint32_t> active;
    };

  private:
    std::map<uint32_t, Page> pages_;

  public:
    void changed(uint32_t slot, bool present) {
        auto &page = pages_[slot / pageSize];
        if (present)
            page.active.insert(slot);
        else
            page.active.erase(slot);
        ++page.revision;
        ++revision_;
    }
    uint64_t revision() const {
        return revision_;
    }
    const auto &pages() const {
        return pages_;
    }
};

// Camera-independent, immutable, ordered primary-chunk suffix. Offsets are local
// to this suffix, not to the changing entity/rigid/Flywheel prefix of a frame.
struct StaticChunkPage {
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

// A publication replaces only dirty pages. Each page owns immutable geometry and
// resources, so old frames can share unchanged pages without pinning replaced ones.
struct StaticChunkScene {
  private:
    std::array<std::shared_ptr<SceneTable::Directory>, 6> tables_ = [] {
        std::array<std::shared_ptr<SceneTable::Directory>, 6> result;
        for (auto &table : result) table = std::make_shared<SceneTable::Directory>();
        return result;
    }();

  public:
    StaticChunkScene() = default;
    StaticChunkScene(const StaticChunkScene &) = delete;
    StaticChunkScene &operator=(const StaticChunkScene &) = delete;
    std::vector<std::shared_ptr<const StaticChunkPage>> pages;
    size_t instanceCount = 0, geometryCount = 0, groupCount = 0, bytes = 0;

    void add(std::shared_ptr<const StaticChunkPage> page) {
        if (!page || page->instances.empty()) throw std::invalid_argument("Empty static chunk page");
        if (geometryCount > UINT32_MAX - page->indices.size() || groupCount > UINT32_MAX - page->groups.size())
            throw std::overflow_error("Static scene offsets exhausted");
        instanceCount += page->instances.size();
        geometryCount += page->indices.size();
        groupCount += page->groups.size();
        bytes += page->storageBytes();
        auto append = [&](size_t table, const auto &records) {
            tables_[table]->add({std::as_bytes(std::span(records)), page});
        };
        append(0, page->indices);
        append(1, page->positions);
        append(2, page->materials);
        append(3, page->lastIndices);
        append(4, page->lastPositions);
        append(5, page->appearances);
        pages.push_back(std::move(page));
    }
    size_t storageBytes() const {
        size_t result = bytes + pages.capacity() * sizeof(pages[0]);
        for (const auto &table : tables_)
            result += sizeof(*table) + table->segments.capacity() * sizeof(SceneTable::Segment);
        return result;
    }
    template <class T>
    SceneTable table(std::span<const T> changing, size_t prefixCount, std::vector<T> StaticChunkPage::*member) const {
        auto result = sceneTable<T>(changing, prefixCount);
        size_t field = 5;
        if constexpr (std::is_same_v<T, uint64_t>) {
            const std::array fields{&StaticChunkPage::indices, &StaticChunkPage::positions, &StaticChunkPage::materials,
                                    &StaticChunkPage::lastIndices, &StaticChunkPage::lastPositions};
            auto found = std::find(fields.begin(), fields.end(), member);
            if (found == fields.end()) throw std::invalid_argument("Unknown static scene field");
            field = size_t(found - fields.begin());
        } else
            static_assert(std::is_same_v<T, vk::VertexFormat::InstanceAppearance>);
        result.directory = tables_[field];
        return result;
    }
    template <class Visit>
    void eachInstance(Visit &&visit) const {
        for (const auto &page : pages)
            for (size_t i = 0; i < page->instances.size(); ++i) visit(*page, i);
    }
    void appendHeader(uint32_t geometryBase, std::vector<uint32_t> &offsets, std::vector<hitgroups::Id> &groups) const {
        if (geometryBase > UINT32_MAX - geometryCount) throw std::overflow_error("Scene geometry offsets exhausted");
        for (const auto &page : pages) {
            page->appendHeader(geometryBase, offsets, groups);
            geometryBase += uint32_t(page->indices.size());
        }
    }
    void append(uint32_t geometryBase,
                std::vector<uint32_t> &offsets,
                std::vector<hitgroups::Id> &groups,
                std::vector<uint64_t> &indices,
                std::vector<uint64_t> &positions,
                std::vector<uint64_t> &materials,
                std::vector<uint64_t> &lastIndices,
                std::vector<uint64_t> &lastPositions,
                std::vector<vk::VertexFormat::InstanceAppearance> &appearances) const {
        if (geometryBase > UINT32_MAX - geometryCount) throw std::overflow_error("Scene geometry offsets exhausted");
        for (const auto &page : pages) {
            page->append(geometryBase, offsets, groups, indices, positions, materials, lastIndices, lastPositions,
                         appearances);
            geometryBase += uint32_t(page->indices.size());
        }
    }
    template <class DefineInstance>
    void appendInstances(glm::dvec3 camera,
                         uint32_t instanceBase,
                         uint32_t groupBase,
                         std::vector<glm::mat4> &history,
                         DefineInstance &&define) const {
        for (const auto &page : pages) {
            page->appendInstances(camera, instanceBase, groupBase, history, define);
            instanceBase += uint32_t(page->instances.size());
            groupBase += uint32_t(page->groups.size());
        }
    }
};

class StaticChunkSceneCache {
    std::shared_ptr<const ChunkScenePublications> publications_;
    uint64_t revision_ = 0, builds_ = 0;
    std::shared_ptr<const StaticChunkScene> value_;
    struct PageVersion {
        uint64_t revision;
        std::shared_ptr<const StaticChunkPage> value;
    };
    std::map<uint32_t, PageVersion> pages_;
    uint64_t pageBuilds_ = 0;

  public:
    template <class Factory>
    const std::shared_ptr<const StaticChunkScene> &get(const std::shared_ptr<ChunkScenePublications> &publications,
                                                       Factory &&factory) {
        if (!value_ || publications_ != publications || revision_ != publications->revision()) {
            auto next = std::make_shared<StaticChunkScene>();
            std::map<uint32_t, PageVersion> versions;
            uint64_t rebuilt = 0;
            const bool sameWorld = publications_ == publications;
            next->pages.reserve(publications->pages().size());
            for (const auto &[id, state] : publications->pages()) {
                if (state.active.empty()) continue;
                auto old = pages_.find(id);
                std::shared_ptr<const StaticChunkPage> page;
                if (sameWorld && old != pages_.end() && old->second.revision == state.revision) {
                    page = old->second.value;
                } else {
                    page = factory(state.active);
                    ++rebuilt;
                }
                next->add(page);
                versions.emplace(id, PageVersion{state.revision, std::move(page)});
            }
            value_ = std::move(next);
            pages_.swap(versions);
            publications_ = publications;
            revision_ = publications->revision();
            pageBuilds_ += rebuilt;
            ++builds_;
        }
        return value_;
    }
    uint64_t builds() const {
        return builds_;
    }
    uint64_t pageBuilds() const {
        return pageBuilds_;
    }
    void reset() {
        value_.reset();
        publications_.reset();
        pages_.clear();
    }
};
} // namespace mcvr
