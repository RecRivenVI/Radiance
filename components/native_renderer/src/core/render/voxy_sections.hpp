#pragma once

#include "core/vulkan/all_core_vulkan.hpp"
#include "common/shared.hpp"
#include "core/render/voxy_layout.hpp"
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
struct VoxyModels {
    std::shared_ptr<vk::DeviceLocalBuffer> records, colours;
    std::vector<uint32_t> cpu;
    std::vector<std::shared_ptr<vk::DeviceLocalImage>> images;
    std::vector<std::shared_ptr<vk::Sampler>> samplers;
    uint32_t count = 0, colourCount = 0;
    bool recorded = false;
};

struct VoxySection {
    uint64_t key = 0, revision = 0, epoch = 0;
    uint32_t count = 0;
    glm::ivec3 origin{};
    uint32_t level = 0;
    uint64_t bytes = 0;
    std::shared_ptr<vk::DeviceLocalBuffer> quads, positions, indices;
    std::shared_ptr<vk::DescriptorTable> table;
    std::shared_ptr<vk::BLASBuilder> builder;
    std::shared_ptr<vk::BLAS> blas;
    std::shared_ptr<VoxyModels> decodeModels;
    bool recorded = false;
};

struct VoxyFrameTables {
    std::shared_ptr<VoxyModels> models;
    std::shared_ptr<vk::DeviceLocalBuffer> coverage, header;
    std::shared_ptr<const StaticChunkScene> nearScene;
    std::shared_ptr<StaticChunkScene> handoffNear;
    uint64_t emptyRevision = 0;
    uint64_t farRevision = 0;
};

// Optional, independent far-geometry ownership; never consumes a near/external chunk slot.
class VoxySections {
  public:
    explicit VoxySections(Framework &framework) : framework_(framework) {}
    void begin(uint64_t epoch);
    void end(uint64_t epoch);
    void clear(bool releaseNear = false);
    void nearEmpty(uint32_t slot, glm::ivec3 origin);
    std::shared_ptr<VoxyFrameTables> tables(Chunks &chunks, const std::shared_ptr<vk::CommandBuffer> &commands);
    void models(uint64_t epoch, std::span<const uint32_t> records, std::span<const uint32_t> colours);
    bool section(uint64_t epoch, uint64_t key, uint64_t revision, std::span<const uint64_t> quads);
    void select(uint64_t epoch, std::span<const uint64_t> keys, voxy::RootCoverage roots);
    void retain(uint64_t epoch, std::span<const uint64_t> keys);
    void record(const std::shared_ptr<vk::CommandBuffer> &commands);
    std::vector<std::shared_ptr<VoxySection>> selected();
    std::shared_ptr<VoxyModels> modelSnapshot();
    std::array<uint64_t, 5> counters(uint64_t epoch);

  private:
    Framework &framework_;
    std::recursive_mutex mutex_;
    uint64_t epoch_ = 0, bytes_ = 0, built_ = 0;
    std::map<uint64_t, std::shared_ptr<VoxySection>> sections_;
    std::set<uint64_t> selected_;
    std::set<uint64_t> pending_;
    voxy::RootCoverage requestedRoots_;
    uint64_t farRevision_ = 0;
    std::shared_ptr<VoxyModels> models_;
    std::shared_ptr<vk::ComputePipeline> pipeline_;
    std::map<uint32_t, glm::ivec3> emptyNear_;
    uint64_t emptyRevision_ = 0;
    std::shared_ptr<VoxyFrameTables> tables_;
};
} // namespace mcvr
