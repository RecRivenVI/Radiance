#pragma once

#include "common/shared.hpp"
#include "common/singleton.hpp"
#include "core/all_extern.hpp"
#include "core/vulkan/all_core_vulkan.hpp"

#include "core/render/emission.hpp"
#include "core/render/chunk_scene_metadata.hpp"
#include "core/render/scene_resource_version.hpp"
#include "core/render/section_handoff_state.hpp"
#include "core/render/static_chunk_scene.hpp"
#include "core/render/external_chunk_handle.hpp"
#include "core/render/world.hpp"
#include "core/render/chunk_build_worker.hpp"
#include "core/render/chunk_scheduling.hpp"

#include <chrono>
#include <atomic>
#include <condition_variable>
#include <deque>
#include <list>
#include <map>
#include <optional>
#include <mutex>
#include <queue>
#include <set>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
#include <thread>

class Framework;
class FrameResourceRetainer;

struct ChunkPackedData {
    int32_t x;
    int32_t y;
    int32_t z;
    uint32_t geometryCount;
    uint32_t lightCount = 0;
    VkDeviceAddress lightBufferAddress = 0;
};
static_assert(sizeof(ChunkPackedData) == 32);

struct ChunkBuildTask {
    int x = 0, y = 0, z = 0;
    int64_t id;
    int64_t generation = -1;
    int geometryCount;
    int *geometryTypes;
    const char **geometryGroupNames;
    int *geometryMaterialFlags;
    int *geometryTextures;
    int *vertexFormats;
    int *vertexCounts;
    vk::VertexFormat::PBRVertex **vertices;
    int priority = 0;
    bool collectEmission = true;
};

struct ChunkBuildData : public SharedObject<ChunkBuildData> {
    int64_t id;
    uint32_t slotGeneration = 0;
    mcvr::scene::Completion completion = mcvr::scene::Completion::pending;
    int priority = 0;
    std::chrono::steady_clock::time_point queuedAt = std::chrono::steady_clock::now();
    std::optional<glm::dvec3> schedulingPosition;
    glm::dvec3 schedulingHalfExtents{8};
    uint64_t schedulingSource = 0;
    int x, y, z;
    int64_t version;
    bool collectChunkEmission = false;
    uint32_t allVertexCount;
    uint32_t allIndexCount;
    uint32_t geometryCount;
    std::vector<World::GeometryTypes> geometryTypes;
    std::vector<std::string> geometryGroupNames;
    std::vector<uint32_t> geometryMaterialFlags;
    std::vector<std::vector<vk::VertexFormat::PBRVertex>> vertices;
    std::vector<std::vector<uint32_t>> indices;
    std::vector<VkDeviceAddress> indexBufferAddresses;
    std::vector<VkDeviceAddress> positionBufferAddresses;
    std::vector<VkDeviceAddress> materialBufferAddresses;
    std::shared_ptr<vk::DeviceLocalBuffer> indexBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> positionBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> materialBuffer;
    std::shared_ptr<vk::BLAS> blas;
    std::shared_ptr<vk::BLASBuilder> blasBuilder;
    std::vector<LightInfo> lightInfos;
    std::vector<std::vector<uint32_t>> topologyVertexFlags;
    std::shared_ptr<vk::DeviceLocalBuffer> lightBuffer;
    uint32_t lightCount = 0;

    ChunkBuildData(int64_t id,
                   int x,
                   int y,
                   int z,
                   int64_t version,
                   bool collectChunkEmission,
                   uint32_t allVertexCount,
                   uint32_t allIndexCount,
                   uint32_t geometryCount,
                   std::vector<World::GeometryTypes> &&geometryTypes,
                   std::vector<std::string> &&geometryGroupNames,
                   std::vector<uint32_t> &&geometryMaterialFlags,
                   std::vector<std::vector<vk::VertexFormat::PBRVertex>> &&vertices,
                   std::vector<std::vector<uint32_t>> &&indices);

    void buildLightInfos(const Emission &emission);
    void buildLightBuffer(const std::shared_ptr<vk::VMA> &vma,
                          const std::shared_ptr<vk::Device> &device,
                          bool persistStaging = true);
};

struct Chunk1;

