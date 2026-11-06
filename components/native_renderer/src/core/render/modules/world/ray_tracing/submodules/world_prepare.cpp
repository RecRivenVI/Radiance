#include "core/render/chunk_trace.hpp"
#include "core/logging.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "core/diagnostics/table_delta.hpp"
#include "core/render/material_faces.hpp"
#include "core/render/scene_scope.hpp"
#include "core/render/scene_space.hpp"
#include "core/render/instance_content.hpp"
#include "core/render/view_frame.hpp"
#include "core/failure_state.hpp"
#include "core/render/modules/world/ray_tracing/submodules/world_prepare.hpp"
#include "core/render/medium_contact_publication.hpp"
#include "core/diagnostics/scene_replay.hpp"

#include "core/render/buffers.hpp"
#include "core/render/chunks.hpp"
#include "core/render/cpu_preparation_batch.hpp"
#include "core/render/entities.hpp"
#include "core/render/instancing.hpp"
#include "core/render/lod_scene.hpp"
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

namespace {
std::string_view sceneStoreMode() {
    static const std::string_view mode = [] {
        const auto *value = std::getenv("RADIANCE_SCENE_STORE");
        return value ? std::string_view(value) : std::string_view("1");
    }();
    return mode;
}

bool compactHistoryEnabled() {
    static const bool enabled = [] {
        const auto *value = std::getenv("RADIANCE_S1_HISTORY_PACK");
        if (value) return std::string_view(value) == "1";
        const auto mode = sceneStoreMode();
        return mode == "1" || mode == "verify";
    }();
    return enabled;
}

std::string_view mediumDirectoryMode() {
    static const std::string_view mode = [] {
        const auto *value = std::getenv("RADIANCE_S2_MEDIUM_DIRECTORY");
        return value ? std::string_view(value) : std::string_view("0");
    }();
    return mode;
}
} // namespace

WorldPrepare::WorldPrepare() {}
WorldPrepare::~WorldPrepare() = default;

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
        std::unique_lock<std::recursive_mutex> lock(instanceTransformMtx_);
        previousInstanceTransforms_.clear();
    }
    previousRigidHistory_.clear();
    rigidSlots_.clear();
    rigidColumns_.clear();
    previousLodHistory_.clear();
    mediumContacts_.clear();
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
        context->mediumContactBuffer = nullptr;
        context->sceneSampleKeys = nullptr;
        context->packedHistory = nullptr;
        context->historyUploadTable = nullptr;
        context->historyUpload = {};
        context->hitGroups.clear();
        context->hitGroupLayout.reset();
        context->stableHitRanges.clear();
        context->metadataScratch = {};
        std::vector<WorldPrepareContext::ChunkFrameItem>().swap(context->chunkFrameScratch);
        for (auto &table : context->observedMetadata) std::vector<uint8_t>().swap(table);
        context->metadataProfileEpoch = 0;
        context->metadataRecordedFrame = 0;
        for (auto &table : context->metadataTables) table.reset();
        context->metadataCapacity = {};
    }
}

WorldPrepareContext::WorldPrepareContext(std::shared_ptr<FrameworkContext> frameworkContext,
                                         std::shared_ptr<WorldPrepare> worldPrepare)
    : frameworkContext(frameworkContext), worldPrepare(worldPrepare) {}

