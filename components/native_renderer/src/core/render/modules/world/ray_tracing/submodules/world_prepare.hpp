#pragma once

#include "common/shared.hpp"
#include "common/singleton.hpp"
#include "core/all_extern.hpp"
#include "core/vulkan/all_core_vulkan.hpp"
#include "core/render/rigid_instances.hpp"
#include "core/render/hit_groups.hpp"
#include "core/render/scene_table.hpp"
#include "core/render/lod_layout.hpp"

#include <map>
#include <mutex>
#include <queue>
#include <unordered_map>

namespace mcvr {
struct StaticChunkScene;
struct LodFrameTables;
} // namespace mcvr

class Framework;
class FrameworkContext;
class RayTracingModule;
struct RayTracingModuleContext;
struct Entity;
struct Chunk1;
struct RigidInstanceBatch;

struct WorldPrepareContext;

class WorldPrepare : public SharedObject<WorldPrepare> {
    friend RayTracingModule;
    friend RayTracingModuleContext;
    friend WorldPrepareContext;

  public:
    WorldPrepare();

    void init(std::shared_ptr<Framework> framework, std::shared_ptr<RayTracingModule> rayTracingModule);

    void build();
    // GPU idle, client left its world: drop the last frames' TLAS, scene buffers and history, which
    // hold every chunk, entity and Flywheel BLAS of those frames until the next world frame.
    void releaseScene();

  private:
    using EntityRenderDataBatch = std::map<uint64_t, std::pair<std::shared_ptr<Entity>, VkTransformMatrixKHR>>;
    using ChunkTransformBatch = std::map<std::shared_ptr<Chunk1>, glm::dmat4, std::owner_less<std::shared_ptr<Chunk1>>>;

    std::weak_ptr<Framework> framework_;
    std::weak_ptr<RayTracingModule> rayTracingModule_;

    std::map<uint32_t, std::queue<EntityRenderDataBatch>> previousEntityRenderDataBatches_;
    EntityRenderDataBatch emptyEntityRenderDataBatch_;
    std::recursive_mutex entityRenderDataBatchesMtx_;

    std::map<uint32_t, std::queue<ChunkTransformBatch>> previousChunkTransformBatches_;
    ChunkTransformBatch emptyChunkTransformBatch_;
    std::recursive_mutex chunkTransformBatchesMtx_;

    using SceneInstanceKey = std::pair<uint64_t, uint64_t>;
    std::map<uint32_t, std::map<SceneInstanceKey, glm::mat4>> previousInstanceTransforms_;
    std::recursive_mutex instanceTransformMtx_;

    // Rigid persistent-model history per view. Each frame retains the instance batch whose model
    // buffers its recorded previous-geometry addresses point into.
    struct RigidHistoryState {
        mcvr::rigid::HistoryFrame frame;
        std::shared_ptr<RigidInstanceBatch> owner;
    };
    std::map<uint32_t, std::queue<std::shared_ptr<RigidHistoryState>>> previousRigidHistory_;
    std::map<uint32_t, std::map<std::pair<uint64_t, uint64_t>, mcvr::lod::HistoryEntry>> previousLodHistory_;

    std::vector<std::shared_ptr<WorldPrepareContext>> contexts_;
};

struct WorldPrepareContext : public SharedObject<WorldPrepareContext> {
    std::weak_ptr<FrameworkContext> frameworkContext;
    std::weak_ptr<RayTracingModuleContext> rayTracingModuleContext;
    std::weak_ptr<WorldPrepare> worldPrepare;

    std::shared_ptr<vk::TLAS> tlas;
    std::shared_ptr<vk::TLASBuilder> tlasBuilder;

    std::shared_ptr<vk::DeviceLocalBuffer> blasOffsetsBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> indexBufferAddr;
    std::shared_ptr<vk::DeviceLocalBuffer> positionBufferAddr;
    std::shared_ptr<vk::DeviceLocalBuffer> materialBufferAddr;
    std::shared_ptr<vk::DeviceLocalBuffer> lastIndexBufferAddr;
    std::shared_ptr<vk::DeviceLocalBuffer> lastPositionBufferAddr;
    std::shared_ptr<vk::DeviceLocalBuffer> lastObjToWorldMat;
    std::shared_ptr<vk::DeviceLocalBuffer> instanceAppearanceBuffer;
    std::shared_ptr<vk::DeviceLocalBuffer> waterExclusionBuffer;
    // Interned hit-group ids, one per instance record plus one per geometry.
    std::vector<mcvr::hitgroups::Id> hitGroups;
    mcvr::hitgroups::Layout hitGroupLayout;

    // Recording-context scratch only. SceneTableCache copies dynamic content before returning;
    // submitted GPU versions never borrow these vectors.
    struct MetadataScratch {
        std::vector<uint32_t> offsets;
        std::vector<uint64_t> indices, positions, materials, lastIndices, lastPositions;
        std::vector<glm::mat4> transforms;
        std::vector<vk::VertexFormat::InstanceAppearance> appearances;
        std::vector<glm::vec4> water;

        void clear() {
            offsets.clear();
            indices.clear();
            positions.clear();
            materials.clear();
            lastIndices.clear();
            lastPositions.clear();
            transforms.clear();
            appearances.clear();
            water.assign(1, glm::vec4(0));
        }
        size_t capacityBytes() const {
            return offsets.capacity() * sizeof(uint32_t) +
                   (indices.capacity() + positions.capacity() + materials.capacity() + lastIndices.capacity() +
                    lastPositions.capacity()) *
                       sizeof(uint64_t) +
                   transforms.capacity() * sizeof(glm::mat4) +
                   appearances.capacity() * sizeof(vk::VertexFormat::InstanceAppearance) +
                   water.capacity() * sizeof(glm::vec4);
        }
    } metadataScratch;

    std::array<mcvr::SceneTableCache<vk::DeviceLocalBuffer>, 9> metadataTables;
    uint64_t metadataProfileEpoch = 0;
    std::array<std::vector<uint8_t>, 9> observedMetadata;

    WorldPrepareContext(std::shared_ptr<FrameworkContext> frameworkContext, std::shared_ptr<WorldPrepare> worldprepare);

    void uploadBuffer(std::vector<uint32_t> &blasOffsets,
                      std::vector<uint64_t> &indexBufferAddrs,
                      std::vector<uint64_t> &positionBufferAddrs,
                      std::vector<uint64_t> &materialBufferAddrs,
                      std::vector<uint64_t> &lastIndexBufferAddrs,
                      std::vector<uint64_t> &lastPositionBufferAddrs,
                      std::vector<glm::mat4> &lastObjToWorldMats,
                      std::vector<vk::VertexFormat::InstanceAppearance> &instanceAppearances,
                      const std::vector<glm::vec4> &waterExclusions,
                      const std::shared_ptr<const mcvr::StaticChunkScene> &staticScene,
                      size_t staticGeometryPrefix);
    void setupHitGroupSbt(const std::unordered_map<std::string, uint32_t> &hitGroupNameToIndex,
                          uint32_t fallbackHitGroupIndex,
                          uint32_t shadowHitGroupIndex,
                          mcvr::hitgroups::ResolvedLayout &resolvedLayout,
                          std::shared_ptr<vk::CommandBuffer> commandBuffer,
                          std::shared_ptr<vk::SBT> updateSbt,
                          std::shared_ptr<vk::SBT> querySbt);
    void render();
};