struct ChunkBuildDataBatch : public SharedObject<ChunkBuildDataBatch> {
    struct GeometryUpload {
        std::shared_ptr<vk::DeviceLocalBuffer> buffer;
        VkDeviceSize stagingOffset = 0, bytes = 0;
    };

    std::vector<std::shared_ptr<ChunkBuildData>> batchData;
    // Transient upload source shared by the batch. The destinations are each chunk's own
    // geometry buffers, so a published chunk never keeps another chunk's memory alive.
    std::shared_ptr<vk::HostVisibleBuffer> geometryStaging;
    std::vector<GeometryUpload> geometryUploads;
    std::shared_ptr<vk::BLASBatchBuilder> blasBatchBuilder;
    std::chrono::steady_clock::time_point submittedAt{};
    std::shared_ptr<vk::Semaphore> readySemaphore;
    uint64_t reservedInputBytes = 0;

    ChunkBuildDataBatch(std::vector<std::shared_ptr<ChunkBuildData>> &&batchData);
    ChunkBuildDataBatch(uint32_t maxBatchSize,
                        std::set<int64_t> &queuedIndex,
                        std::vector<std::shared_ptr<Chunk1>> &chunks,
                        std::vector<std::shared_ptr<ChunkBuildData>> &chunkBuildDatas,
                        glm::dvec3 cameraPos,
                        uint64_t inFlightBytes,
                        uint64_t admissionSequence,
                        uint64_t byteBudget,
                        uint64_t &sourceCursor);
    void build();
};

class ChunkBuildScheduler : public SharedObject<ChunkBuildScheduler> {
  public:
    ChunkBuildScheduler(std::set<int64_t> &queuedIndex,
                        std::vector<std::shared_ptr<Chunk1>> &chunks,
                        std::vector<std::shared_ptr<ChunkBuildData>> &chunkBuildDatas,
                        std::recursive_mutex &mutex,
                        std::vector<ChunkPackedData> &chunkPackedData,
                        mcvr::ExternalChunkHandleTable &externalHandles,
                        uint32_t chunkBuildingBatchSize,
                        uint32_t chunkBuildingTotalBatches);
    ~ChunkBuildScheduler();
    // Never join while holding the chunk publication mutex.
    void stopWorker();
    void notifyWork();

    void tryCheckBatchesFinish();
    void waitAllBatchesFinish();
    void tryScheduleBatches(uint32_t maxBatchSize);

    uint32_t chunkBuildingBatchSize();
    uint32_t chunkBuildingTotalBatches();
    size_t pendingBuildCount() const;
    size_t activeBatchCount() const;
    std::pair<uint64_t, uint64_t> pendingInput() const;

    // Diagnostics only: builds waiting for admission and submitted batches not yet completed.
    struct Census {
        size_t queued = 0, batches = 0, chunks = 0;
        uint64_t bytes = 0; // device-local geometry and BLAS storage of in-flight batches
    };
    Census census() const;
    void collectInFlightBuffers(std::unordered_set<const void *> &buffers) const;

  private:
    void scheduleBatches(uint32_t maxBatchSize);
    void pollBatches(bool wait);
    void publishCompleted();
    std::set<int64_t> &queuedIndex_;
    std::vector<std::shared_ptr<Chunk1>> &chunks_;
    std::vector<std::shared_ptr<ChunkBuildData>> &chunkBuildDatas_;
    std::recursive_mutex &mutex_;
    std::vector<ChunkPackedData> &chunkPackedData_;
    mcvr::ExternalChunkHandleTable &externalHandles_;

    std::queue<std::shared_ptr<vk::Fence>> freeFences_;
    std::queue<std::shared_ptr<vk::CommandBuffer>> freeCommandBuffers_;
    std::list<std::shared_ptr<vk::Fence>> buildingFences_;
    std::list<std::shared_ptr<vk::CommandBuffer>> buildingCommandBuffers_;
    std::list<std::shared_ptr<ChunkBuildDataBatch>> buildingBatches_;
    struct Completed {
        std::shared_ptr<ChunkBuildDataBatch> batch;
    };
    std::vector<Completed> completed_;
    std::shared_ptr<vk::CommandPool> commandPool_;
    mutable std::mutex pipelineMutex_;
    mcvr::ChunkBuildWorker worker_;
    glm::dvec3 camera_{}; // Updated under mutex_; background selection never reads the mutable world.
    std::atomic<uint64_t> selectedCount_{0}, selectedBytes_{0};
    std::atomic<uint32_t> preparingBatches_{0};
    bool useSecondaryQueue_ = false;
    uint64_t admissionSequence_ = 0;
    uint64_t sourceCursor_ = UINT64_MAX;
    mcvr::chunkScheduling::UploadPacer uploadPacer_{mcvr::chunkScheduling::Clock::now()};

