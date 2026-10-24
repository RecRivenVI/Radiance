#include "core/render/chunk_trace.hpp"
#include "core/logging.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "core/render/material_faces.hpp"
#include "core/render/scene_scope.hpp"
#include "core/render/scene_space.hpp"
#include "core/render/instance_content.hpp"
#include "core/render/view_frame.hpp"
#include "core/failure_state.hpp"
#include "core/render/modules/world/ray_tracing/submodules/world_prepare.hpp"

#include "core/render/buffers.hpp"
#include "core/render/chunks.hpp"
#include "core/render/entities.hpp"
#include "core/render/instancing.hpp"
#include "core/render/voxy_sections.hpp"
#include "core/render/instancing_contract.hpp"
#include "core/render/modules/world/ray_tracing/ray_tracing_module.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"
#include "core/render/water_clip.hpp"
#include "core/render/world.hpp"
#include "core/render/world_mesh_contract.hpp"

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <unordered_set>
#include <glm/gtc/type_ptr.hpp>

WorldPrepare::WorldPrepare() {}

void WorldPrepare::init(std::shared_ptr<Framework> framework, std::shared_ptr<RayTracingModule> rayTracingModule) {
    framework_ = framework;
    rayTracingModule_ = rayTracingModule;
}

void WorldPrepare::build() {
    auto framework = framework_.lock();
    auto rayTracingModule = rayTracingModule_.lock();
    uint32_t size = framework->recordingContextCount();

    contexts_.resize(size);

    for (int i = 0; i < size; i++) {
        contexts_[i] = WorldPrepareContext::create(framework->contexts()[i], shared_from_this());
    }
}

void WorldPrepare::releaseScene() {
    {
        std::unique_lock<std::recursive_mutex> lock(entityRenderDataBatchesMtx_);
        previousEntityRenderDataBatches_.clear();
    }
    {
        std::unique_lock<std::recursive_mutex> lock(chunkTransformBatchesMtx_);
        previousChunkTransformBatches_.clear();
    }
    {
        std::unique_lock<std::recursive_mutex> lock(flywheelTransformMtx_);
        previousFlywheelTransforms_.clear();
    }
    previousRigidHistory_.clear();
    previousVoxyHistory_.clear();
    previousVoxyTables_.clear();
    for (const auto &context : contexts_) {
        if (context == nullptr) continue;
        context->tlas = nullptr;
        context->tlasBuilder = nullptr;
        context->blasOffsetsBuffer = nullptr;
        context->indexBufferAddr = nullptr;
        context->positionBufferAddr = nullptr;
        context->materialBufferAddr = nullptr;
        context->lastIndexBufferAddr = nullptr;
        context->lastPositionBufferAddr = nullptr;
        context->lastObjToWorldMat = nullptr;
        context->instanceAppearanceBuffer = nullptr;
        context->waterExclusionBuffer = nullptr;
        context->hitGroups.clear();
        for (auto &table : context->observedMetadata) std::vector<uint8_t>().swap(table);
        context->metadataProfileEpoch = 0;
        for (auto &table : context->metadataTables) table.reset();
    }
}

WorldPrepareContext::WorldPrepareContext(std::shared_ptr<FrameworkContext> frameworkContext,
                                         std::shared_ptr<WorldPrepare> worldPrepare)
    : frameworkContext(frameworkContext), worldPrepare(worldPrepare) {}