bool WorldPrepareContext::uploadHistory(std::span<const glm::mat4> matrices,
                                        bool completedExclusiveSlot,
                                        VkBufferUsageFlags usage,
                                        glm::uvec2 staticHistory) {
    static_assert(sizeof(VkAccelerationStructureInstanceKHR) == 64 &&
                  offsetof(VkAccelerationStructureInstanceKHR, transform) == 0);
    if (matrices.empty()) return false;
    mcvr::profile::Scope timing("pt.metadata.history-transport");
    const auto instanceInput =
        staticHistory.y && tlasBuilder ? tlasBuilder->beginInstanceBuilder().instanceBuffer : nullptr;
    if (!instanceInput) staticHistory = glm::uvec2(0);
    historyUpload.encode(matrices, staticHistory.x, staticHistory.y);
    const auto bytes = historyUpload.words.size() * sizeof(glm::uvec4);
    if (bytes >= matrices.size_bytes()) return false;

    auto context = frameworkContext.lock();
    auto framework = context->framework.lock();
    auto device = framework->device();
    auto owner = worldPrepare.lock();
    auto command = context->worldCommandBuffer;
    auto packed = completedExclusiveSlot ? packedHistory : nullptr;
    auto output = completedExclusiveSlot && historyUploadTable ? lastObjToWorldMat : nullptr;
    auto table = completedExclusiveSlot ? historyUploadTable : nullptr;
    if (!packed || packed->size() < bytes)
        packed =
            vk::HostVisibleBuffer::create(framework->vma(), device, std::bit_ceil(std::max(bytes, sizeof(glm::uvec4))),
                                          VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    if (!output || output->size() < matrices.size_bytes())
        output =
            vk::DeviceLocalBuffer::create(framework->vma(), device, false, std::bit_ceil(matrices.size_bytes()), usage);
    if (bytes) packed->uploadToBuffer(historyUpload.words.data(), bytes, 0);
    if (!table) {
        table = vk::DescriptorTableBuilder{}
                    .beginDescriptorLayoutSet()
                    .beginDescriptorLayoutSetBinding()
                    .defineDescriptorLayoutSetBinding(
                        {0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                    .defineDescriptorLayoutSetBinding(
                        {1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                    .defineDescriptorLayoutSetBinding(
                        {2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                    .endDescriptorLayoutSetBinding()
                    .endDescriptorLayoutSet()
                    .definePushConstant({VK_SHADER_STAGE_COMPUTE_BIT, 0, 16})
                    .build(device);
    }
    table->bindBuffer(packed, 0, 0)->bindBuffer(output, 0, 1);
    if (instanceInput)
        table->bindBuffer(instanceInput, 0, 2);
    else
        table->bindBuffer(packed, 0, 2);
    if (!owner->historyUploadPipeline_) {
        auto shader =
            vk::Shader::create(device, (Renderer::folderPath / "shaders/world/scene_history_unpack_comp.spv").string());
        owner->historyUploadPipeline_ =
            vk::ComputePipelineBuilder{}.defineShader(shader).definePipelineLayout(table).build(device);
    }
    auto &keep = framework->frameResourceRetainer();
    keep.retain(packed);
    keep.retain(output);
    keep.retain(table);
    if (instanceInput) {
        keep.retain(instanceInput);
        command->barriersBufferImage({{.srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                       .srcAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
                                       .dstStageMask = VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                                       .dstAccessMask = VK_ACCESS_2_SHADER_READ_BIT,
                                       .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                       .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                       .buffer = instanceInput}},
                                     {});
    }
    const auto cmd = command->vkCommandBuffer();
    const auto timestamp = context->auditGpu.begin(cmd, "scene-history-unpack-gpu");
    command->bindComputePipeline(owner->historyUploadPipeline_);
    const auto set = table->descriptorSet().front();
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, table->vkPipelineLayout(), 0, 1, &set, 0, nullptr);
    const auto limit = framework->physicalDevice()->properties().limits.maxComputeWorkGroupCount[0];
    const auto count = static_cast<uint32_t>(matrices.size());
    for (uint64_t first = 0; first < count;) {
        const uint32_t groups = static_cast<uint32_t>(std::min<uint64_t>((count - first + 63) / 64, limit));
        const uint32_t push[]{static_cast<uint32_t>(first), count, staticHistory.x, staticHistory.y};
        vkCmdPushConstants(cmd, table->vkPipelineLayout(), VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(push), push);
        vkCmdDispatch(cmd, groups, 1, 1);
        first += uint64_t(groups) * 64;
    }
    command->barriersBufferImage(
        {{.srcStageMask = VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
          .srcAccessMask = VK_ACCESS_2_SHADER_WRITE_BIT,
          .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
          .dstAccessMask = VK_ACCESS_2_SHADER_READ_BIT,
          .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
          .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
          .buffer = output}},
        {});
    context->auditGpu.end(cmd, timestamp);
    packedHistory = completedExclusiveSlot ? packed : nullptr;
    historyUploadTable = completedExclusiveSlot ? table : nullptr;
    lastObjToWorldMat = std::move(output);
    if (mcvr::profile::enabled()) {
        mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.host-upload", 1, bytes);
        mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.transforms.packed", matrices.size(), bytes);
        mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.transforms.reused-tlas", staticHistory.y,
                            staticHistory.y * sizeof(glm::mat4));
    }
    return true;
}

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
                                       size_t staticGeometryPrefix,
                                       std::span<const glm::uvec2> sampleKeys,
                                       const mcvr::RigidSceneColumns::Publication &rigid,
                                       std::optional<glm::uvec2> packedStaticHistory) {
    mcvr::profile::Phases profile("pt.metadata.allocate-copy");
    auto context = frameworkContext.lock();
    auto framework = context->framework.lock();
    auto vma = framework->vma();
    auto device = framework->device();
    auto cmdBuffer = context->worldCommandBuffer;

    const bool observing = mcvr::profile::enabled();
    const auto epoch = mcvr::profile::epoch.load();
    const bool cold = epoch != metadataProfileEpoch;
    // acquireContext waited for this exclusive slot's fence. Auxiliary views and
    // repeated recordings in the same frame still need immutable versions.
    const bool completedExclusiveSlot =
        !SceneRecordingScope::active() && context->frameSerial != 0 && metadataRecordedFrame != context->frameSerial;
    metadataRecordedFrame = context->frameSerial;
    mcvr::profile::Accumulated allocationTime("pt.metadata.allocation");
    mcvr::profile::Accumulated copyTime("pt.metadata.staging-copy");
    mcvr::profile::Accumulated observationTime("pt.metadata.content-check");
    mcvr::profile::Accumulated recordTime("pt.metadata.upload-record");
    std::vector<vk::CommandBuffer::BufferMemoryBarrier> pendingUploads;
    pendingUploads.reserve(metadataTables.size());
    uint64_t barrierGroups = 0;
    auto prepare = [&](const mcvr::SceneTable &data, size_t table, const char *name, VkBufferUsageFlags usage) {
        const auto bytes = data.size();
        if (mcvr::profile::flowAuditEnabled()) {
            const size_t strides[]{
                sizeof(uint32_t),  sizeof(uint64_t),  sizeof(uint64_t),  sizeof(uint64_t),
                sizeof(uint64_t),  sizeof(uint64_t),  sizeof(glm::mat4), sizeof(vk::VertexFormat::InstanceAppearance),
                sizeof(glm::vec4), sizeof(glm::uvec2)};
            mcvr::profile::observeTable(metadataTables[table].observedContent(), data, strides[table], name);
        }
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
        // Keep unchanged records resident when their offsets fit the completed slot.
        // A layout change still gets a new buffer and copies the surviving ranges.
        mcvr::profile::Scope selectionTime("pt.metadata.select-content");
        std::shared_ptr<vk::HostVisibleBuffer> staging;
        std::vector<VkBufferCopy> uploads;
        const auto selection = metadataTables[table].acquirePatched(
            data,
            [&](const mcvr::SceneTable &, const mcvr::SceneTablePatch &patch, const auto &source) {
                auto buffer = source;
                if (!patch.inPlace) {
                    mcvr::profile::Scope allocation(allocationTime);
                    const auto capacity = rigid[0] ? std::bit_ceil(bytes) : bytes;
                    buffer = vk::DeviceLocalBuffer::create(vma, device, false, capacity, usage);
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
                if (observing) {
                    mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.host-upload", 1, patch.uploadBytes);
                    if (patch.inPlace)
                        mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.resident-update", 1,
                                            patch.residentBytes);
                }
                if (mcvr::profile::flowAuditEnabled()) {
                    uint64_t copied = 0;
                    for (const auto &copy : patch.copies) copied += copy.bytes;
                    mcvr::profile::emit(mcvr::profile::frame, 5, (std::string(name) + ".host-upload").c_str(),
                                        patch.uploads.size(), patch.uploadBytes);
                    mcvr::profile::emit(mcvr::profile::frame, 5, (std::string(name) + ".gpu-copy").c_str(),
                                        patch.copies.size(), copied);
                    if (!patch.inPlace)
                        mcvr::profile::emit(mcvr::profile::frame, 5, (std::string(name) + ".allocation").c_str(), 1,
                                            bytes);
                }
                return buffer;
            },
            [&](const auto &buffer, const mcvr::SceneTablePatch &patch, const auto &source) {
                mcvr::profile::Scope record(recordTime);
                if (source && !patch.inPlace) {
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
                // Make the changed table ranges visible to the world passes.
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
            [&](const auto &buffer) { framework->frameResourceRetainer().retain(buffer); }, true, true,
            completedExclusiveSlot, rigid[0] ? metadataCapacity[table] : 0);
        metadataCapacity[table] = selection.buffer->size();
        if (observing)
            mcvr::profile::emit(mcvr::profile::frame, 5, selection.uploaded ? "metadata.upload" : "metadata.reuse", 1,
                                bytes);
        return selection.buffer;
    };
    const VkBufferUsageFlags addressUsage = VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                                            VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
                                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    auto addresses = [&](const auto &values, auto member, size_t column) {
        auto table = staticScene ? staticScene->table<uint64_t>(values, staticGeometryPrefix, member) :
                                   mcvr::sceneTable<uint64_t>(values);
        table.leading = rigid[column];
        return table;
    };
    blasOffsetsBuffer = prepare(mcvr::sceneTable<uint32_t>(blasOffsets), 0, "metadata.offsets.changed", addressUsage);
    indexBufferAddr = prepare(addresses(indexBufferAddrs, &mcvr::StaticChunkPage::indices, 0), 1,
                              "metadata.indices.changed", addressUsage);
    positionBufferAddr = prepare(addresses(positionBufferAddrs, &mcvr::StaticChunkPage::positions, 1), 2,
                                 "metadata.positions.changed", addressUsage);
    materialBufferAddr = prepare(addresses(materialBufferAddrs, &mcvr::StaticChunkPage::materials, 2), 3,
                                 "metadata.materials.changed", addressUsage);
    lastIndexBufferAddr = prepare(addresses(lastIndexBufferAddrs, &mcvr::StaticChunkPage::lastIndices, 3), 4,
                                  "metadata.last-indices.changed", addressUsage);
    lastPositionBufferAddr = prepare(addresses(lastPositionBufferAddrs, &mcvr::StaticChunkPage::lastPositions, 4), 5,
                                     "metadata.last-positions.changed", addressUsage);
    if (!packedStaticHistory ||
        !uploadHistory(lastObjToWorldMats, completedExclusiveSlot, addressUsage, *packedStaticHistory)) {
        historyUploadTable.reset();
        lastObjToWorldMat =
            prepare(mcvr::sceneTable<glm::mat4>(lastObjToWorldMats), 6, "metadata.transforms.changed", addressUsage);
    }
    static_assert(uint64_t(UINT32_MAX) / sizeof(vk::VertexFormat::InstanceAppearance) < (1u << 24u));
    const auto totalGeometry = instanceAppearances.size() + (staticScene ? staticScene->geometryCount : 0) +
                               (rigid[5] ? rigid[5]->bytes / sizeof(vk::VertexFormat::InstanceAppearance) : 0);
    if (totalGeometry >= (1u << 24u)) throw std::overflow_error("Surface cache geometry tag capacity");
    auto appearance = staticScene ?
                          staticScene->table<vk::VertexFormat::InstanceAppearance>(
                              instanceAppearances, staticGeometryPrefix, &mcvr::StaticChunkPage::appearances) :
                          mcvr::sceneTable<vk::VertexFormat::InstanceAppearance>(instanceAppearances);
    appearance.leading = rigid[5];
    instanceAppearanceBuffer = prepare(appearance, 7, "metadata.appearances.changed",
                                       VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    waterExclusionBuffer = prepare(mcvr::sceneTable<glm::vec4>(waterExclusions), 8, "metadata.water.changed",
                                   VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    static const std::array<glm::uvec2, 1> legacySampleKeys{glm::uvec2(UINT32_MAX)};
    if (sampleKeys.empty()) sampleKeys = legacySampleKeys;
    sceneSampleKeys = prepare(mcvr::sceneTable<glm::uvec2>(sampleKeys), 9, "metadata.sample-keys.changed",
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

    if (mcvr::replay::render(*this)) return;
    auto world = Renderer::instance().world();
    auto chunks = world->chunks();
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

    profile.next("pt.prepare.record-models");
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
    auto farSections = Renderer::instance().world()->lodScene();
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
    auto &frameChunks = chunkFrameScratch;
    frameChunks.clear();
    struct ReleaseSnapshots {
        std::vector<ChunkFrameItem> &items;
        ~ReleaseSnapshots() {
            items.clear();
        }
    } releaseSnapshots{frameChunks};
    std::shared_ptr<const mcvr::StaticChunkScene> staticScene;
    std::shared_ptr<mcvr::LodFrameTables> lodTables;
    uint64_t cachedSnapshot = 0, rebuiltSnapshot = 0, verifiedSnapshot = 0;
    // Keep one coherent publication across near/far geometry and handoffs, then
    // release the producer lock before entity history, table assembly and upload.
    profile.next("pt.prepare.chunk-lock-wait");
    {
        std::unique_lock lock(chunks->mutex());
        profile.next("pt.prepare.static-scene-refresh");
        const auto staticBuilds = chunks->staticSceneBuilds();
        const auto staticPageBuilds = chunks->staticScenePageBuilds();
        staticScene = cacheEnabled && staticSceneEnabled ? chunks->staticScene() : nullptr;
        if (mcvr::profile::enabled()) {
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.static-rebuild",
                                chunks->staticSceneBuilds() - staticBuilds, 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.static-page-rebuild",
                                chunks->staticScenePageBuilds() - staticPageBuilds, 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.static-records",
                                staticScene ? staticScene->instanceCount : 0,
                                staticScene ? staticScene->storageBytes() : 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.static-geometries",
                                staticScene ? staticScene->geometryCount : 0, 0);
        }


        const auto &snapshotChunks = chunks->chunks();
        if (staticScene) {
            if (verifyCache) {
                staticScene->eachInstance([&](const auto &page, size_t i) {
                    const auto &entry = page.instances[i];
                    const auto &chunk = snapshotChunks.at(entry.slot);
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
                    ++verifiedSnapshot;
                });
            }
        }
        profile.next("pt.prepare.chunk-snapshot");
        for (size_t i = staticScene && !mcvr::chunkTrace::enabled ? chunks->primaryChunkCount() : 0;
             i < snapshotChunks.size(); ++i) {
            const auto &chunk = snapshotChunks[i];
            if (mcvr::chunkTrace::enabled) {
                const auto version = chunk->blas ? chunk->blasVersion : chunk->desiredVersion;
                if (version != chunk->lastTracedVersion || bool(chunk->blas) != chunk->lastTracedPresent) {
                    if (chunk->blas || chunk->lastTracedPresent) {
                        mcvr::chunkTrace::note(chunk->blas ? "tlas-record" : "tlas-absent", i, version,
                                               context->chunkTraceSerial);
                    }
                    chunk->lastTracedVersion = version;
                    chunk->lastTracedPresent = bool(chunk->blas);
                }
            }
            if ((staticScene && i < chunks->primaryChunkCount()) || !chunk->blas) continue;
            const auto builds = chunk->sceneMetadataCache.builds();
            auto metadata = cacheEnabled ? chunk->sceneMetadata() : nullptr;
            if (cacheEnabled) {
                rebuiltSnapshot += builds != chunk->sceneMetadataCache.builds();
                cachedSnapshot += builds == chunk->sceneMetadataCache.builds();
            }
            const auto *space = chunk->space ?
                                    (chunk->retiredSpace ? &*chunk->retiredSpace : &world->spaces().at(chunk->space)) :
                                    nullptr;
            auto transform = space ? space->matrix * chunk->customTransform : chunk->customTransform;
            if (!chunk->hasCustomTransform) {
                transform = glm::dmat4(1);
                transform[3] = glm::dvec4(chunk->x, chunk->y, chunk->z, 1);
            }
            // A held section can outlive its space. Contact preparation must use the
            // same publication as TLAS placement without resolving its parent again.
            frameChunks.push_back({chunk, metadata, !cacheEnabled || verifyCache ? chunk->tryGetValid() : nullptr,
                                   chunk->blas, chunk->geometryCount, chunk->hasCustomTransform,
                                   i >= chunks->primaryChunkCount(), transform, chunk->space,
                                   space ? chunk->customTransform : transform,
                                   space ? space->sectionColour.plane : glm::vec4(0),
                                   space ? space->sectionColour.target : glm::vec4(0)});
        }
        if (!SceneRecordingScope::active()) lodTables = farSections->tables(*chunks, worldCommandBuffer);
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
            for (const auto &chunk1 : snapshotChunks) {
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
    }

    // The retainer stores erased mutable pointers but never mutates its resources.
    if (staticScene)
        framework->frameResourceRetainer().retain(std::const_pointer_cast<mcvr::StaticChunkScene>(staticScene));
    profile.next("pt.prepare.metadata-init");
    uint32_t blasAccu = 0, blasGroupAccu = 0;
    size_t staticGeometryPrefix = 0;
    glm::uvec2 staticHistory(0);
    const auto previousScratchBytes = metadataScratch.capacityBytes();
    metadataScratch.clear();
    auto &blasOffset = metadataScratch.offsets;
    hitGroups.clear();
    stableHitRanges.clear();
    static const auto defaultGroup = mcvr::hitgroups::intern("default");
    auto &indexBufferAddrs = metadataScratch.indices;
    auto &positionBufferAddrs = metadataScratch.positions;
    auto &materialBufferAddrs = metadataScratch.materials;
    auto &lastIndexBufferAddrs = metadataScratch.lastIndices;
    auto &lastPositionBufferAddrs = metadataScratch.lastPositions;
    auto &lastObjToWorldMats = metadataScratch.transforms;
    auto &instanceAppearances = metadataScratch.appearances;
    auto &waterExclusions = metadataScratch.water;
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
        mcvr::profile::emit(mcvr::profile::frame, 5, "scene.shared.instances", preparedInstances.size(), 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "scene.shared.resident-models", instancing->residentModelCount(),
                            0);
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
    for (const auto &chunk : frameChunks) {
        ++instanceCapacity;
        geometryCapacity += chunk.geometryCount;
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
    const uint32_t viewIndex = mcvr::view::viewForSlot(context->frameIndex, framework->mainFrameSlotCount());
    const bool rigidAccepted =
        preparedRigid && !preparedRigid->instances.empty() &&
        entities->acceptsWorldMeshGeneration(preparedRigid->worldToken, preparedRigid->frameToken,
                                             preparedRigid->resourceGeneration);
    const auto storeMode = sceneStoreMode();
    const bool sparseRigid = storeMode == "1" || storeMode == "verify";
    const bool verifyRigid = storeMode == "verify";
    const bool packHistory = compactHistoryEnabled();
    struct RigidVerification {
        uint64_t key;
        uint32_t slot, group, base;
        VkTransformMatrixKHR transform;
        glm::mat4 previous;
        uint32_t mask, flags;
        const RigidModelResource *model;
        bool indexHistory, positionHistory;
        bool resident;
        std::vector<vk::VertexFormat::InstanceAppearance> appearances;
    };
    std::vector<RigidVerification> rigidVerification;
    std::vector<std::optional<mcvr::SceneSlots::Range>> rigidRanges;
    std::vector<uint32_t> rigidPoseSlots;
    std::vector<glm::uvec3> rigidSampleOrder;
    bool verifiedSpace = false;
    uint32_t reservedRigidSlots = 0, reservedRigidRecords = 0;
    mcvr::RigidSceneColumns *rigidColumns = nullptr;
    if (sparseRigid) {
        mcvr::profile::Scope time("pt.prepare.rigid-slots");
        auto &slots = worldPrepare1->rigidSlots_[viewIndex];
        rigidColumns = &worldPrepare1->rigidColumns_[viewIndex];
        if (scene && scene->resetHistory) {
            slots = {};
            *rigidColumns = {};
        }
        slots.begin();
        if (rigidAccepted) {
            rigidRanges.reserve(preparedRigid->instances.size());
            rigidSampleOrder.reserve(preparedRigid->instances.size());
            rigidPoseSlots.reserve(preparedRigid->instances.size());
            for (const auto &instance : preparedRigid->instances) {
                auto poseSlot = mcvr::RigidSceneColumns::zeroHistorySlot;
                rigidRanges.push_back(
                    slots.select(instance.history, uint32_t(instance.model->geometries.size()), &poseSlot));
                rigidPoseSlots.push_back(poseSlot);
            }
        }
        slots.finish();
        if (mcvr::profile::enabled()) {
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.store.rigid-insert", slots.additions(), 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.store.rigid-remove", slots.removals(), 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.store.rigid-relocate", slots.moves(), 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.store.rigid-selection", rigidRanges.size(), 0);
        }
        reservedRigidSlots = slots.slotCount();
        blasOffset.resize(reservedRigidSlots);
        lastObjToWorldMats.resize(reservedRigidSlots, glm::mat4(1));
        reservedRigidRecords = slots.recordCount();
        blasAccu = reservedRigidRecords;
        blasGroupAccu = slots.groupCount();
        hitGroups.resize(blasGroupAccu, mcvr::hitgroups::shadow);
        rigidColumns->resize(reservedRigidSlots, reservedRigidRecords, blasGroupAccu);
        rigidColumns->beginPoses(reservedRigidSlots);
        for (const auto &retired : slots.retired()) {
            rigidColumns->clear(retired.record, retired.group, retired.geometries);
            rigidColumns->sources[retired.slot] = {};
        }
    }
    auto &contactOwner = worldPrepare1->mediumContacts_[viewIndex];
    if (!contactOwner) contactOwner = std::make_unique<mcvr::medium::Publication>();
    auto &contacts = *contactOwner;
    contacts.begin(mediumDirectoryMode());
    const auto addContacts = [&](mcvr::medium::SourceKey key, const auto &mesh, const glm::dmat4 &pose,
                                 const glm::dmat4 &local, uint64_t space, bool fixed, uint32_t base, uint32_t count) {
        if (!mesh || mesh->complete && mesh->triangles.empty()) return;
        contacts.scene.add(key, mesh, pose, local, space, fixed, base, count);
    };
    if (scene && scene->resetHistory) {
        worldPrepare1->previousEntityRenderDataBatches_.erase(viewIndex);
        worldPrepare1->previousRigidHistory_.erase(viewIndex);
        worldPrepare1->previousChunkTransformBatches_.erase(viewIndex);
        worldPrepare1->previousInstanceTransforms_.erase(viewIndex);
    }
    auto previousTlasBuilder = std::move(tlasBuilder);
    auto previousTlas = std::move(tlas);
    framework->frameResourceRetainer().retain(previousTlasBuilder);
    framework->frameResourceRetainer().retain(previousTlas);
    tlasBuilder = vk::TLASBuilder::create();
    auto &instanceBuilder = tlasBuilder->beginInstanceBuilder();
    instanceBuilder.instances.reserve(instanceCapacity);
    instanceBuilder.instanceModels.reserve(instanceCapacity);
    VkAccelerationStructureInstanceKHR vacant{};
    vacant.transform.matrix[0][0] = vacant.transform.matrix[1][1] = vacant.transform.matrix[2][2] = 1;
    instanceBuilder.instances.resize(reservedRigidSlots, vacant);
    instanceBuilder.instanceModels.resize(reservedRigidSlots);
    int blasIndex = int(reservedRigidSlots);

    // Entity
    profile.next("pt.prepare.entity-metadata");
    {
        auto entityBatch = preparedEntityBatch;

        if (entityBatch != nullptr) {
            std::unique_lock<std::recursive_mutex> entityHistoryLock(worldPrepare1->entityRenderDataBatchesMtx_);
            auto &previousEntityRenderDataBatches = worldPrepare1->previousEntityRenderDataBatches_[(
                mcvr::view::viewForSlot(context->frameIndex, framework->mainFrameSlotCount()))];
            auto &emptyEntityRenderDataBatch = worldPrepare1->emptyEntityRenderDataBatch_;

            auto &previousEntityRenderDataBatch = previousEntityRenderDataBatches.empty() ?
                                                      emptyEntityRenderDataBatch :
                                                      previousEntityRenderDataBatches.back();
            if (previousEntityRenderDataBatches.size() > Renderer::instance().framework()->mainFrameSlotCount())
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

                auto contactPose = glm::dmat4(entities1[i]->instanceTransform);
                if (entities1[i]->coordinate == World::WORLD)
                    contactPose[3] += glm::dvec4(entities1[i]->x, entities1[i]->y, entities1[i]->z, 0);
                else {
                    contactPose = glm::dmat4(mcvr::space::entityTransform(
                        entities1[i]->coordinate, entities1[i]->instanceTransform, glm::dvec3(0), glm::dvec3(0),
                        ubo ? &ubo->cameraViewMatInv : nullptr));
                    contactPose[3] += glm::dvec4(cameraPos, 0);
                }
                addContacts({1, entities1[i]->historyKey(), 0}, entities1[i]->mediumMesh, contactPose, contactPose, 0,
                            false, blasAccu, entities1[i]->geometryCount);

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
    const uint32_t rigidStart = uint32_t(blasIndex);
    profile.next("pt.prepare.rigid-metadata");
    uint64_t rigidGeometryRows = 0, rigidAppearanceRows = 0, rigidHistoryRows = 0;
    {
        std::unique_lock<std::recursive_mutex> rigidHistoryLock(worldPrepare1->entityRenderDataBatchesMtx_);
        auto &views = worldPrepare1->previousRigidHistory_[viewIndex];
        const auto previous = views.empty() ? nullptr : views.back();
        // Record every frame, even an empty one, so history always means the immediately
        // preceding frame of this view, as in the per-entity map.
        auto current = !rigidColumns || verifyRigid ? std::make_shared<WorldPrepare::RigidHistoryState>() : nullptr;
        if (rigidAccepted) {
            framework->frameResourceRetainer().retain(preparedRigid);
            if (current) {
                current->owner = preparedRigid;
                current->frame.reserve(preparedRigid->instances.size());
            }
            const auto uniformBuffer = Renderer::instance().buffers()->worldUniformBuffer();
            const auto ubo = static_cast<const vk::Data::WorldUBO *>(uniformBuffer->mappedPtr());
            const auto inverseView = glm::dmat4(ubo->cameraViewMatInv);
            const auto localPose = [](const auto &instance) {
                auto pose = glm::dmat4(instance.transform);
                pose[3] += glm::dvec4(instance.x, instance.y, instance.z, 0);
                return pose;
            };
            const auto toScene = [&](const auto &instance, const glm::dmat4 &objectPose) {
                auto scenePose = mcvr::space::worldToScene(objectPose, cameraPos);
                if (instance.coordinates == mcvr::abi::COORDINATE_CAMERA)
                    scenePose = inverseView * objectPose;
                else if (instance.coordinates == mcvr::abi::COORDINATE_CAMERA_SHIFT) {
                    scenePose = objectPose;
                    scenePose[3] += glm::dvec4(glm::dvec3(inverseView[3]), 0);
                }
                return scenePose;
            };
            const auto preparePose = [&](size_t index, const auto &instance, const glm::dmat4 *parent) {
                VkTransformMatrixKHR transform;
                if (!parent && instance.coordinates == mcvr::abi::COORDINATE_WORLD) {
                    // No space composition: only the origin needs double precision.
                    transform = mcvr::space::affineRows<VkTransformMatrixKHR>(mcvr::space::stableOriginToScene(
                        {instance.x, instance.y, instance.z}, instance.transform, cameraPos));
                } else {
                    const auto local = localPose(instance);
                    const auto scenePose = toScene(instance, parent ? *parent * local : local);
                    transform = mcvr::space::affineRows<VkTransformMatrixKHR>(scenePose);
                }
                const auto legacy = [&] { return previous ? previous->frame.find(instance.history) : nullptr; };
                const auto resolve = [&](const mcvr::rigid::HistoryEntry *entry) {
                    return mcvr::rigid::previous(entry, instance.key, instance.model->vertexCount,
                                                 instance.model->indexCount);
                };
                const auto last = resolve(rigidColumns ? rigidColumns->previousPose(rigidPoseSlots[index]) : legacy());
                if (verifyRigid) {
                    const auto expected = resolve(legacy());
                    if (last.transform != expected.transform || last.indexAddress != expected.indexAddress ||
                        last.positionAddress != expected.positionAddress || last.hasTransform != expected.hasTransform)
                        throw std::logic_error("Rigid scene store mismatch: " + std::to_string(instance.history) +
                                               " previous source");
                }
                const auto previousTransform = glm::transpose(
                    glm::mat4(glm::make_vec4(last.transform.data()), glm::make_vec4(last.transform.data() + 4),
                              glm::make_vec4(last.transform.data() + 8), glm::vec4(0, 0, 0, 1)));
                return RigidPose{transform, previousTransform, last.indexAddress != 0, last.positionAddress != 0};
            };
            auto &poses = rigidPoseScratch;
            poses.clear();
            const auto &instances = preparedRigid->instances;
            if (auto *cpu = entities->cpuPreparation(); cpu && !scene && instances.size() >= 512) {
                mcvr::profile::Scope parallel("pt.prepare.rigid-pose-dispatch");
                std::map<uint64_t, glm::dmat4> parents;
                for (const auto &instance : instances)
                    if (instance.space && !parents.contains(instance.space))
                        parents.emplace(instance.space, world->spaces().at(instance.space).matrix);
                const auto &frozenParents = std::as_const(parents);
                poses.resize(instances.size());
                constexpr size_t grain = 64;
                cpu->run((instances.size() + grain - 1) / grain, [&](size_t job) {
                    const auto end = std::min((job + 1) * grain, instances.size());
                    for (size_t i = job * grain; i < end; ++i) {
                        const auto &instance = instances[i];
                        poses[i] =
                            preparePose(i, instance, instance.space ? &frozenParents.at(instance.space) : nullptr);
                    }
                });
                if (mcvr::profile::enabled())
                    mcvr::profile::emit(mcvr::profile::frame, 5, "rigid.pose.background-jobs", cpu->backgroundJobs(),
                                        0);
            }
            for (size_t i = 0; i < instances.size(); ++i) {
                const auto &instance = instances[i];
                const auto &model = *instance.model;
                const auto pose =
                    poses.empty() ? preparePose(i, instance,
                                                instance.space ? &world->spaces().at(instance.space).matrix : nullptr) :
                                    poses[i];
                const auto &transform = pose.transform;
                const auto range = sparseRigid ? rigidRanges[i] : std::nullopt;
                const uint32_t geometryBase = range ? range->record : blasAccu;
                const auto instanceFlags = model.faces.instanceFlags(transform);
                if (sparseRigid) {
                    const auto identity = uint32_t(instance.history) ^ uint32_t(instance.history >> 32u);
                    const auto key = std::min(identity, UINT32_MAX - 1);
                    rigidSampleOrder.emplace_back(range ? range->slot : uint32_t(blasIndex), model.geometries.size(),
                                                  range ? key : UINT32_MAX);
                }
                if (verifyRigid) {
                    verifiedSpace |= instance.space != 0;
                    rigidVerification.push_back({instance.history, range ? range->slot : uint32_t(blasIndex),
                                                 range ? range->group : blasGroupAccu, geometryBase, transform,
                                                 pose.previous, instance.mask, instanceFlags, &model, pose.indexHistory,
                                                 pose.positionHistory, range.has_value()});
                }
                if (range) {
                    auto &entry = instanceBuilder.instances[range->slot];
                    entry.transform = transform;
                    entry.instanceCustomIndex = range->slot;
                    entry.mask = instance.mask;
                    entry.instanceShaderBindingTableRecordOffset = range->group;
                    entry.flags = instanceFlags;
                    entry.accelerationStructureReference = model.blas->blasDeviceAddress();
                    instanceBuilder.instanceModels[range->slot] = model.blas.get();
                    instanceBuilder.owners.push_back(model.blas);
                    blasOffset[range->slot] = geometryBase;
                    lastObjToWorldMats[range->slot] = pose.previous;
                } else {
                    instanceBuilder.defineInstance(transform, blasIndex, instance.mask, blasGroupAccu, instanceFlags,
                                                   model.blas);
                    hitGroups.push_back(mcvr::hitgroups::shadow);
                    lastObjToWorldMats.push_back(pose.previous);
                }
                const auto &mesh = model.batch->entities.front()->mediumMesh;
                if (mesh && (!mesh->complete || !mesh->triangles.empty())) {
                    const auto local = localPose(instance);
                    auto contactPose = instance.space ? world->spaces().at(instance.space).matrix * local : local;
                    if (instance.coordinates != mcvr::abi::COORDINATE_WORLD) {
                        contactPose = toScene(instance, contactPose);
                        contactPose[3] += glm::dvec4(cameraPos, 0);
                    }
                    addContacts({2, instance.history, instance.key}, mesh, contactPose, local, instance.space, false,
                                geometryBase, uint32_t(model.geometries.size()));
                }
                bool geometryChanged = true, appearanceChanged = true, historyChanged = true;
                if (range) {
                    auto &source = rigidColumns->sources[range->slot];
                    geometryChanged = source.model != &model || source.record != range->record;
                    appearanceChanged = geometryChanged ||
                                        std::memcmp(&source.surface, &instance.surface, sizeof(source.surface)) != 0 ||
                                        std::memcmp(&source.colour, &instance.drawColour, sizeof(source.colour)) != 0;
                    historyChanged = geometryChanged || source.indexHistory != pose.indexHistory ||
                                     source.positionHistory != pose.positionHistory;
                    if (geometryChanged || appearanceChanged || historyChanged)
                        source = {&model,
                                  range->record,
                                  instance.surface,
                                  instance.drawColour,
                                  pose.indexHistory,
                                  pose.positionHistory};
                }
                const auto buildAppearance = [&](const auto &geometry) {
                    auto appearance = identityAppearance();
                    appearance.materialFlags = model.faces.shaderFlags(geometry.flags);
                    const auto &surface = instance.surface;
                    if (surface.flags) appearance.flags |= mcvr::abi::APPEARANCE_LOCAL_SURFACE;
                    appearance.flags |= surface.flags & ~mcvr::abi::APPEARANCE_COLOR_MULTIPLY;
                    if (surface.flags & mcvr::abi::APPEARANCE_COLOR_MULTIPLY)
                        appearance.flags |= mcvr::abi::APPEARANCE_SOURCE_COLOUR;
                    appearance.sourceColour = glm::make_vec4(surface.colour);
                    appearance.light = {surface.light & 65535, (surface.light >> 16) & 65535};
                    appearance.overlay = {surface.overlay & 65535, (surface.overlay >> 16) & 65535};
                    appearance.uvTransform0 = {surface.uv[0], surface.uv[1], surface.uv[2], 0};
                    appearance.uvTransform1 = {surface.uv[3], surface.uv[4], surface.uv[5], 0};
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
                    return appearance;
                };
                if (!range || geometryChanged || appearanceChanged || historyChanged || verifyRigid) {
                    for (uint32_t part = 0; part < model.geometries.size(); ++part) {
                        const auto &geometry = model.geometries[part];
                        const auto lastIndex = pose.indexHistory ? geometry.index : 0;
                        const auto lastPosition = pose.positionHistory ? geometry.position : 0;
                        if (verifyRigid) rigidVerification.back().appearances.push_back(buildAppearance(geometry));
                        if (range) {
                            const auto row = geometryBase + part;
                            if (geometryChanged) {
                                rigidColumns->groups[range->group + 1 + part] = geometry.group;
                                rigidColumns->addresses[0].set(row, geometry.index);
                                rigidColumns->addresses[1].set(row, geometry.position);
                                rigidColumns->addresses[2].set(row, geometry.material);
                                ++rigidGeometryRows;
                            }
                            if (historyChanged) {
                                rigidColumns->addresses[3].set(row, lastIndex);
                                rigidColumns->addresses[4].set(row, lastPosition);
                                ++rigidHistoryRows;
                            }
                            if (appearanceChanged) {
                                rigidColumns->appearance.set(row, buildAppearance(geometry));
                                ++rigidAppearanceRows;
                            }
                        } else {
                            hitGroups.push_back(geometry.group);
                            indexBufferAddrs.push_back(geometry.index);
                            positionBufferAddrs.push_back(geometry.position);
                            materialBufferAddrs.push_back(geometry.material);
                            lastIndexBufferAddrs.push_back(lastIndex);
                            lastPositionBufferAddrs.push_back(lastPosition);
                            instanceAppearances.push_back(buildAppearance(geometry));
                        }
                    }
                }

                mcvr::rigid::HistoryEntry entry{.history = instance.history,
                                                .model = instance.key,
                                                .indexAddress = model.indexAddress,
                                                .positionAddress = model.positionAddress,
                                                .vertexCount = model.vertexCount,
                                                .indexCount = model.indexCount};
                std::memcpy(entry.transform.data(), &transform, sizeof(transform));
                if (current) current->frame.add(entry);
                if (rigidColumns) rigidColumns->recordPose(rigidPoseSlots[i], entry);

                if (!range) {
                    blasOffset.push_back(blasAccu);
                    blasAccu += static_cast<uint32_t>(model.geometries.size());
                    blasGroupAccu += static_cast<uint32_t>(model.geometries.size()) + 1;
                    ++blasIndex;
                }
            }
        }
        if (current) {
            current->frame.finish();
            // Same retention depth as the per-entity history: pop before push, up to imageCount + 1.
            if (views.size() > framework->mainFrameSlotCount()) views.pop();
            views.push(std::move(current));
        }
    }
    const uint32_t rigidEnd = uint32_t(blasIndex);
    if (rigidColumns) {
        std::copy(rigidColumns->groups.begin(), rigidColumns->groups.end(), hitGroups.begin());
        if (mcvr::profile::enabled()) {
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.store.geometry-written", rigidGeometryRows, 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.store.history-written", rigidHistoryRows, 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.store.appearance-written", rigidAppearanceRows,
                                rigidAppearanceRows * sizeof(vk::VertexFormat::InstanceAppearance));
        }
    }

    // Shared-model instance scenes. Model vertex/material/index buffers and BLAS are reused;
    // only the TLAS transform and the small appearance record vary per instance.
    profile.next("pt.prepare.instances-lock");
    std::unique_lock<std::recursive_mutex> instanceHistoryLock(worldPrepare1->instanceTransformMtx_);
    profile.next("pt.prepare.instances-metadata");
    auto previousInstanceTransforms = std::move(worldPrepare1->previousInstanceTransforms_[(
        mcvr::view::viewForSlot(context->frameIndex, framework->mainFrameSlotCount()))]);
    std::map<WorldPrepare::SceneInstanceKey, glm::mat4> currentInstanceTransforms;
    for (const auto &prepared : preparedInstances) {
        auto geometry = prepared.model->geometry;
        if (geometry == nullptr || geometry->blas == nullptr) continue;
        const mcvr::faces::ModelRules faceRules(prepared.model->materialFlags);
        framework->frameResourceRetainer().retain(prepared.model);
        glm::mat4 objectToWorld =
            prepared.space ?
                glm::mat4(mcvr::space::worldToScene(
                    world->spaces().at(prepared.space).matrix * glm::dmat4(prepared.transform), cameraPos)) :
                mcvr::instancing::cameraRelativeTransform(prepared.renderOrigin, prepared.transform, cameraPos);
        const auto transform = mcvr::space::affineRows<VkTransformMatrixKHR>(objectToWorld);
        instanceBuilder.defineInstance(transform, blasIndex, 0x01, blasGroupAccu, faceRules.instanceFlags(transform),
                                       geometry->blas);
        auto contactLocal = glm::dmat4(prepared.transform);
        auto contactPose = prepared.space ? world->spaces().at(prepared.space).matrix * contactLocal : contactLocal;
        if (!prepared.space) contactPose[3] += glm::dvec4(glm::dvec3(prepared.renderOrigin), 0);
        if (!prepared.space) contactLocal = contactPose;
        addContacts({3, prepared.engine, prepared.id}, geometry->mediumMesh, contactPose, contactLocal, prepared.space,
                    false, blasAccu, geometry->geometryCount);
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
        WorldPrepare::SceneInstanceKey historyKey{prepared.engine, prepared.id};
        auto previous = previousInstanceTransforms.find(historyKey);
        lastObjToWorldMats.push_back(previous == previousInstanceTransforms.end() ? objectToWorld : previous->second);
        currentInstanceTransforms.emplace(historyKey, objectToWorld);
        blasOffset.push_back(blasAccu);
        blasAccu += geometry->geometryCount;
        blasGroupAccu += geometry->geometryCount + 1;
        ++blasIndex;
    }
    worldPrepare1
        ->previousInstanceTransforms_[(mcvr::view::viewForSlot(context->frameIndex, framework->mainFrameSlotCount()))] =
        std::move(currentInstanceTransforms);

    profile.next("pt.prepare.chunk-metadata");
    // Chunk
    {
        profile.next("pt.prepare.chunk-history-lock");
        std::unique_lock<std::recursive_mutex> chunkHistoryLock(worldPrepare1->chunkTransformBatchesMtx_);
        profile.next("pt.prepare.chunk-metadata");
        auto &previousChunkTransformBatches = worldPrepare1->previousChunkTransformBatches_[(
            mcvr::view::viewForSlot(context->frameIndex, framework->mainFrameSlotCount()))];
        auto &emptyChunkTransformBatch = worldPrepare1->emptyChunkTransformBatch_;
        auto &previousChunkTransformBatch =
            previousChunkTransformBatches.empty() ? emptyChunkTransformBatch : previousChunkTransformBatches.back();
        if (previousChunkTransformBatches.size() > Renderer::instance().framework()->mainFrameSlotCount()) {
            previousChunkTransformBatches.pop();
        }
        auto &currentChunkTransformBatch = previousChunkTransformBatches.emplace();

        uint64_t cached = cachedSnapshot, rebuilt = rebuiltSnapshot, customHistory = 0, verified = verifiedSnapshot;
        if (staticScene) {
            profile.next("pt.prepare.static-scene-append");
            staticGeometryPrefix = indexBufferAddrs.size();
            size_t groupOffset = hitGroups.size();
            for (const auto &page : staticScene->pages) {
                stableHitRanges.push_back({groupOffset, page->groups.share()});
                groupOffset += page->groups.size();
            }
            contacts.staticSources(staticScene, blasAccu, [&](auto remember) {
                uint32_t pageBase = 0;
                for (const auto &page : staticScene->pages) {
                    for (size_t i = 0; i < page->instances.size(); ++i) {
                        const auto &entry = page->instances[i];
                        const auto &metadata = page->owners[i];
                        auto pose = glm::dmat4(1);
                        pose[3] = glm::dvec4(glm::dvec3(entry.origin), 1);
                        remember({4, entry.slot, 0}, metadata->mediumMesh, pose, pageBase + entry.geometryOffset,
                                 uint32_t(metadata->indices.size()));
                    }
                    pageBase += uint32_t(page->indices.size());
                }
            });
            staticScene->appendHeader(blasAccu, blasOffset, hitGroups);
            if (mcvr::profile::enabled())
                mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.static-copy-avoided", staticScene->geometryCount,
                                    staticScene->geometryCount *
                                        (5 * sizeof(uint64_t) + sizeof(vk::VertexFormat::InstanceAppearance)));
            profile.next("pt.prepare.static-instance-append");
            staticHistory = {uint32_t(blasIndex), uint32_t(staticScene->instanceCount)};
            staticScene->appendInstances(cameraPos, blasIndex, blasGroupAccu, lastObjToWorldMats,
                                         instanceBuilder.instances, instanceBuilder.instanceModels,
                                         instanceBuilder.owners);
            cached += staticScene->instanceCount;
            blasAccu += uint32_t(staticScene->geometryCount);
            blasGroupAccu += uint32_t(staticScene->groupCount);
            blasIndex += int(staticScene->instanceCount);
            profile.next("pt.prepare.external-metadata");
        }
        for (const auto &chunk : frameChunks) {
            const auto &chunk1 = chunk.reference;
            const auto &metadata = chunk.metadata;
            if (metadata)
                framework->frameResourceRetainer().retain(metadata);
            else
                framework->frameResourceRetainer().retain(chunk1);
            const auto faceRules = metadata ? metadata->faces : mcvr::faces::ModelRules(*chunk1->geometryMaterialFlags);
            glm::dmat4 objectToWorld = chunk.objectToWorld;
            const auto absoluteObjectToWorld = objectToWorld;
            addContacts({5, uint64_t(reinterpret_cast<uintptr_t>(chunk.identity.get())), 0},
                        metadata ? metadata->mediumMesh : chunk1->mediumMesh, objectToWorld, chunk.objectToSpace,
                        chunk.contactSpace, !chunk.custom && !chunk.external, blasAccu, chunk.geometryCount);
            objectToWorld = mcvr::space::worldToScene(objectToWorld, cameraPos);
            // External slots may receive their first transform after geometry publication.
            // Preserve that preceding frame too; only primary slots cannot become custom.
            mcvr::recordChunkTransform(currentChunkTransformBatch, chunk.identity, objectToWorld,
                                       chunk.custom || chunk.external, !cacheEnabled);
            customHistory += chunk.custom;

            const auto transform = mcvr::space::affineRows<VkTransformMatrixKHR>(objectToWorld);

            instanceBuilder.defineInstance(transform, blasIndex, 0x01, blasGroupAccu,
                                           faceRules.instanceFlags(transform), chunk.blas);

            const size_t firstGroup = hitGroups.size(), firstGeometry = indexBufferAddrs.size();
            if (metadata) {
                metadata->append(chunk.custom, hitGroups, indexBufferAddrs, positionBufferAddrs, materialBufferAddrs,
                                 lastIndexBufferAddrs, lastPositionBufferAddrs, instanceAppearances);
            } else {
                hitGroups.push_back(mcvr::hitgroups::shadow);
                for (int j = 0; j < chunk.geometryCount; j++) {
                    if (chunk1->geometryGroupNames != nullptr &&
                        j < static_cast<int>(chunk1->geometryGroupNames->size())) {
                        hitGroups.push_back(mcvr::hitgroups::intern((*chunk1->geometryGroupNames)[j]));
                    } else {
                        hitGroups.push_back(defaultGroup);
                    }
                }

                for (int j = 0; j < chunk.geometryCount; j++) {
                    indexBufferAddrs.push_back((*chunk1->indexBufferAddresses)[j]);
                    positionBufferAddrs.push_back((*chunk1->positionBufferAddresses)[j]);
                    materialBufferAddrs.push_back((*chunk1->materialBufferAddresses)[j]);
                    auto appearance = identityAppearance();
                    if (chunk1->geometryMaterialFlags != nullptr &&
                        j < static_cast<int>(chunk1->geometryMaterialFlags->size())) {
                        appearance.materialFlags = faceRules.shaderFlags((*chunk1->geometryMaterialFlags)[j]);
                    }
                    instanceAppearances.push_back(appearance);
                    if (chunk.custom) {
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
                for (uint32_t j = 0; j < chunk.geometryCount; ++j) {
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
                        lastIndexBufferAddrs[k] != (chunk.custom ? (*chunk1->indexBufferAddresses)[j] : 0) ||
                        lastPositionBufferAddrs[k] != (chunk.custom ? (*chunk1->positionBufferAddresses)[j] : 0) ||
                        std::memcmp(&expected, &instanceAppearances[k], sizeof(expected)))
                        throw std::logic_error("Chunk scene cache geometry mismatch");
                }
                ++verified;
            }

            if (chunk.colourTarget.w != 0) {
                const auto localPlane =
                    glm::vec4(glm::transpose(absoluteObjectToWorld) * glm::dvec4(chunk.colourPlane));
                for (uint32_t j = 0; j < chunk.geometryCount; ++j) {
                    auto &appearance = instanceAppearances[firstGeometry + j];
                    appearance.sectionColourPlane = localPlane;
                    appearance.sectionColourTarget = chunk.colourTarget;
                }
            }
            {
                const auto previousObjectToWorld = mcvr::previousChunkTransform(
                    previousChunkTransformBatch, chunk.identity, objectToWorld, chunk.custom);
                if (verifyCache && !chunk.custom && previousObjectToWorld != objectToWorld)
                    throw std::logic_error("Static chunk history must use its current camera-relative transform");
                glm::mat4 lastObjToWorldMat = glm::mat4(previousObjectToWorld);
                lastObjToWorldMats.push_back(lastObjToWorldMat);
            }

            blasOffset.push_back(blasAccu);
            blasAccu += chunk.geometryCount;
            blasGroupAccu += chunk.geometryCount + 1;

            blasIndex++;
        }
        if (mcvr::profile::enabled()) {
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.cache-hit", cached, 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.cache-build", rebuilt, 0);
            mcvr::profile::emit(mcvr::profile::frame, 5, "chunk.scene.custom-history", customHistory, 0);
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

    auto *lodUbo = static_cast<vk::Data::WorldUBO *>(Renderer::instance().buffers()->worldUniformBuffer()->mappedPtr());
    lodUbo->lodGeometry = 0;
    profile.next("pt.prepare.voxy-metadata");
    if (auto tables = lodTables) {
        auto &previous = worldPrepare1->previousLodHistory_[viewIndex];
        std::map<std::pair<uint64_t, uint64_t>, mcvr::lod::HistoryEntry> current;
        static const bool verifyLodHistory = [] {
            const char *value = std::getenv("MCVR_LOD_HISTORY_VERIFY");
            return value && std::string_view(value) == "1";
        }();
        uint32_t movingHistoryChecked = 0;
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
                auto contactPose = glm::dmat4(1);
                contactPose[3] = glm::dvec4(glm::dvec3(entry.origin), 1);
                addContacts({6, entry.slot, 0}, metadata->mediumMesh, contactPose, contactPose, 0, true, blasAccu,
                            uint32_t(metadata->indices.size()));
                metadata->append(false, hitGroups, indexBufferAddrs, positionBufferAddrs, materialBufferAddrs,
                                 lastIndexBufferAddrs, lastPositionBufferAddrs, instanceAppearances);
                lastObjToWorldMats.push_back(objectToWorld);
                blasOffset.push_back(blasAccu);
                blasAccu += uint32_t(metadata->indices.size());
                blasGroupAccu += uint32_t(metadata->groups.size());
                ++blasIndex;
            });
        }
        // The immutable table owns these versions through GPU completion.
        for (auto &node : tables->sections) {
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
            // Static records are inserted during upload; this cursor includes that directory.
            if (!lodUbo->lodGeometry) lodUbo->lodGeometry = blasAccu + 1;
            const auto identity = std::make_pair(node->revision, node->positions->bufferAddress());
            const auto historyKey = std::make_pair(node->epoch, node->key);
            auto old = previous.find(historyKey);
            const bool hasPrevious = old != previous.end() && old->second.matches(identity);
            lastIndexBufferAddrs.push_back(hasPrevious ? node->indices->bufferAddress() : 0);
            lastPositionBufferAddrs.push_back(hasPrevious ? node->positions->bufferAddress() : 0);
            // Previous-scene positions are relative to the previous camera, not this frame's origin.
            lastObjToWorldMats.push_back(hasPrevious ? old->second.previousTransform(identity, objectToWorld) :
                                                       objectToWorld);
            if (verifyLodHistory && hasPrevious) {
                const auto *lastUbo = static_cast<const vk::Data::WorldUBO *>(
                    Renderer::instance().buffers()->lastWorldUniformBuffer()->mappedPtr());
                const auto expected = mcvr::space::worldOriginFloatLocal(glm::mat4(1), glm::dvec3(node->origin),
                                                                         glm::dvec3(lastUbo->cameraPos));
                for (int column = 0; column < 4; ++column)
                    if (glm::length(lastObjToWorldMats.back()[column] - expected[column]) > 1e-4f)
                        throw std::logic_error("LOD history is not relative to the previous shader camera");
                if (glm::length(cameraPos - glm::dvec3(lastUbo->cameraPos)) > 1e-5) ++movingHistoryChecked;
            }
            auto unknownMesh = std::make_shared<mcvr::medium::Mesh>();
            unknownMesh->complete = false;
            unknownMesh->bounds = {glm::dvec3(0), glm::dvec3(16u << node->level)};
            auto contactPose = glm::dmat4(1);
            contactPose[3] = glm::dvec4(glm::dvec3(node->origin), 1);
            addContacts({7, node->epoch, node->key}, unknownMesh, contactPose, contactPose, 0, true, blasAccu, 1);
            auto appearance = identityAppearance();
            appearance.flags = mcvr::abi::APPEARANCE_LOD;
            appearance.overlay = {node->origin.x, node->origin.y};
            appearance.light = {node->origin.z, int(node->level)};
            // The LOD tag selects this union; surface-layer ownership remains null.
            appearance.lightSectionStart = uint32_t(tables->header->bufferAddress());
            appearance.lightSectionCount = uint32_t(tables->header->bufferAddress() >> 32);
            instanceAppearances.push_back(appearance);
            blasOffset.push_back(blasAccu++);
            blasGroupAccu += 2;
            ++blasIndex;
            current.emplace(historyKey, mcvr::lod::HistoryEntry{identity, objectToWorld});
        }
        previous = std::move(current);
        if (verifyLodHistory && movingHistoryChecked) {
            static std::atomic<uint64_t> movingFrames{0};
            if (movingFrames.fetch_add(1) % 60 == 0)
                mcvr::log::info("LodHistoryCheck")
                    << "moving-node-transforms=" << movingHistoryChecked << " previous-shader-camera=PASS" << std::endl;
        }
    }

    if (verifyRigid) {
        mcvr::profile::Scope verification("pt.prepare.rigid-verify");
        for (size_t i = staticHistory.x; i < size_t(staticHistory.x) + staticHistory.y; ++i) {
            glm::mat4 fromTlas(1);
            const auto &transform = instanceBuilder.instances.at(i).transform;
            fromTlas[3] = {transform.matrix[0][3], transform.matrix[1][3], transform.matrix[2][3], 1};
            if (std::memcmp(&lastObjToWorldMats.at(i), &fromTlas, sizeof(fromTlas)) != 0)
                throw std::logic_error("Rigid scene store mismatch: " + std::to_string(i) + " static history");
        }
        for (const auto &expected : rigidVerification) {
            const auto require = [&](bool agrees, const char *field) {
                if (!agrees)
                    throw std::logic_error("Rigid scene store mismatch: " + std::to_string(expected.key) + " " + field);
            };
            const auto &instance = instanceBuilder.instances.at(expected.slot);
            require(instance.instanceCustomIndex == expected.slot && blasOffset.at(expected.slot) == expected.base,
                    "instance offset");
            require(std::memcmp(&instance.transform, &expected.transform, sizeof(instance.transform)) == 0, "pose");
            require(instance.mask == expected.mask && instance.flags == expected.flags, "mask and flags");
            require(instance.accelerationStructureReference == expected.model->blas->blasDeviceAddress(), "BLAS");
            require(instance.instanceShaderBindingTableRecordOffset == expected.group &&
                        hitGroups.at(expected.group) == mcvr::hitgroups::shadow,
                    "SBT shadow");
            require(lastObjToWorldMats.at(expected.slot) == expected.previous, "history pose");
            for (uint32_t part = 0; part < expected.model->geometries.size(); ++part) {
                const auto &geometry = expected.model->geometries[part];
                const auto row = expected.base + part;
                const auto index = expected.resident ? row : row - reservedRigidRecords;
                const auto address = [&](size_t column, const auto &dense) {
                    return expected.resident ? rigidColumns->addresses[column].at(index) : dense.at(index);
                };
                require(hitGroups.at(expected.group + 1 + part) == geometry.group, "SBT geometry");
                require(address(0, indexBufferAddrs) == geometry.index &&
                            address(1, positionBufferAddrs) == geometry.position &&
                            address(2, materialBufferAddrs) == geometry.material,
                        "geometry buffers");
                require(address(3, lastIndexBufferAddrs) == (expected.indexHistory ? geometry.index : 0) &&
                            address(4, lastPositionBufferAddrs) == (expected.positionHistory ? geometry.position : 0),
                        "history buffers");
                const auto &actual =
                    expected.resident ? rigidColumns->appearance.at(index) : instanceAppearances.at(index);
                require(std::memcmp(&actual, &expected.appearances[part], sizeof(actual)) == 0, "appearance");
            }
        }
        struct VerifyWork {
            std::atomic<uint64_t> frames{0}, nonEmpty{0}, spaces{0}, geometry{0}, appearance{0};
        };
        static VerifyWork verified[2];
        auto &work = verified[scene != nullptr];
        const auto geometry = work.geometry.fetch_add(rigidGeometryRows, std::memory_order_relaxed) + rigidGeometryRows;
        const auto appearance =
            work.appearance.fetch_add(rigidAppearanceRows, std::memory_order_relaxed) + rigidAppearanceRows;
        const auto nonEmpty =
            work.nonEmpty.fetch_add(!rigidVerification.empty(), std::memory_order_relaxed) + !rigidVerification.empty();
        const auto spaces = work.spaces.fetch_add(verifiedSpace, std::memory_order_relaxed) + verifiedSpace;
        const auto frames = work.frames.fetch_add(1, std::memory_order_relaxed) + 1;
        if (frames % 60 == 0)
            mcvr::log::info("SceneStore")
                << "verify view=" << (scene ? "auxiliary" : "main") << " frames=" << frames << " nonempty=" << nonEmpty
                << " spaces=" << spaces << " geometry=" << geometry << " appearance=" << appearance << " at="
                << std::chrono::duration_cast<std::chrono::milliseconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                       .count()
                << std::endl;
        if (mcvr::profile::enabled())
            mcvr::profile::emit(mcvr::profile::frame, 5, "scene.store.rigid-verified", rigidVerification.size(), 0);
    }
    profile.next("pt.prepare.medium-contacts");
    constexpr auto contactUsage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    const auto contactBarrier = [&](const auto &buffer, VkPipelineStageFlags2 sourceStage, VkAccessFlags2 sourceAccess,
                                    VkPipelineStageFlags2 targetStage, VkAccessFlags2 targetAccess) {
        worldCommandBuffer->barriersBufferImage({{.srcStageMask = sourceStage,
                                                  .srcAccessMask = sourceAccess,
                                                  .dstStageMask = targetStage,
                                                  .dstAccessMask = targetAccess,
                                                  .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                                  .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                                  .buffer = buffer}},
                                                {});
    };
    const auto exposeContacts = [&](const auto &buffer) {
        contactBarrier(buffer, VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                       VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR, VK_ACCESS_2_SHADER_READ_BIT);
    };
    {
        mcvr::profile::Phases contactsProfile("pt.medium.contact-pairs");
        contacts.publish(
            blasAccu,
            [&](std::span<const std::byte> bytes) {
                mcvr::profile::Scope upload("pt.medium.buffer-upload");
                auto buffer = vk::DeviceLocalBuffer::create(vma, device, false, bytes.size(), contactUsage);
                buffer->writeToStagingBuffer(
                    [&](void *destination, size_t) { std::memcpy(destination, bytes.data(), bytes.size()); });
                buffer->uploadToBuffer(worldCommandBuffer);
                exposeContacts(buffer);
                return buffer;
            },
            [&](const auto &buffer) { framework->frameResourceRetainer().retain(buffer); },
            [&](std::span<const mcvr::medium::Filter> directory,
                std::span<const mcvr::medium::Publication::Range> dirty, const auto &previous) {
                constexpr size_t rowBytes = sizeof(mcvr::medium::Filter);
                auto buffer = vk::DeviceLocalBuffer::create(vma, device, false, directory.size_bytes(), contactUsage);
                auto staging = vk::HostVisibleBuffer::create(vma, device, contacts.uploadedBytes,
                                                             VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
                auto *mapped = static_cast<std::byte *>(staging->mappedPtr());
                std::vector<VkBufferCopy> uploads, copies;
                uploads.reserve(dirty.size());
                copies.reserve(dirty.size() + 1);
                size_t cursor = 0, offset = 0;
                for (const auto [start, end] : dirty) {
                    if (start > cursor)
                        copies.push_back({cursor * rowBytes, cursor * rowBytes, (start - cursor) * rowBytes});
                    const size_t bytes = (end - start) * rowBytes;
                    std::memcpy(mapped + offset, directory.data() + start, bytes);
                    uploads.push_back({offset, start * rowBytes, bytes});
                    offset += bytes;
                    cursor = end;
                }
                if (cursor < directory.size())
                    copies.push_back({cursor * rowBytes, cursor * rowBytes, (directory.size() - cursor) * rowBytes});
                staging->flush();
                auto &retain = framework->frameResourceRetainer();
                retain.retain(staging);
                const auto cmd = worldCommandBuffer->vkCommandBuffer();
                if (!copies.empty()) {
                    retain.retain(previous);
                    contactBarrier(previous, VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT, VK_ACCESS_2_MEMORY_WRITE_BIT,
                                   VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_READ_BIT);
                    vkCmdCopyBuffer(cmd, previous->vkBuffer(), buffer->vkBuffer(), uint32_t(copies.size()),
                                    copies.data());
                }
                vkCmdCopyBuffer(cmd, staging->vkBuffer(), buffer->vkBuffer(), uint32_t(uploads.size()), uploads.data());
                exposeContacts(buffer);
                return buffer;
            },
            [&](const char *name) { contactsProfile.next(name); });
    }
    mediumContactBuffer = contacts.directoryBuffer;
    if (mcvr::profile::enabled()) {
        mcvr::profile::emit(mcvr::profile::frame, 5, "medium.proof.publications", contacts.prepared, contacts.gpuBytes);
        mcvr::profile::emit(mcvr::profile::frame, 5, "medium.proof.cpu-resident", contacts.scene.sources().size(),
                            contacts.cpuBytes);
        mcvr::profile::emit(mcvr::profile::frame, 5, "medium.directory.host-upload", contacts.directoryRows,
                            contacts.uploadedBytes);
        mcvr::profile::emit(mcvr::profile::frame, 5, "medium.directory.gpu-copy", 0, contacts.copiedBytes);
        mcvr::profile::emit(mcvr::profile::frame, 5, "medium.proof.lifetime-visits", contacts.scene.lifetimeVisits, 0);
        if (mcvr::profile::frame % 60 == 0)
            mcvr::profile::emit(mcvr::profile::frame, 5, "medium.proof.cpu-mesh", contacts.scene.pairs().size(),
                                contacts.scene.meshBytes());
    }
    profile.next("pt.prepare.tlas-build-record");
    if (!hitGroupLayout || *hitGroupLayout != hitGroups) {
        hitGroupLayout = std::make_shared<const std::vector<mcvr::hitgroups::Id>>(hitGroups);
        if (mcvr::profile::enabled())
            mcvr::profile::emit(mcvr::profile::frame, 5, "sbt.layout.publish", 1,
                                hitGroups.size() * sizeof(mcvr::hitgroups::Id));
    }
    if (mcvr::profile::enabled()) {
        const auto scratchBytes = metadataScratch.capacityBytes();
        mcvr::profile::emit(mcvr::profile::frame, 5, "metadata.cpu-capacity-growth",
                            scratchBytes > previousScratchBytes,
                            scratchBytes > previousScratchBytes ? scratchBytes - previousScratchBytes : 0);
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
               mcvr::sameInstanceContent(instanceBuilder.instances, instanceBuilder.instanceModels,
                                         previousTlasBuilder->beginInstanceBuilder().instances,
                                         previousTlasBuilder->beginInstanceBuilder().instanceModels)) {
        tlas = std::move(previousTlas);
        tlasBuilder = std::move(previousTlasBuilder);
        if (mcvr::profile::enabled()) mcvr::profile::emit(mcvr::profile::frame, 5, "world.tlas.reuse", 1, 0);
    } else {
        const auto tlasStamp = SceneRecordingScope::active() ?
                                   -1 :
                                   context->auditGpu.begin(worldCommandBuffer->vkCommandBuffer(), "tlas-build-gpu");
        const VkBufferUsageFlags historyUsage = packHistory ? VK_BUFFER_USAGE_STORAGE_BUFFER_BIT : 0;
        tlas = instanceBuilder.endInstanceBuilder(device, vma, historyUsage)
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
    std::vector<glm::uvec2> sampleKeys;
    if (sparseRigid) {
        // Resident sources keep their stochastic sequence when geometry slots move.
        // Transient sources keep the legacy dense sequence.
        sampleKeys.resize(blasOffset.size(), glm::uvec2(UINT32_MAX));
        uint32_t logicalInstance = 0, logicalGeometry = 0;
        const auto sample = [&](uint32_t slot, uint32_t geometries) {
            sampleKeys[slot] = {logicalInstance++, logicalGeometry};
            logicalGeometry += geometries;
        };
        const auto sampleDense = [&](uint32_t first, uint32_t end) {
            for (uint32_t i = first; i < end; ++i) {
                const auto next = i + 1 < blasOffset.size() ? blasOffset[i + 1] : blasAccu;
                sample(i, next - blasOffset[i]);
            }
        };
        sampleDense(reservedRigidSlots, rigidStart);
        for (const auto &draw : rigidSampleOrder) {
            sample(draw.x, draw.y);
            if (draw.z != UINT32_MAX) sampleKeys[draw.x] = glm::uvec2(draw.z);
        }
        sampleDense(rigidEnd, uint32_t(blasOffset.size()));
    }
    const auto columns =
        rigidColumns ? rigidColumns->publish(reservedRigidRecords) : mcvr::RigidSceneColumns::Publication{};
    uploadBuffer(blasOffset, indexBufferAddrs, positionBufferAddrs, materialBufferAddrs, lastIndexBufferAddrs,
                 lastPositionBufferAddrs, lastObjToWorldMats, instanceAppearances, waterExclusions, staticScene,
                 staticGeometryPrefix, sampleKeys, columns, packHistory ? std::optional(staticHistory) : std::nullopt);
#ifdef MCVR_BUILD_SCENE_REPLAY
    if (tlasBuilder)
        mcvr::replay::capture(*this, tlasBuilder->beginInstanceBuilder(), blasOffset, indexBufferAddrs,
                              positionBufferAddrs, materialBufferAddrs, instanceAppearances,
                              std::as_bytes(std::span(contacts.directory)), waterExclusions, staticScene,
                              staticGeometryPrefix, sampleKeys, columns);
#endif
    profile.next("pt.prepare.cleanup");
}

void WorldPrepareContext::setupHitGroupSbt(const std::unordered_map<std::string, uint32_t> &hitGroupNameToIndex,
                                           uint32_t fallbackHitGroupIndex,
                                           uint32_t shadowHitGroupIndex,
                                           mcvr::hitgroups::ResolvedLayout &resolvedLayout,
                                           std::shared_ptr<vk::CommandBuffer> commandBuffer,
                                           std::shared_ptr<vk::SBT> updateSbt,
                                           std::shared_ptr<vk::SBT> querySbt,
                                           const std::string &auditName) {
    mcvr::profile::Phases profile("pt.sbt.lookup");
    const auto hitGroupIndices = resolvedLayout.resolve(hitGroupLayout, [&] {
        if (mcvr::profile::enabled())
            mcvr::profile::emit(mcvr::profile::frame, 5, "sbt.layout.resolve", 1,
                                hitGroupLayout->size() * sizeof(uint32_t));
        return mcvr::hitgroups::Registry::global().resolve(hitGroupNameToIndex, fallbackHitGroupIndex,
                                                           shadowHitGroupIndex);
    });

    profile.next("pt.sbt.allocate-copy-record");
    if (updateSbt != nullptr) {
        if (mcvr::profile::flowAuditEnabled())
            updateSbt->setupHitSBT(hitGroupIndices, commandBuffer, auditName + "/update", stableHitRanges,
                                   resolvedLayout.palette());
        else
            updateSbt->setupHitSBT(hitGroupIndices, commandBuffer, auditName, stableHitRanges,
                                   resolvedLayout.palette());
    }
    if (querySbt != nullptr) {
        if (mcvr::profile::flowAuditEnabled())
            querySbt->setupHitSBT(hitGroupIndices, commandBuffer, auditName + "/query", stableHitRanges,
                                  resolvedLayout.palette());
        else
            querySbt->setupHitSBT(hitGroupIndices, commandBuffer, auditName, stableHitRanges, resolvedLayout.palette());
    }
}