    uint32_t chunkBuildingBatchSize_;
    uint32_t chunkBuildingTotalBatches_;
};

struct ChunkRenderData : public SharedObject<ChunkRenderData> {
    int x, y, z;
    std::shared_ptr<vk::BLAS> blas;
    std::shared_ptr<std::vector<VkDeviceAddress>> indexBufferAddresses;
    std::shared_ptr<std::vector<VkDeviceAddress>> positionBufferAddresses;
    std::shared_ptr<std::vector<VkDeviceAddress>> materialBufferAddresses;
    std::shared_ptr<vk::DeviceLocalBuffer> indexBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> positionBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> materialBuffer;
    std::shared_ptr<std::vector<LightInfo>> lightInfos;
    std::shared_ptr<vk::DeviceLocalBuffer> lightBuffer;
    uint32_t lightCount = 0;
    uint32_t geometryCount;
    std::shared_ptr<std::vector<std::string>> geometryGroupNames;
    std::shared_ptr<std::vector<uint32_t>> geometryMaterialFlags;
};

struct Chunk1 : public SharedObject<Chunk1> {
    bool publicationHeld = false;
    bool releaseAfterHandoff = false;
    int64_t settledVersion = -1;
    std::optional<int64_t> heldEmpty;
    std::shared_ptr<ChunkBuildData> heldBuild;
    std::optional<mcvr::scene::SpacePose> retiredSpace;
    uint64_t space = 0;
    constexpr static float T_HALF = 200; // ms
    constexpr static float T_WEIGHT = 1.0;

    constexpr static float D_HALF = 128; // blocks
    constexpr static float D_SENSITIVITY = 1.35;
    constexpr static float D_PRIORITY_FLOOR = 0.4;
    constexpr static float D_WEIGHT = 1.1;

    int x, y, z;
    int64_t latestVersion = 0;
    int64_t desiredVersion = -1;
    std::chrono::steady_clock::time_point lastUpdate;

    std::shared_ptr<vk::BLAS> blas;
    int64_t blasVersion = -1;
    int64_t lastTracedVersion = -2;
    bool lastTracedPresent = false;
    std::shared_ptr<std::vector<VkDeviceAddress>> indexBufferAddresses;
    std::shared_ptr<std::vector<VkDeviceAddress>> positionBufferAddresses;
    std::shared_ptr<std::vector<VkDeviceAddress>> materialBufferAddresses;
    std::shared_ptr<vk::DeviceLocalBuffer> indexBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> positionBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> materialBuffer;
    std::shared_ptr<std::vector<LightInfo>> lightInfos;
    std::shared_ptr<vk::DeviceLocalBuffer> lightBuffer;
    uint32_t lightCount = 0;
    uint32_t geometryCount;
    std::shared_ptr<std::vector<std::string>> geometryGroupNames;
    std::shared_ptr<std::vector<uint32_t>> geometryMaterialFlags;
    bool hasCustomTransform = false;
    glm::dmat4 customTransform = glm::dmat4(1.0);

    mcvr::ChunkSceneCache sceneMetadataCache;
    std::shared_ptr<mcvr::ChunkScenePublications> scenePublications;
    uint32_t sceneSlot = 0;
    const std::shared_ptr<mcvr::ChunkSceneMetadata> &sceneMetadata();

    float buildFactor(std::chrono::steady_clock::time_point currentTime, glm::vec3 cameraPos, glm::vec3 chunkPos);

    bool enqueue(std::shared_ptr<ChunkBuildData> chunkBuildData);
    void markDirty(int64_t generation);
    void invalidate(int64_t generation = -1);
    void retainResources(FrameResourceRetainer &frr);
    void releaseEmissionResources(FrameResourceRetainer &frr);
    std::shared_ptr<ChunkRenderData> tryGetValid();
};

class Chunks : public SharedObject<Chunks> {
    friend World;

  public:
    Chunks(std::shared_ptr<Framework> framework);