void WorldPrepareContext::uploadBuffer(std::vector<uint32_t> &blasOffsets,
                                       std::vector<uint64_t> &indexBufferAddrs,
                                       std::vector<uint64_t> &positionBufferAddrs,
                                       std::vector<uint64_t> &materialBufferAddrs,
                                       std::vector<uint64_t> &lastIndexBufferAddrs,
                                       std::vector<uint64_t> &lastPositionBufferAddrs,
                                       std::vector<glm::mat4> &lastObjToWorldMats,
                                       std::vector<vk::VertexFormat::InstanceAppearance> &instanceAppearances,
                                       const std::vector<glm::vec4> &waterExclusions,
                                       const std::shared_ptr<const mcvr::StaticChunkScene> &staticScene,
                                       size_t staticGeometryPrefix) {
    mcvr::profile::Phases profile("pt.metadata.allocate-copy");
    auto context = frameworkContext.lock();
    auto framework = context->framework.lock();
    auto vma = framework->vma();
    auto device = framework->device();
    auto cmdBuffer = context->worldCommandBuffer;

    const bool observing = mcvr::profile::enabled();
    const auto epoch = mcvr::profile::epoch.load();
    const bool cold = epoch != metadataProfileEpoch;
    mcvr::profile::Accumulated allocationTime("pt.metadata.allocation");
    mcvr::profile::Accumulated copyTime("pt.metadata.staging-copy");
    mcvr::profile::Accumulated observationTime("pt.metadata.content-check");
    mcvr::profile::Accumulated recordTime("pt.metadata.upload-record");
    std::vector<vk::CommandBuffer::BufferMemoryBarrier> pendingUploads;
    pendingUploads.reserve(9);
    uint64_t barrierGroups = 0;
    auto prepare = [&](const mcvr::SceneTable &data, size_t table, const char *name, VkBufferUsageFlags usage) {
        const auto bytes = data.size();
        if (mcvr::profile::contentAuditEnabled()) {
            mcvr::profile::Scope observation(observationTime);
            auto &previous = observedMetadata[table];
            const bool equal = !cold && data.equals(std::as_bytes(std::span(previous)));
            mcvr::profile::emit(mcvr::profile::frame, 5, name, equal ? 0 : 1, bytes);
            if (!equal) {
                previous.resize(bytes);
                data.write(std::as_writable_bytes(std::span(previous)));
            }
        }
        // Resident models keep their addresses and appearance records across frames.
        // Compare the actual table content, including dynamic ends; changed tables get
        // a fresh immutable buffer, never an in-place write into an in-flight version.
        const bool reusable = true;
        mcvr::profile::Scope selectionTime("pt.metadata.select-content");
        std::shared_ptr<vk::HostVisibleBuffer> staging;
        std::vector<VkBufferCopy> uploads;
        const auto selection = metadataTables[table].acquirePatched(
            data,
            [&](const mcvr::SceneTable &input, const mcvr::SceneTablePatch &patch, const auto &) {
                std::shared_ptr<vk::DeviceLocalBuffer> buffer;
                {
                    mcvr::profile::Scope allocation(allocationTime);
                    buffer = vk::DeviceLocalBuffer::create(vma, device, false, bytes, usage);
                }
                {
                    mcvr::profile::Scope copy(copyTime);
                    if (patch.uploadBytes) {
                        staging = vk::HostVisibleBuffer::create(vma, device, patch.uploadBytes,
                                                                VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
                        auto *target = static_cast<std::byte *>(staging->mappedPtr());
                        size_t offset = 0;
                        for (const auto &upload : patch.uploads) {
                            std::memcpy(target + offset, upload.bytes.data(), upload.bytes.size());
                            uploads.push_back({offset, upload.target, upload.bytes.size()});
                            offset += upload.bytes.size();
                        }
                        staging->flush();
                        framework->frameResourceRetainer().retain(staging);
                    }
                }
                if (observing)
                    mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.host-upload", 1, patch.uploadBytes);
                return buffer;
            },
            [&](const auto &buffer, const mcvr::SceneTablePatch &patch, const auto &source) {
                mcvr::profile::Scope record(recordTime);
                if (source) {
                    // Source tables are immutable. This also covers a source uploaded earlier
                    // in the same command stream, not just a completed previous frame.
                    cmdBuffer->barriersBufferImage({{
                                                       .srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                                       .srcAccessMask = VK_ACCESS_2_MEMORY_WRITE_BIT,
                                                       .dstStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                                       .dstAccessMask = VK_ACCESS_2_TRANSFER_READ_BIT,
                                                       .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                                       .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                                       .buffer = source,
                                                   }},
                                                   {});
                    std::vector<VkBufferCopy> copies;
                    copies.reserve(patch.copies.size());
                    for (const auto &copy : patch.copies) copies.push_back({copy.source, copy.target, copy.bytes});
                    vkCmdCopyBuffer(cmdBuffer->vkCommandBuffer(), source->vkBuffer(), buffer->vkBuffer(),
                                    uint32_t(copies.size()), copies.data());
                }
                if (staging)
                    vkCmdCopyBuffer(cmdBuffer->vkCommandBuffer(), staging->vkBuffer(), buffer->vkBuffer(),
                                    uint32_t(uploads.size()), uploads.data());
                // Fresh, immutable destination: no previous reads/writes need a pre-barrier.
                const vk::CommandBuffer::BufferMemoryBarrier barrier{
                    .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                    .srcAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    .dstStageMask = VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT |
                                    VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                                    VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                    .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT,
                    .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                    .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                    .buffer = buffer,
                };
                // No table is consumed until uploadBuffer returns. Publish only the
                // changed versions together at the existing dependency boundary.
                pendingUploads.push_back(barrier);
            },
            [&](const auto &buffer) { framework->frameResourceRetainer().retain(buffer); }, reusable);
        if (observing)
            mcvr::profile::emit(mcvr::profile::frame, 5, selection.uploaded ? "metadata.upload" : "metadata.reuse", 1,
                                bytes);
        return selection.buffer;
    };
    const VkBufferUsageFlags addressUsage = VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                                            VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
                                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    auto addresses = [&](const auto &values, auto member) {
        return staticScene ? staticScene->table<uint64_t>(values, staticGeometryPrefix, member) :
                             mcvr::sceneTable<uint64_t>(values);
    };
    blasOffsetsBuffer = prepare(mcvr::sceneTable<uint32_t>(blasOffsets), 0, "metadata.offsets.changed", addressUsage);
    indexBufferAddr = prepare(addresses(indexBufferAddrs, &mcvr::StaticChunkPage::indices), 1,
                              "metadata.indices.changed", addressUsage);
    positionBufferAddr = prepare(addresses(positionBufferAddrs, &mcvr::StaticChunkPage::positions), 2,
                                 "metadata.positions.changed", addressUsage);
    materialBufferAddr = prepare(addresses(materialBufferAddrs, &mcvr::StaticChunkPage::materials), 3,
                                 "metadata.materials.changed", addressUsage);
    lastIndexBufferAddr = prepare(addresses(lastIndexBufferAddrs, &mcvr::StaticChunkPage::lastIndices), 4,
                                  "metadata.last-indices.changed", addressUsage);
    lastPositionBufferAddr = prepare(addresses(lastPositionBufferAddrs, &mcvr::StaticChunkPage::lastPositions), 5,
                                     "metadata.last-positions.changed", addressUsage);
    lastObjToWorldMat =
        prepare(mcvr::sceneTable<glm::mat4>(lastObjToWorldMats), 6, "metadata.transforms.changed", addressUsage);
    static_assert(uint64_t(UINT32_MAX) / sizeof(vk::VertexFormat::InstanceAppearance) < (1u << 24u));
    const auto totalGeometry = instanceAppearances.size() + (staticScene ? staticScene->geometryCount : 0);
    if (totalGeometry >= (1u << 24u)) throw std::overflow_error("Surface cache geometry tag capacity");
    instanceAppearanceBuffer =
        prepare(staticScene ? staticScene->table<vk::VertexFormat::InstanceAppearance>(
                                  instanceAppearances, staticGeometryPrefix, &mcvr::StaticChunkPage::appearances) :
                              mcvr::sceneTable<vk::VertexFormat::InstanceAppearance>(instanceAppearances),
                7, "metadata.appearances.changed",
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    waterExclusionBuffer = prepare(mcvr::sceneTable<glm::vec4>(waterExclusions), 8, "metadata.water.changed",
                                   VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    if (!pendingUploads.empty()) {
        mcvr::profile::Scope record(recordTime);
        cmdBuffer->barriersBufferImage(std::move(pendingUploads), {});
        ++barrierGroups;
    }
    if (observing) {
        metadataProfileEpoch = epoch;
        mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.barrier-groups", barrierGroups, 0);
    }
}

void WorldPrepareContext::render() {
    mcvr::profile::Scope profileOperation("pt.world-prepare");
    mcvr::profile::Phases profile("pt.prepare.setup");
    auto rayTracingContext = rayTracingModuleContext.lock();
    auto rayTracingModule = rayTracingContext != nullptr ? rayTracingContext->rayTracingModule.lock() : nullptr;
    auto worldPrepare1 = worldPrepare.lock();
    if (rayTracingModule == nullptr) { return; }
    if (worldPrepare1 == nullptr) { return; }

    std::shared_ptr<Framework> framework = Renderer::instance().framework();
    std::shared_ptr<FrameworkContext> context = frameworkContext.lock();
    std::shared_ptr<vk::VMA> vma = framework->vma();
    std::shared_ptr<vk::Device> device = framework->device();
    std::shared_ptr<vk::PhysicalDevice> physicalDevice = framework->physicalDevice();
    std::shared_ptr<vk::CommandBuffer> worldCommandBuffer = context->worldCommandBuffer;

    auto chunks = Renderer::instance().world()->chunks();
    auto entities = Renderer::instance().world()->entities();
    auto instancing = Renderer::instance().world()->instancing();
    auto cameraPos = Renderer::instance().world()->getCameraPos();

    auto chunkBuildScheduler = chunks->chunkBuildScheduler();
    profile.next("pt.prepare.chunk-schedule");
    if (chunkBuildScheduler != nullptr) {
        mcvr::failure::runCheckedStage([&] { chunkBuildScheduler->tryCheckBatchesFinish(); });
        mcvr::failure::runCheckedStage(
            [&] { chunkBuildScheduler->tryScheduleBatches(chunkBuildScheduler->chunkBuildingBatchSize()); });
    }

    profile.next("pt.prepare.chunk-lock");
    std::unique_lock<std::recursive_mutex> lock(chunks->mutex());


    entities->recordGpuConversion(worldCommandBuffer);
    const auto entityBlasStamp = SceneRecordingScope::active() ?
                                     -1 :
                                     context->auditGpu.begin(worldCommandBuffer->vkCommandBuffer(), "entity-blas-gpu");
    if (entities->blasBatchBuilder() != nullptr) { entities->blasBatchBuilder()->submit(worldCommandBuffer); }
    entities->recordCachedCloudBuild(worldCommandBuffer);
    entities->recordRigidModels(worldCommandBuffer);
    context->auditGpu.end(worldCommandBuffer->vkCommandBuffer(), entityBlasStamp);
    for (auto &builder : instancing->drainPendingBlasBuilders()) {
        if (builder != nullptr) builder->submit(worldCommandBuffer);
    }
    auto farSections = Renderer::instance().world()->voxy();
    if (!SceneRecordingScope::active()) farSections->record(worldCommandBuffer);

    worldCommandBuffer->barriersMemory({vk::CommandBuffer::MemoryBarrier{
        .srcStageMask = VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
        .srcAccessMask =
            VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR | VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
        .dstStageMask = VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
        .dstAccessMask = VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR,
    }});

    static const bool cacheEnabled = [] {
        const char *value = std::getenv("MCVR_CHUNK_SCENE_CACHE");
        return !value || std::string_view(value) != "0";
    }();
    static const bool staticSceneEnabled = [] {
        const char *value = std::getenv("MCVR_STATIC_SCENE_CACHE");
        return !value || std::string_view(value) != "0";
    }();
    static const bool verifyCache = [] {
        const char *value = std::getenv("MCVR_CHUNK_SCENE_VERIFY");
        return value && std::string_view(value) == "1";
    }();
    profile.next("pt.prepare.static-scene-refresh");
    const auto staticBuilds = chunks->staticSceneBuilds();
    const auto staticScene = cacheEnabled && staticSceneEnabled ? chunks->staticScene() : nullptr;
    // The retainer stores erased mutable pointers but never mutates its resources.
    if (staticScene)
        framework->frameResourceRetainer().retain(std::const_pointer_cast<mcvr::StaticChunkScene>(staticScene));
    if (mcvr::profile::enabled()) {
        mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.static-rebuild",
                            chunks->staticSceneBuilds() - staticBuilds, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.static-records",
                            staticScene ? staticScene->instanceCount : 0,
                            staticScene ? staticScene->storageBytes() : 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.static-geometries",
                            staticScene ? staticScene->geometryCount : 0, 0);
    }

    profile.next("pt.prepare.metadata-init");
    uint32_t blasAccu = 0, blasGroupAccu = 0;
    size_t staticGeometryPrefix = 0;
    std::vector<uint32_t> blasOffset;
    hitGroups.clear();
    const auto defaultGroup = mcvr::hitgroups::intern("default");
    std::vector<uint64_t> indexBufferAddrs;
    std::vector<uint64_t> positionBufferAddrs, materialBufferAddrs;
    std::vector<uint64_t> lastIndexBufferAddrs;
    std::vector<uint64_t> lastPositionBufferAddrs;
    std::vector<glm::mat4> lastObjToWorldMats;
    std::vector<vk::VertexFormat::InstanceAppearance> instanceAppearances;
    std::vector<glm::vec4> waterExclusions(1, glm::vec4(0));
    auto identityAppearance = [] {
        vk::VertexFormat::InstanceAppearance value{};
        value.colorMultiply = glm::vec4(1.0f);
        value.colorReplace = glm::vec4(1.0f);
        value.fluidProgress = 1.0f;
        return value;
    };

    // These counts are already published with the geometry. Allocate each CPU table once
    // instead of repeatedly relocating address/appearance arrays as the scene is appended.
    // Only capacity is predicted: the traversal below still validates and retains every owner.
    size_t instanceCapacity = 0, geometryCapacity = 0;
    const auto preparedEntityBatch = entities->entityBatch();
    const auto preparedInstances = instancing->preparedInstances();
    if (mcvr::profile::enabled()) {
        mcvr::profile::emit(mcvr::profile::frame, 5, "flywheel.instances", preparedInstances.size(), 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "flywheel.resident-models", instancing->residentModelCount(), 0);
    }
    const auto preparedRigid = entities->rigidInstanceBatch();
    if (preparedEntityBatch) {
        instanceCapacity += preparedEntityBatch->entities.size();
        for (const auto &entity : preparedEntityBatch->entities) geometryCapacity += entity->geometryCount;
    }
    if (preparedRigid) {
        instanceCapacity += preparedRigid->instances.size();
        for (const auto &instance : preparedRigid->instances) geometryCapacity += instance.model->geometries.size();
        if (mcvr::profile::enabled()) {
            const auto multi =
                std::count_if(preparedRigid->instances.begin(), preparedRigid->instances.end(),
                              [](const auto &instance) { return instance.model->geometries.size() > 1; });
            mcvr::profile::emit(mcvr::profile::frame, 5, "entity.rigid.multi-geometry-instances", multi, 0);
        }
    }
    for (const auto &prepared : preparedInstances) {
        const auto &geometry = prepared.model->geometry;
        if (geometry && geometry->blas) {
            ++instanceCapacity;
            geometryCapacity += geometry->geometryCount;
        }
    }
    if (staticScene) {
        instanceCapacity += staticScene->instanceCount;
        geometryCapacity += staticScene->geometryCount;
    }
    const auto &capacityChunks = chunks->chunks();
    for (size_t i = staticScene ? chunks->primaryChunkCount() : 0; i < capacityChunks.size(); ++i) {
        const auto &chunk = capacityChunks[i];
        if (chunk->blas) {
            ++instanceCapacity;
            geometryCapacity += chunk->geometryCount;
        }
    }
    const auto changingGeometryCapacity = geometryCapacity - (staticScene ? staticScene->geometryCount : 0);
    blasOffset.reserve(instanceCapacity);
    hitGroups.reserve(instanceCapacity + geometryCapacity);
    indexBufferAddrs.reserve(changingGeometryCapacity);
    positionBufferAddrs.reserve(changingGeometryCapacity);
    materialBufferAddrs.reserve(changingGeometryCapacity);
    lastIndexBufferAddrs.reserve(changingGeometryCapacity);
    lastPositionBufferAddrs.reserve(changingGeometryCapacity);
    lastObjToWorldMats.reserve(instanceCapacity);
    instanceAppearances.reserve(changingGeometryCapacity);

    auto scene = SceneRecordingScope::active();
    const uint32_t viewIndex = mcvr::view::viewForSlot(context->frameIndex, framework->swapchain()->imageCount());
    if (scene && scene->resetHistory) {
        worldPrepare1->previousEntityRenderDataBatches_.erase(viewIndex);
        worldPrepare1->previousRigidHistory_.erase(viewIndex);
        worldPrepare1->previousChunkTransformBatches_.erase(viewIndex);
        worldPrepare1->previousFlywheelTransforms_.erase(viewIndex);
    }
    auto previousTlasBuilder = std::move(tlasBuilder);
    auto previousTlas = std::move(tlas);
    framework->frameResourceRetainer().retain(previousTlasBuilder);
    framework->frameResourceRetainer().retain(previousTlas);
    tlasBuilder = vk::TLASBuilder::create();
    auto &instanceBuilder = tlasBuilder->beginInstanceBuilder();
    instanceBuilder.instances.reserve(instanceCapacity);
    int blasIndex = 0;

    // Entity
    profile.next("pt.prepare.entity-metadata");
    {
        auto entityBatch = preparedEntityBatch;

        if (entityBatch != nullptr) {
            std::unique_lock<std::recursive_mutex> entityHistoryLock(worldPrepare1->entityRenderDataBatchesMtx_);
            auto &previousEntityRenderDataBatches = worldPrepare1->previousEntityRenderDataBatches_[(
                mcvr::view::viewForSlot(context->frameIndex, framework->swapchain()->imageCount()))];
            auto &emptyEntityRenderDataBatch = worldPrepare1->emptyEntityRenderDataBatch_;

            auto &previousEntityRenderDataBatch = previousEntityRenderDataBatches.empty() ?
                                                      emptyEntityRenderDataBatch :
                                                      previousEntityRenderDataBatches.back();
            if (previousEntityRenderDataBatches.size() > Renderer::instance().framework()->swapchain()->imageCount())
                previousEntityRenderDataBatches.pop();
            auto &currentEntityRenderDataBatch = previousEntityRenderDataBatches.emplace();

            auto worldUniformBuffer = Renderer::instance().buffers()->worldUniformBuffer();
            auto ubo = static_cast<vk::Data::WorldUBO *>(worldUniformBuffer->mappedPtr());

            auto &entities1 = entityBatch->entities;
            for (int i = 0; i < entities1.size(); i++) {
                if (entities1[i]->worldToken != 0 &&
                    !entities->acceptsWorldMeshGeneration(entities1[i]->worldToken, entities1[i]->frameToken,
                                                          entities1[i]->resourceGeneration)) {
                    continue;
                }
                VkGeometryInstanceFlagsKHR flags = VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR;
                if (entities1[i]->uiSceneOwner != 0) flags |= VK_GEOMETRY_INSTANCE_FORCE_NO_OPAQUE_BIT_KHR;
                const mcvr::faces::ModelRules faceRules(entities1[i]->geometryMaterialFlags);
                // VkGeometryInstanceFlagsKHR flags = 0;
                VkTransformMatrixKHR transform;

                if (entities1[i]->prebuiltBLAS < 0) {
                    transform = mcvr::space::affineRows<VkTransformMatrixKHR>(
                        mcvr::space::entityTransform(entities1[i]->coordinate, entities1[i]->instanceTransform,
                                                     {entities1[i]->x, entities1[i]->y, entities1[i]->z}, cameraPos,
                                                     ubo ? &ubo->cameraViewMatInv : nullptr));

                    if (!entities1[i]->waterExclusions.empty()) {
                        auto matrix = glm::transpose(
                            glm::mat4(glm::make_vec4(transform.matrix[0]), glm::make_vec4(transform.matrix[1]),
                                      glm::make_vec4(transform.matrix[2]), glm::vec4(0, 0, 0, 1)));
                        for (const auto &planes : entities1[i]->waterExclusions)
                            mcvr::water::appendVolume(waterExclusions, planes, matrix);
                        continue;
                    }
                    flags = faceRules.instanceFlags(transform) |
                            ((entities1[i]->uiSceneOwner ||
                              (entities1[i]->rayTracingFlag & mcvr::abi::QUERY_PRIORITY) != 0) ?
                                 VK_GEOMETRY_INSTANCE_FORCE_NO_OPAQUE_BIT_KHR :
                                 0);
                    instanceBuilder.defineInstance(transform, blasIndex, entities1[i]->rayTracingFlag, blasGroupAccu,
                                                   flags, entities1[i]->blas);
                } else {
                    // auto &prebuiltBLAS =
                    //     Renderer::instance().framework()->prebuiltBLASs()[entityRenderData->prebuiltBLAS];
                    // transform = prebuiltBLAS.align(*entityRenderData->vertices, *entityRenderData->indices);

                    // instanceBuilder.defineInstance(transform, blasIndex, entityRenderData->rayTracingFlag,
                    // blasGroupAccu, flags,
                    //                                prebuiltBLAS.blas);
                    throw std::runtime_error("prebuilt blas not implemented yet!");
                }

                hitGroups.push_back(mcvr::hitgroups::shadow);
                for (int j = 0; j < entities1[i]->geometryCount; j++) {
                    if (entities1[i]->geometryGroupNames != nullptr &&
                        j < static_cast<int>(entities1[i]->geometryGroupNames->size())) {
                        hitGroups.push_back(mcvr::hitgroups::intern((*entities1[i]->geometryGroupNames)[j]));
                    } else {
                        hitGroups.push_back(defaultGroup);
                    }
                }

                for (int j = 0; j < entities1[i]->geometryCount; j++) {
                    indexBufferAddrs.push_back((*entities1[i]->indexBufferAddresses)[j]);
                    positionBufferAddrs.push_back((*entities1[i]->positionBufferAddresses)[j]);
                    materialBufferAddrs.push_back((*entities1[i]->materialBufferAddresses)[j]);
                    auto appearance = identityAppearance();
                    appearance.materialFlags = faceRules.shaderFlags(entities1[i]->geometryMaterialFlags[j]) |
                                               (entities1[i]->uiSceneOwner << 24u);
                    if (j < entities1[i]->geometryDrawColours.size() &&
                        entities1[i]->geometryDrawColours[j] != glm::vec4(1.0f)) {
                        appearance.colorMultiply = entities1[i]->geometryDrawColours[j];
                        appearance.flags |= mcvr::abi::APPEARANCE_DRAW_COLOUR;
                    }
                    if (j < entities1[i]->geometryEyeColours.size() &&
                        entities1[i]->geometryEyeColours[j] != glm::vec4(1)) {
                        appearance.colorReplace = entities1[i]->geometryEyeColours[j];
                        appearance.flags |= mcvr::abi::APPEARANCE_EYE_COLOUR;
                    }
                    if (j < entities1[i]->surfaceLayerAddresses.size()) {
                        const auto address = entities1[i]->surfaceLayerAddresses[j];
                        appearance.surfaceLayerAddressLo = uint32_t(address);
                        appearance.surfaceLayerAddressHi = uint32_t(address >> 32);
                        appearance.surfaceLayerPrimitiveCount = (*entities1[i]->indexCounts)[j] / 3;
                    }
                    instanceAppearances.push_back(appearance);
                }

                {
                    if (entities1[i]->historyKey()) {
                        currentEntityRenderDataBatch[entities1[i]->historyKey()].first = entities1[i];
                        currentEntityRenderDataBatch[entities1[i]->historyKey()].second = transform;
                    }
                }

                {
                    glm::mat4 lastObjToWorldMat(1);
                    auto iter = previousEntityRenderDataBatch.find(entities1[i]->historyKey());
                    if (iter != previousEntityRenderDataBatch.end()) {
                        auto &previousEntityRenderData = (*iter).second.first;
                        if (previousEntityRenderData->geometryCount == entities1[i]->geometryCount &&
                            previousEntityRenderData->lineLayoutKey == entities1[i]->lineLayoutKey &&
                            previousEntityRenderData->surfaceLayoutKey == entities1[i]->surfaceLayoutKey) {
                            for (int j = 0; j < entities1[i]->geometryCount; j++) {
                                if ((*previousEntityRenderData->vertexCounts)[j] == (*entities1[i]->vertexCounts)[j] &&
                                    (*previousEntityRenderData->indexCounts)[j] == (*entities1[i]->indexCounts)[j]) {
                                    lastIndexBufferAddrs.push_back(
                                        (*previousEntityRenderData->indexBufferAddresses)[j]);
                                    lastPositionBufferAddrs.push_back(
                                        (*previousEntityRenderData->positionBufferAddresses)[j]);
                                } else {
                                    lastIndexBufferAddrs.push_back(0);
                                    lastPositionBufferAddrs.push_back(0);
                                }
                            }
                        } else {
                            for (int j = 0; j < entities1[i]->geometryCount; j++) {
                                lastIndexBufferAddrs.push_back(0);
                                lastPositionBufferAddrs.push_back(0);
                            }
                        }

                        VkTransformMatrixKHR lastObjToWorldVkMat = iter->second.second;
                        lastObjToWorldMat = glm::transpose(glm::mat4(glm::make_vec4(lastObjToWorldVkMat.matrix[0]), //
                                                                     glm::make_vec4(lastObjToWorldVkMat.matrix[1]), //
                                                                     glm::make_vec4(lastObjToWorldVkMat.matrix[2]), //
                                                                     glm::vec4(0.0f, 0.0f, 0.0f, 1.0f)));
                    } else {
                        for (int j = 0; j < entities1[i]->geometryCount; j++) {
                            lastIndexBufferAddrs.push_back(0);
                            lastPositionBufferAddrs.push_back(0);
                        }
                    }
                    lastObjToWorldMats.push_back(lastObjToWorldMat);
                }

                blasOffset.push_back(blasAccu);
                blasAccu += entities1[i]->geometryCount;
                blasGroupAccu += entities1[i]->geometryCount + 1;

                blasIndex++;
            }
        }
    }

    // Rigid persistent-model instances. Geometry, BLAS, hit group and face rules are resolved once
    // per resident model; each instance contributes its transform, mask and motion history.
    profile.next("pt.prepare.rigid-metadata");
    {
        std::unique_lock<std::recursive_mutex> rigidHistoryLock(worldPrepare1->entityRenderDataBatchesMtx_);
        auto &views = worldPrepare1->previousRigidHistory_[viewIndex];
        const auto previous = views.empty() ? nullptr : views.back();
        // Record every frame, even an empty one, so history always means the immediately
        // preceding frame of this view, as in the per-entity map.
        auto current = std::make_shared<WorldPrepare::RigidHistoryState>();
        const bool accepted = preparedRigid && !preparedRigid->instances.empty() &&
                              entities->acceptsWorldMeshGeneration(preparedRigid->worldToken, preparedRigid->frameToken,
                                                                   preparedRigid->resourceGeneration);
        if (accepted) {
            framework->frameResourceRetainer().retain(preparedRigid);
            current->owner = preparedRigid;
            current->frame.reserve(preparedRigid->instances.size());
            for (const auto &instance : preparedRigid->instances) {
                const auto &model = *instance.model;
                const auto transform = mcvr::space::affineRows<VkTransformMatrixKHR>(mcvr::space::worldOriginFloatLocal(
                    instance.transform, {instance.x, instance.y, instance.z}, cameraPos));
                instanceBuilder.defineInstance(transform, blasIndex, instance.mask, blasGroupAccu,
                                               model.faces.instanceFlags(transform), model.blas);
                const auto last = mcvr::rigid::previous(previous ? previous->frame.find(instance.history) : nullptr,
                                                        instance.key, model.vertexCount, model.indexCount);
                const auto previousTransform = glm::transpose(
                    glm::mat4(glm::make_vec4(last.transform.data()), glm::make_vec4(last.transform.data() + 4),
                              glm::make_vec4(last.transform.data() + 8), glm::vec4(0, 0, 0, 1)));
                mcvr::rigid::appendInstanceTables(
                    model.geometries, mcvr::hitgroups::shadow, hitGroups, lastObjToWorldMats, previousTransform,
                    [&](const auto &geometry) {
                        indexBufferAddrs.push_back(geometry.index);
                        positionBufferAddrs.push_back(geometry.position);
                        materialBufferAddrs.push_back(geometry.material);
                        auto appearance = identityAppearance();
                        appearance.materialFlags = model.faces.shaderFlags(geometry.flags);
                        const auto colour = geometry.colour * instance.drawColour;
                        if (colour != glm::vec4(1)) {
                            appearance.colorMultiply = colour;
                            appearance.flags |= mcvr::abi::APPEARANCE_DRAW_COLOUR;
                        }
                        if (geometry.eyeColour != glm::vec4(1)) {
                            appearance.colorReplace = geometry.eyeColour;
                            appearance.flags |= mcvr::abi::APPEARANCE_EYE_COLOUR;
                        }
                        appearance.surfaceLayerAddressLo = uint32_t(geometry.surfaceLayers);
                        appearance.surfaceLayerAddressHi = uint32_t(geometry.surfaceLayers >> 32);
                        appearance.surfaceLayerPrimitiveCount = geometry.surfaceLayers ? geometry.indexCount / 3 : 0;
                        instanceAppearances.push_back(appearance);
                        // An immutable model identity implies the same complete local geometry set.
                        // Retired previous batches own the addresses; a topology/material resource
                        // replacement invalidates all primitive correspondence, not just geometry 0.
                        lastIndexBufferAddrs.push_back(last.indexAddress ? geometry.index : 0);
                        lastPositionBufferAddrs.push_back(last.positionAddress ? geometry.position : 0);
                    });

                mcvr::rigid::HistoryEntry entry{.history = instance.history,
                                                .model = instance.key,
                                                .indexAddress = model.indexAddress,
                                                .positionAddress = model.positionAddress,
                                                .vertexCount = model.vertexCount,
                                                .indexCount = model.indexCount};
                std::memcpy(entry.transform.data(), &transform, sizeof(transform));
                current->frame.add(entry);

                blasOffset.push_back(blasAccu);
                blasAccu += static_cast<uint32_t>(model.geometries.size());
                blasGroupAccu += static_cast<uint32_t>(model.geometries.size()) + 1;
                ++blasIndex;
            }
        }
        current->frame.finish();
        // Same retention depth as the per-entity history: pop before push, up to imageCount + 1.
        if (views.size() > framework->swapchain()->imageCount()) views.pop();
        views.push(std::move(current));
    }

    // Flywheel shared-model instances. Model vertex/material/index buffers and BLAS are reused;
    // only the TLAS transform and the small appearance record vary per instance.
    profile.next("pt.prepare.flywheel-lock");
    std::unique_lock<std::recursive_mutex> flywheelHistoryLock(worldPrepare1->flywheelTransformMtx_);
    profile.next("pt.prepare.flywheel-metadata");
    auto previousFlywheelTransforms = std::move(worldPrepare1->previousFlywheelTransforms_[(
        mcvr::view::viewForSlot(context->frameIndex, framework->swapchain()->imageCount()))]);
    std::map<WorldPrepare::FlywheelInstanceKey, glm::mat4> currentFlywheelTransforms;
    for (const auto &prepared : preparedInstances) {
        auto geometry = prepared.model->geometry;
        if (geometry == nullptr || geometry->blas == nullptr) continue;
        const mcvr::faces::ModelRules faceRules(prepared.model->materialFlags);
        framework->frameResourceRetainer().retain(prepared.model);
        glm::mat4 objectToWorld =
            mcvr::instancing::cameraRelativeTransform(prepared.renderOrigin, prepared.transform, cameraPos);
        const auto transform = mcvr::space::affineRows<VkTransformMatrixKHR>(objectToWorld);
        instanceBuilder.defineInstance(transform, blasIndex, 0x01, blasGroupAccu, faceRules.instanceFlags(transform),
                                       geometry->blas);
        hitGroups.push_back(mcvr::hitgroups::shadow);
        for (int j = 0; j < geometry->geometryCount; ++j) {
            hitGroups.push_back(prepared.model->groupIds[j]);
            indexBufferAddrs.push_back((*geometry->indexBufferAddresses)[j]);
            positionBufferAddrs.push_back((*geometry->positionBufferAddresses)[j]);
            materialBufferAddrs.push_back((*geometry->materialBufferAddresses)[j]);
            lastIndexBufferAddrs.push_back((*geometry->indexBufferAddresses)[j]);
            lastPositionBufferAddrs.push_back((*geometry->positionBufferAddresses)[j]);
            auto appearance = prepared.appearance;
            appearance.materialFlags = faceRules.shaderFlags(prepared.model->materialFlags[j]);
            if ((appearance.flags & mcvr::abi::APPEARANCE_EXPLICIT_LIGHTING) == 0u) {
                appearance.lightTransform = objectToWorld;
                appearance.lightTransform[3][0] += static_cast<float>(cameraPos.x);
                appearance.lightTransform[3][1] += static_cast<float>(cameraPos.y);
                appearance.lightTransform[3][2] += static_cast<float>(cameraPos.z);
            }
            instanceAppearances.push_back(appearance);
        }
        WorldPrepare::FlywheelInstanceKey historyKey{prepared.engine, prepared.id};
        auto previous = previousFlywheelTransforms.find(historyKey);
        lastObjToWorldMats.push_back(previous == previousFlywheelTransforms.end() ? objectToWorld : previous->second);
        currentFlywheelTransforms.emplace(historyKey, objectToWorld);
        blasOffset.push_back(blasAccu);
        blasAccu += geometry->geometryCount;
        blasGroupAccu += geometry->geometryCount + 1;
        ++blasIndex;
    }
    worldPrepare1->previousFlywheelTransforms_[(mcvr::view::viewForSlot(
        context->frameIndex, framework->swapchain()->imageCount()))] = std::move(currentFlywheelTransforms);

    profile.next("pt.prepare.chunk-metadata");
    // Chunk
    {
        profile.next("pt.prepare.chunk-history-lock");
        std::unique_lock<std::recursive_mutex> chunkHistoryLock(worldPrepare1->chunkTransformBatchesMtx_);
        profile.next("pt.prepare.chunk-metadata");
        auto &previousChunkTransformBatches = worldPrepare1->previousChunkTransformBatches_[(
            mcvr::view::viewForSlot(context->frameIndex, framework->swapchain()->imageCount()))];
        auto &emptyChunkTransformBatch = worldPrepare1->emptyChunkTransformBatch_;
        auto &previousChunkTransformBatch =
            previousChunkTransformBatches.empty() ? emptyChunkTransformBatch : previousChunkTransformBatches.back();
        if (previousChunkTransformBatches.size() > Renderer::instance().framework()->swapchain()->imageCount()) {
            previousChunkTransformBatches.pop();
        }
        auto &currentChunkTransformBatch = previousChunkTransformBatches.emplace();

        uint64_t cached = 0, rebuilt = 0, customHistory = 0, verified = 0;
        auto &chunk1s = chunks->chunks();
        if (staticScene) {
            profile.next("pt.prepare.static-scene-append");
            staticGeometryPrefix = indexBufferAddrs.size();
            staticScene->appendHeader(blasAccu, blasOffset, hitGroups);
            if (mcvr::profile::enabled())
                mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.static-copy-avoided", staticScene->geometryCount,
                                    staticScene->geometryCount *
                                        (5 * sizeof(uint64_t) + sizeof(vk::VertexFormat::InstanceAppearance)));
            profile.next("pt.prepare.static-instance-append");
            staticScene->appendInstances(
                cameraPos, blasIndex, blasGroupAccu, lastObjToWorldMats,
                [&](const auto &transform, auto index, auto group, auto flags, const auto &blas) {
                    instanceBuilder.defineInstance(transform, index, 0x01, group, flags, blas);
                });
            cached += staticScene->instanceCount;
            blasAccu += uint32_t(staticScene->geometryCount);
            blasGroupAccu += uint32_t(staticScene->groupCount);
            blasIndex += int(staticScene->instanceCount);
            if (verifyCache) {
                staticScene->eachInstance([&](const auto &page, size_t i) {
                    const auto &entry = page.instances[i];
                    const auto &chunk = chunk1s.at(entry.slot);
                    const auto &metadata = page.owners[i];
                    if (chunk->blas != entry.blas || chunk->sceneMetadata() != metadata ||
                        glm::ivec3(chunk->x, chunk->y, chunk->z) != entry.origin)
                        throw std::logic_error("Static chunk snapshot ownership mismatch");
                    for (uint32_t j = 0; j < chunk->geometryCount; ++j) {
                        const auto k = entry.geometryOffset + j;
                        const mcvr::faces::ModelRules rules(*chunk->geometryMaterialFlags);
                        const auto group = chunk->geometryGroupNames && j < chunk->geometryGroupNames->size() ?
                                               (*chunk->geometryGroupNames)[j] :
                                               "default";
                        if (page.indices[k] != (*chunk->indexBufferAddresses)[j] ||
                            page.positions[k] != (*chunk->positionBufferAddresses)[j] ||
                            page.materials[k] != (*chunk->materialBufferAddresses)[j] ||
                            page.appearances[k].materialFlags !=
                                rules.shaderFlags((*chunk->geometryMaterialFlags)[j]) ||
                            page.groups[entry.groupOffset + 1 + j] != mcvr::hitgroups::intern(group))
                            throw std::logic_error("Static chunk snapshot geometry mismatch");
                    }
                    ++verified;
                });
            }
            profile.next("pt.prepare.external-metadata");
        }
        for (size_t i = staticScene && !mcvr::chunkTrace::enabled ? chunks->primaryChunkCount() : 0; i < chunk1s.size();
             ++i) {
            auto &chunk1 = chunk1s[i];
            if (mcvr::chunkTrace::enabled) {
                auto version = chunk1->blas ? chunk1->blasVersion : chunk1->desiredVersion;
                if (version != chunk1->lastTracedVersion || bool(chunk1->blas) != chunk1->lastTracedPresent) {
                    const bool observablePresence = bool(chunk1->blas) || chunk1->lastTracedPresent;
                    chunk1->lastTracedVersion = version;
                    chunk1->lastTracedPresent = bool(chunk1->blas);
                    if (observablePresence) {
                        auto frame = framework->safeAcquireCurrentContext();
                        mcvr::chunkTrace::note(chunk1->blas ? "tlas-record" : "tlas-absent", i, version,
                                               frame ? frame->chunkTraceSerial : 0);
                    }
                }
            }
            if ((staticScene && i < chunks->primaryChunkCount()) || chunk1->blas == nullptr) continue;
            std::shared_ptr<mcvr::ChunkSceneMetadata> metadata;
            if (cacheEnabled) {
                auto builds = chunk1->sceneMetadataCache.builds();
                metadata = chunk1->sceneMetadata();
                rebuilt += builds != chunk1->sceneMetadataCache.builds();
                cached += builds == chunk1->sceneMetadataCache.builds();
                // Retain the immutable published snapshot, never the mutable Chunk1 owner.
                framework->frameResourceRetainer().retain(metadata);
            } else {
                chunk1->retainResources(framework->frameResourceRetainer());
            }
            const auto faceRules = metadata ? metadata->faces : mcvr::faces::ModelRules(*chunk1->geometryMaterialFlags);

            glm::dmat4 objectToWorld = chunk1->customTransform;
            if (!chunk1->hasCustomTransform) {
                objectToWorld = glm::dmat4(1.0);
                objectToWorld[3] = glm::dvec4(chunk1->x, chunk1->y, chunk1->z, 1.0);
            }
            objectToWorld = mcvr::space::worldToScene(objectToWorld, cameraPos);
            // External slots may receive their first transform after geometry publication.
            // Preserve that preceding frame too; only primary slots cannot become custom.
            mcvr::recordChunkTransform(currentChunkTransformBatch, chunk1, objectToWorld,
                                       chunk1->hasCustomTransform || uint32_t(i) >= chunks->primaryChunkCount(),
                                       !cacheEnabled);
            customHistory += chunk1->hasCustomTransform;

            const auto transform = mcvr::space::affineRows<VkTransformMatrixKHR>(objectToWorld);

            instanceBuilder.defineInstance(transform, blasIndex, 0x01, blasGroupAccu,
                                           faceRules.instanceFlags(transform), chunk1->blas);

            const size_t firstGroup = hitGroups.size(), firstGeometry = indexBufferAddrs.size();
            if (metadata) {
                metadata->append(chunk1->hasCustomTransform, hitGroups, indexBufferAddrs, positionBufferAddrs,
                                 materialBufferAddrs, lastIndexBufferAddrs, lastPositionBufferAddrs,
                                 instanceAppearances);
            } else {
                hitGroups.push_back(mcvr::hitgroups::shadow);
                for (int j = 0; j < chunk1->geometryCount; j++) {
                    if (chunk1->geometryGroupNames != nullptr &&
                        j < static_cast<int>(chunk1->geometryGroupNames->size())) {
                        hitGroups.push_back(mcvr::hitgroups::intern((*chunk1->geometryGroupNames)[j]));
                    } else {
                        hitGroups.push_back(defaultGroup);
                    }
                }

                for (int j = 0; j < chunk1->geometryCount; j++) {
                    indexBufferAddrs.push_back((*chunk1->indexBufferAddresses)[j]);
                    positionBufferAddrs.push_back((*chunk1->positionBufferAddresses)[j]);
                    materialBufferAddrs.push_back((*chunk1->materialBufferAddresses)[j]);
                    auto appearance = identityAppearance();
                    if (chunk1->geometryMaterialFlags != nullptr &&
                        j < static_cast<int>(chunk1->geometryMaterialFlags->size())) {
                        appearance.materialFlags = faceRules.shaderFlags((*chunk1->geometryMaterialFlags)[j]);
                    }
                    instanceAppearances.push_back(appearance);
                    if (chunk1->hasCustomTransform) {
                        lastIndexBufferAddrs.push_back((*chunk1->indexBufferAddresses)[j]);
                        lastPositionBufferAddrs.push_back((*chunk1->positionBufferAddresses)[j]);
                    } else {
                        lastIndexBufferAddrs.push_back(0);
                        lastPositionBufferAddrs.push_back(0);
                    }
                }
            }
            if (metadata && verifyCache) {
                // Independent reference from the current published source, after append.
                const mcvr::faces::ModelRules referenceFaces(*chunk1->geometryMaterialFlags);
                if (faceRules.instanceFlags(transform) != referenceFaces.instanceFlags(transform) ||
                    hitGroups[firstGroup] != mcvr::hitgroups::shadow)
                    throw std::logic_error("Chunk scene cache TLAS/group mismatch");
                for (uint32_t j = 0; j < chunk1->geometryCount; ++j) {
                    const auto k = firstGeometry + j;
                    auto expected = identityAppearance();
                    expected.materialFlags = referenceFaces.shaderFlags((*chunk1->geometryMaterialFlags)[j]);
                    const auto group = chunk1->geometryGroupNames && j < chunk1->geometryGroupNames->size() ?
                                           (*chunk1->geometryGroupNames)[j] :
                                           "default";
                    if (hitGroups[firstGroup + 1 + j] != mcvr::hitgroups::intern(group) ||
                        indexBufferAddrs[k] != (*chunk1->indexBufferAddresses)[j] ||
                        positionBufferAddrs[k] != (*chunk1->positionBufferAddresses)[j] ||
                        materialBufferAddrs[k] != (*chunk1->materialBufferAddresses)[j] ||
                        lastIndexBufferAddrs[k] !=
                            (chunk1->hasCustomTransform ? (*chunk1->indexBufferAddresses)[j] : 0) ||
                        lastPositionBufferAddrs[k] !=
                            (chunk1->hasCustomTransform ? (*chunk1->positionBufferAddresses)[j] : 0) ||
                        std::memcmp(&expected, &instanceAppearances[k], sizeof(expected)))
                        throw std::logic_error("Chunk scene cache geometry mismatch");
                }
                ++verified;
            }

            {
                const auto previousObjectToWorld = mcvr::previousChunkTransform(
                    previousChunkTransformBatch, chunk1, objectToWorld, chunk1->hasCustomTransform);
                if (verifyCache && !chunk1->hasCustomTransform && previousObjectToWorld != objectToWorld)
                    throw std::logic_error("Static chunk history must use its current camera-relative transform");
                glm::mat4 lastObjToWorldMat = glm::mat4(previousObjectToWorld);
                lastObjToWorldMats.push_back(lastObjToWorldMat);
            }

            blasOffset.push_back(blasAccu);
            blasAccu += chunk1->geometryCount;
            blasGroupAccu += chunk1->geometryCount + 1;

            blasIndex++;
        }
        if (mcvr::profile::enabled()) {
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.cache-hit", cached, 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.cache-build", rebuilt, 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.custom-history", customHistory, 0);
        }
        // Residency census: device bytes owned by published slots, each buffer counted once even
        // when a build batch shares it, against builds still queued or in flight. Profile counters
        // only in an explicit residency audit; RADIANCE_CHUNK_CENSUS=1 also logs it about every 60 frames.
        static const bool censusLog = [] {
            const char *value = std::getenv("RADIANCE_CHUNK_CENSUS");
            return value && std::string_view(value) == "1";
        }();
        static uint64_t censusFrames = 0;
        const bool censusProfile = mcvr::profile::residencyCensusEnabled() && mcvr::profile::frame % 30 == 0;
        const bool censusLogNow = censusLog && ++censusFrames % 60 == 0;
        if (censusProfile || censusLogNow) {
            profile.next("pt.prepare.chunk-residency-census");
            std::unordered_set<const void *> counted;
            uint64_t slots = 0, slotBytes = 0;
            auto add = [&](const auto &buffer) {
                if (buffer && counted.insert(buffer.get()).second) slotBytes += buffer->size();
            };
            for (const auto &chunk1 : chunk1s) {
                if (!chunk1->blas) continue;
                ++slots;
                add(chunk1->indexBuffer);
                add(chunk1->positionBuffer);
                add(chunk1->materialBuffer);
                add(chunk1->lightBuffer);
                add(chunk1->blas->blasBuffer());
            }
            const auto census =
                chunkBuildScheduler != nullptr ? chunkBuildScheduler->census() : ChunkBuildScheduler::Census{};
            if (censusProfile) {
                mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.resident.slots", slots, slotBytes);
                mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.resident.queued", census.queued, 0);
                mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.resident.inflight", census.chunks, census.bytes);
            }
            if (censusLogNow)
                mcvr::log::info("ChunkResidency")
                    << "slots=" << slots << " slotBytes=" << slotBytes << " queued=" << census.queued
                    << " inflightBatches=" << census.batches << " inflightChunks=" << census.chunks
                    << " inflightBytes=" << census.bytes << chunks->orphanCensus()
                    << framework->frameResourceRetainer().census() << std::endl;
        }
        if (verifyCache) {
            static std::atomic<uint64_t> verifiedFrames{0}, verifiedChunks{0};
            const auto chunkTotal = verifiedChunks.fetch_add(verified, std::memory_order_relaxed) + verified;
            const auto frameTotal = verifiedFrames.fetch_add(1, std::memory_order_relaxed) + 1;
            if (frameTotal == 1 || frameTotal % 300 == 0)
                mcvr::log::info("ChunkSceneCheck")
                    << "cache=" << cacheEnabled << " frames=" << frameTotal << " checkedChunks=" << chunkTotal
                    << " cached=" << cached << " rebuilt=" << rebuilt << " customHistory=" << customHistory
                    << std::endl;
        }
    }

    profile.next("pt.prepare.voxy-metadata");
    if (auto tables =
            scene ? std::shared_ptr<mcvr::VoxyFrameTables>{} : farSections->tables(*chunks, worldCommandBuffer)) {
        auto &previous = worldPrepare1->previousVoxyHistory_[viewIndex];
        if (worldPrepare1->previousVoxyTables_[viewIndex].lock() != tables) previous.clear();
        worldPrepare1->previousVoxyTables_[viewIndex] = tables;
        std::map<std::pair<uint64_t, uint64_t>, std::pair<uint64_t, uint64_t>> current;
        framework->frameResourceRetainer().retain(tables);
        if (const auto &held = tables->handoffNear) {
            held->eachInstance([&](const auto &page, size_t i) {
                const auto &entry = page.instances[i];
                const auto &metadata = page.owners[i];
                const auto objectToWorld =
                    mcvr::space::worldOriginFloatLocal(glm::mat4(1), glm::dvec3(entry.origin), cameraPos);
                const auto transform = mcvr::space::affineRows<VkTransformMatrixKHR>(objectToWorld);
                instanceBuilder.defineInstance(transform, blasIndex, mcvr::abi::QUERY_WORLD, blasGroupAccu, entry.flags,
                                               entry.blas);
                metadata->append(false, hitGroups, indexBufferAddrs, positionBufferAddrs, materialBufferAddrs,
                                 lastIndexBufferAddrs, lastPositionBufferAddrs, instanceAppearances);
                lastObjToWorldMats.push_back(objectToWorld);
                blasOffset.push_back(blasAccu);
                blasAccu += uint32_t(metadata->indices.size());
                blasGroupAccu += uint32_t(metadata->groups.size());
                ++blasIndex;
            });
        }
        for (auto &node : farSections->selected()) {
            framework->frameResourceRetainer().retain(node);
            const auto objectToWorld =
                mcvr::space::worldOriginFloatLocal(glm::mat4(1), glm::dvec3(node->origin), cameraPos);
            const auto transform = mcvr::space::affineRows<VkTransformMatrixKHR>(objectToWorld);
            instanceBuilder.defineInstance(transform, blasIndex, mcvr::abi::QUERY_WORLD, blasGroupAccu,
                                           VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR, node->blas);
            hitGroups.push_back(mcvr::hitgroups::shadow);
            hitGroups.push_back(defaultGroup);
            indexBufferAddrs.push_back(node->indices->bufferAddress());
            positionBufferAddrs.push_back(node->positions->bufferAddress());
            materialBufferAddrs.push_back(node->quads->bufferAddress());
            const auto identity = std::make_pair(node->revision, node->positions->bufferAddress());
            const auto historyKey = std::make_pair(node->epoch, node->key);
            auto old = previous.find(historyKey);
            const bool hasPrevious = old != previous.end() && old->second == identity;
            lastIndexBufferAddrs.push_back(hasPrevious ? node->indices->bufferAddress() : 0);
            lastPositionBufferAddrs.push_back(hasPrevious ? node->positions->bufferAddress() : 0);
            lastObjToWorldMats.push_back(objectToWorld);
            auto appearance = identityAppearance();
            appearance.flags = mcvr::abi::APPEARANCE_VOXY;
            appearance.overlay = {node->origin.x, node->origin.y};
            appearance.light = {node->origin.z, int(node->level)};
            // The VOXY tag selects this union; surface-layer ownership remains null.
            appearance.lightSectionStart = uint32_t(tables->header->bufferAddress());
            appearance.lightSectionCount = uint32_t(tables->header->bufferAddress() >> 32);
            instanceAppearances.push_back(appearance);
            blasOffset.push_back(blasAccu++);
            blasGroupAccu += 2;
            ++blasIndex;
            current.emplace(historyKey, identity);
        }
        previous = std::move(current);
    }

    profile.next("pt.prepare.tlas-build-record");
    if (mcvr::profile::enabled()) {
        mcvr::profile::emit(mcvr::profile::frame, 5, "world.tlas.instances", instanceBuilder.instances.size(), 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "world.tlas.geometries", blasAccu, 0);
    }
    if (instanceBuilder.instances.empty()) {
        tlas = nullptr;
        for (auto &table : metadataTables) table.reset();
        return;
    }

    if (scene && scene->batchTlas) {
        tlas = scene->batchTlas;
        // The shared scene owns the recorded build. This unfinished builder is
        // not a reusable TLAS publication on a subsequent ordinary frame.
        tlasBuilder.reset();
    } else if (!scene && previousTlas && previousTlasBuilder &&
               mcvr::sameInstanceContent(instanceBuilder.instances,
                                         previousTlasBuilder->beginInstanceBuilder().instances)) {
        tlas = std::move(previousTlas);
        tlasBuilder = std::move(previousTlasBuilder);
        if (mcvr::profile::enabled()) mcvr::profile::emit(mcvr::profile::frame, 5, "world.tlas.reuse", 1, 0);
    } else {
        const auto tlasStamp = SceneRecordingScope::active() ?
                                   -1 :
                                   context->auditGpu.begin(worldCommandBuffer->vkCommandBuffer(), "tlas-build-gpu");
        tlas = instanceBuilder.endInstanceBuilder(device, vma)
                   ->defineBuildProperty(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                   ->querySizeInfo(device)
                   ->allocateBuffers(physicalDevice, device, vma)
                   ->buildAndSubmit(device, worldCommandBuffer);
        context->auditGpu.end(worldCommandBuffer->vkCommandBuffer(), tlasStamp);
        if (mcvr::profile::enabled()) mcvr::profile::emit(mcvr::profile::frame, 5, "world.tlas.build", 1, 0);

        if (scene) {
            scene->batchTlas = tlas;
            ++scene->batchTlasBuilds;
        }
    }

    worldCommandBuffer->barriersMemory({vk::CommandBuffer::MemoryBarrier{
        .srcStageMask = VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
        .srcAccessMask = VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
        .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
        .dstAccessMask = VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR,
    }});

    profile.next("pt.prepare.metadata-upload");
    uploadBuffer(blasOffset, indexBufferAddrs, positionBufferAddrs, materialBufferAddrs, lastIndexBufferAddrs,
                 lastPositionBufferAddrs, lastObjToWorldMats, instanceAppearances, waterExclusions, staticScene,
                 staticGeometryPrefix);
    profile.next("pt.prepare.cleanup");
}

void WorldPrepareContext::setupHitGroupSbt(const std::unordered_map<std::string, uint32_t> &hitGroupNameToIndex,
                                           uint32_t fallbackHitGroupIndex,
                                           uint32_t shadowHitGroupIndex,
                                           std::shared_ptr<vk::CommandBuffer> commandBuffer,
                                           std::shared_ptr<vk::SBT> updateSbt,
                                           std::shared_ptr<vk::SBT> querySbt) {
    mcvr::profile::Phases profile("pt.sbt.lookup");
    // One name lookup per registered group, then an array index per geometry.
    const auto table =
        mcvr::hitgroups::Registry::global().resolve(hitGroupNameToIndex, fallbackHitGroupIndex, shadowHitGroupIndex);
    std::vector<uint32_t> hitGroupIndices;
    hitGroupIndices.reserve(hitGroups.size());
    for (const auto group : hitGroups) {
        if (group >= table.size()) throw std::logic_error("Hit-group id was interned after SBT resolution");
        hitGroupIndices.push_back(table[group]);
    }

    profile.next("pt.sbt.allocate-copy-record");
    if (updateSbt != nullptr) { updateSbt->setupHitSBT(hitGroupIndices, commandBuffer); }
    if (querySbt != nullptr) { querySbt->setupHitSBT(hitGroupIndices, commandBuffer); }
}
