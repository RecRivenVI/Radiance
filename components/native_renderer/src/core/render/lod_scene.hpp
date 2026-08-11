#pragma once

#include "core/vulkan/all_core_vulkan.hpp"
#include "common/shared.hpp"
#include "core/render/lod_layout.hpp"
#include <map>
#include <array>
#include <mutex>
#include <set>
#include <span>

class Framework;
class Chunks;
struct WorldPrepareContext;

namespace mcvr {
struct StaticChunkScene;
struct LodMaterials {
    std::shared_ptr<vk::DeviceLocalBuffer> records, colours;
    std::vector<uint32_t> cpu;
    std::vector<std::shared_ptr<vk::DeviceLocalImage>> images;
    std::vector<std::shared_ptr<vk::Sampler>> samplers;
    uint32_t count = 0, colourCount = 0;
    bool recorded = false;
};

struct LodSection {
    uint64_t key = 0, revision = 0, epoch = 0;
    uint32_t count = 0;
    glm::ivec3 origin{};
    uint32_t level = 0;
    uint64_t bytes = 0;
    std::shared_ptr<vk::DeviceLocalBuffer> quads, positions, indices;
    std::shared_ptr<vk::DescriptorTable> table;
    std::shared_ptr<vk::BLASBuilder> builder;
    std::shared_ptr<vk::BLAS> blas;
    std::shared_ptr<LodMaterials> decodeModels;
    std::vector<uint16_t> materialIds;
    bool recorded = false;
};

struct LodFrameTables {
    std::shared_ptr<LodMaterials> models;
    std::shared_ptr<vk::DeviceLocalBuffer> coverage, header, lightGroups, lightIndices;
    std::vector<std::shared_ptr<LodSection>> lightOwners;
    std::shared_ptr<const std::vector<glm::ivec4>> cpuCoverage;
    std::shared_ptr<const std::vector<int32_t>> primaryCoverage;
    uint64_t coverageRevision = 0;
    std::vector<std::shared_ptr<LodSection>> sections;
    uint32_t coveredSections = 0;
    uint32_t coverageMask = 0, lightGroupCount = 0, lightCount = 0;
    std::shared_ptr<const StaticChunkScene> nearScene;
    std::shared_ptr<StaticChunkScene> handoffNear;
    uint64_t emptyRevision = 0;
    uint64_t farRevision = 0;
};

// Optional, independent far-geometry ownership; never consumes a near/external chunk slot.
class LodScene {
  public:
    explicit LodScene(Framework &framework) : framework_(framework) {}
    void begin(uint64_t epoch);
    void end(uint64_t epoch);
    void clear(bool releaseNear = false);
    void nearEmpty(uint32_t slot, glm::ivec3 origin);
    std::shared_ptr<LodFrameTables> tables(Chunks &chunks, const std::shared_ptr<vk::CommandBuffer> &commands);
    void models(uint64_t epoch, std::span<const uint32_t> records, std::span<const uint32_t> colours);
    bool section(uint64_t epoch, uint64_t key, uint64_t revision, std::span<const lod::Quad> quads);
    void select(uint64_t epoch, std::span<const uint64_t> keys, lod::RootCoverage roots);
    void retain(uint64_t epoch, std::span<const uint64_t> keys);
    void record(const std::shared_ptr<vk::CommandBuffer> &commands);
    std::shared_ptr<LodMaterials> modelSnapshot();
    std::array<uint64_t, 10> counters(uint64_t epoch);
    std::vector<int64_t> coverage(uint64_t epoch, uint64_t previous);

  private:
    void finishTables(const std::shared_ptr<LodFrameTables> &tables,
                      const std::shared_ptr<vk::CommandBuffer> &commands);
    Framework &framework_;
    std::recursive_mutex mutex_;
    uint64_t epoch_ = 0, bytes_ = 0, built_ = 0;
    std::map<uint64_t, std::shared_ptr<LodSection>> sections_;
    std::set<uint64_t> selected_;
    std::set<uint64_t> pending_;
    lod::RootCoverage requestedRoots_;
    uint64_t farRevision_ = 0;
    std::shared_ptr<LodMaterials> models_;
    std::shared_ptr<vk::ComputePipeline> pipeline_;
    std::map<uint32_t, glm::ivec3> emptyNear_;
    uint64_t emptyRevision_ = 0;
    uint64_t coverageRevision_ = 0;
    std::shared_ptr<LodFrameTables> tables_;
};
} // namespace mcvr