    void reset(uint32_t numChunks, uint32_t sizeX, uint32_t sizeY, uint32_t sizeZ, int32_t bottomSectionCoord);
    void resetScheduler();
    void resetFrame();
    void markChunkDirty(int64_t handle, int64_t generation);
    void invalidateChunk(int64_t handle, int64_t generation = -1);
    void relocateChunk(int64_t handle, int x, int y, int z, int64_t generation = -1);
    void queueChunkBuild(ChunkBuildTask task);
    int64_t allocateExternalChunk();
    void bindExternalChunkSpace(int64_t id, uint64_t space, const glm::dmat4 &local);
    void releaseExternalChunk(int64_t id);
    void setCollectChunkEmission(bool collect);

    bool isChunkReady(int64_t id);
    uint64_t beginHandoff();
    void holdHandoff(uint64_t handoff, int64_t section);
    bool holdChangedHandoff(uint64_t handoff, int64_t section, int64_t removalVersion);
    void sealHandoff(uint64_t handoff);
    void joinHandoff(uint64_t handoff, uint64_t other);
    bool commitHandoff(uint64_t handoff);
    std::string handoffStatus(uint64_t handoff);
    void retireHeldSpace(uint64_t space, const mcvr::scene::SpacePose &pose);
    void closeHandoff(uint64_t handoff);
    uint32_t countReadyPrimaryChunks();
    std::string performanceSnapshot();
    std::pair<uint64_t, uint64_t> buildBacklog();

    void close();
    // GPU idle, client left its world: drop every slot, queued build and in-flight batch until the
    // next world initializes its section grid. Unlike close(), the renderer keeps running.
    void releaseScene();

    std::recursive_mutex &mutex();
    std::vector<std::shared_ptr<Chunk1>> &chunks();
    std::shared_ptr<ChunkBuildScheduler> chunkBuildScheduler();
    // Diagnostics only (RADIANCE_CHUNK_CENSUS=1): tracked chunk buffers alive although neither their
    // slot nor an in-flight batch references them. Empty when disabled.
    std::string orphanCensus();
    std::shared_ptr<vk::DeviceLocalBuffer> chunkPackedData();
    glm::ivec4 chunkGridInfo();
    void setChunkStorageSectionPos(glm::ivec3 sectionPos);
    glm::ivec4 chunkStorageSectionPos();
    uint32_t primaryChunkCount() const {
        return primaryChunkCount_;
    }
    // Caller holds mutex(); the immutable value is safe to retain for in-flight frames.
    const std::shared_ptr<const mcvr::StaticChunkScene> &staticScene();
    uint64_t staticSceneBuilds() const {
        return staticSceneCache_.builds();
    }

  private:
    using Handoff = mcvr::scene::SectionHandoffState<Chunk1>;
    uint64_t nextHandoff_ = 0;
    std::map<uint64_t, std::shared_ptr<Handoff>> handoffs_;
    std::map<int64_t, std::weak_ptr<Handoff>> heldSections_;
    void finishHandoff(const std::shared_ptr<Handoff> &handoff);
    void releaseHandoffSection(int64_t handle, const std::shared_ptr<Chunk1> &chunk, bool discard);
    void allocateChunkPackedDataBuffers();
    void releaseEmissionResources();

    std::recursive_mutex mutex_;
    std::vector<std::shared_ptr<Chunk1>> chunks_;
    std::shared_ptr<mcvr::ChunkScenePublications> scenePublications_;
    mcvr::StaticChunkSceneCache staticSceneCache_;
    std::vector<ChunkPackedData> chunkPackedData_;
    std::vector<std::shared_ptr<vk::DeviceLocalBuffer>> chunkPackedDataBuffers_;
    std::vector<std::shared_ptr<ChunkBuildData>> chunkBuildDatas_;
    std::set<int64_t> queuedIndex_;
    std::shared_ptr<ChunkBuildScheduler> chunkBuildScheduler_;

    int32_t sizeX_ = 0;
    int32_t sizeY_ = 0;
    int32_t sizeZ_ = 0;
    int32_t bottomSectionCoord_ = 0;
    glm::ivec3 chunkStorageSectionPos_ = glm::ivec3(0);
    uint32_t primaryChunkCount_ = 0;
    mcvr::ExternalChunkHandleTable externalHandles_;
};
