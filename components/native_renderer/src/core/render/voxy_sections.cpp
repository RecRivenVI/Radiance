#include "voxy_sections.hpp"
#include "render_framework.hpp"
#include "renderer.hpp"
#include "textures.hpp"
#include "chunks.hpp"
#include "voxy_layout.hpp"
#include <cstring>
#include <limits>

namespace mcvr {
namespace {
    constexpr uint64_t budget = 256ull * 1024 * 1024;
    constexpr auto geometryUsage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                                   VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
    std::shared_ptr<vk::DeviceLocalBuffer> upload(Framework &f, const void *data, size_t bytes) {
        auto result = vk::DeviceLocalBuffer::create(f.vma(), f.device(), false, std::max(size_t(4), bytes),
                                                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT |
                                                        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
        uint32_t zero = 0;
        result->uploadToStagingBuffer(bytes ? const_cast<void *>(data) : &zero, std::max(size_t(4), bytes), 0);
        return result;
    }
} // namespace

void VoxySections::begin(uint64_t epoch) {
    std::lock_guard lock(mutex_);
    if (epoch == 0) throw std::invalid_argument("Voxy epoch is zero");
    clear();
    epoch_ = epoch;
}
void VoxySections::end(uint64_t epoch) {
    std::lock_guard lock(mutex_);
    if (epoch == epoch_) clear();
}
void VoxySections::clear(bool releaseNear) {
    std::lock_guard lock(mutex_);
    sections_.clear();
    selected_.clear();
    pending_.clear();
    requestedRoots_ = {};
    ++farRevision_;
    models_.reset();
    tables_.reset();
    if (releaseNear) {
        emptyNear_.clear();
        ++emptyRevision_;
    }
    epoch_ = bytes_ = built_ = 0;
}

void VoxySections::nearEmpty(uint32_t slot, glm::ivec3 origin) {
    std::lock_guard lock(mutex_);
    auto old = emptyNear_.find(slot);
    if (old != emptyNear_.end() && old->second == origin) return;
    emptyNear_[slot] = origin;
    ++emptyRevision_;
}

std::shared_ptr<VoxyFrameTables> VoxySections::tables(Chunks &chunks,
                                                      const std::shared_ptr<vk::CommandBuffer> &commands) {
    // Caller owns Chunks::mutex; there is no far -> chunk lock acquisition.
    std::lock_guard lock(mutex_);
    if (!models_) return {};
    auto scene = chunks.staticScene();
    if (tables_ && tables_->models == models_ && tables_->nearScene == scene &&
        tables_->emptyRevision == emptyRevision_ && tables_->farRevision == farRevision_)
        return tables_;
    auto result = std::make_shared<VoxyFrameTables>();
    result->models = models_;
    result->nearScene = scene;
    result->emptyRevision = emptyRevision_;
    result->farRevision = farRevision_;
    std::set<std::array<int32_t, 3>> coordinates;
    for (const auto &instance : scene->instances)
        coordinates.insert({instance.origin.x / 16, instance.origin.y / 16, instance.origin.z / 16});
    for (auto &[slot, origin] : emptyNear_) {
        if (slot >= chunks.primaryChunkCount()) continue;
        const auto &current = chunks.chunks()[slot];
        if (!current || current->blas || current->x != origin.x || current->y != origin.y || current->z != origin.z)
            continue;
        coordinates.insert({origin.x / 16, origin.y / 16, origin.z / 16});
    }
    auto keyAt = [](uint32_t level, glm::ivec3 origin) {
        const auto x = uint64_t(uint32_t(origin.x >> (5 + level)) & 0xffffff);
        const auto y = uint64_t(uint32_t(origin.y >> (5 + level)) & 0xff);
        const auto z = uint64_t(uint32_t(origin.z >> (5 + level)) & 0xffffff);
        return (uint64_t(level) << 60) | (y << 52) | (z << 28) | (x << 4);
    };
    result->handoffNear = std::make_shared<StaticChunkScene>();
    uint64_t handoffBytes = 0;
    std::set<const void *> handoffBuffers;
    auto keepMissing = [&](const std::shared_ptr<const StaticChunkScene> &old) {
        if (!old) return;
        for (size_t i = 0; i < old->instances.size(); ++i) {
            const auto &instance = old->instances[i];
            const auto origin = instance.origin;
            const std::array<int32_t, 3> cell{origin.x / 16, origin.y / 16, origin.z / 16};
            if (coordinates.contains(cell) || !requestedRoots_.contains(keyAt(4, origin))) continue;
            bool replacement = false;
            for (uint32_t level = 0; level <= 4; ++level) {
                const auto key = keyAt(level, origin);
                if (selected_.contains(key) && sections_.at(key)->recorded) {
                    replacement = true;
                    break;
                }
            }
            if (replacement) continue;
            for (const auto &[identity, bytes] : old->owners[i]->memoryFootprint)
                if (identity && handoffBuffers.insert(identity).second) handoffBytes += bytes;
            if (handoffBytes > 128ull * 1024 * 1024 || result->handoffNear->instances.size() >= 2048)
                throw std::length_error("Voxy near/far handoff budget exceeded; no valid owner was overwritten");
            result->handoffNear->add(instance.slot, origin, instance.blas, old->owners[i]);
            coordinates.insert(cell);
        }
    };
    if (tables_) {
        keepMissing(tables_->nearScene);
        keepMissing(tables_->handoffNear);
    }
    size_t capacity = 1;
    while (capacity < coordinates.size() * 2) capacity *= 2;
    if (capacity > 1u << 20) throw std::length_error("Voxy near coverage budget");
    std::vector<glm::ivec4> cells(capacity, glm::ivec4(0));
    auto hash = [](const std::array<int32_t, 3> &c) {
        return (uint32_t(c[0]) * 73856093u) ^ (uint32_t(c[1]) * 19349663u) ^ (uint32_t(c[2]) * 83492791u);
    };
    for (const auto &c : coordinates) {
        size_t index = hash(c) & (capacity - 1);
        while (cells[index].w) index = (index + 1) & (capacity - 1);
        cells[index] = {c[0], c[1], c[2], 1};
    }
    result->coverage = upload(framework_, cells.data(), cells.size() * sizeof(cells[0]));
    struct Header {
        uint64_t models, colours, coverage;
        uint32_t modelCount, colourCount, coverageMask, padding;
    };
    static_assert(sizeof(Header) == 40);
    const Header header{models_->records->bufferAddress(),
                        models_->colours->bufferAddress(),
                        result->coverage->bufferAddress(),
                        models_->count,
                        models_->colourCount,
                        uint32_t(capacity - 1),
                        0};
    result->header = upload(framework_, &header, sizeof(header));
    result->coverage->uploadToBuffer(commands);
    result->header->uploadToBuffer(commands);
    commands->barriersMemory(
        {vk::CommandBuffer::MemoryBarrier{.srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                          .srcAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
                                          .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                                          .dstAccessMask = VK_ACCESS_2_SHADER_STORAGE_READ_BIT}});
    framework_.frameResourceRetainer().retain(result);
    tables_ = result;
    return result;
}
void VoxySections::models(uint64_t epoch, std::span<const uint32_t> records, std::span<const uint32_t> colours) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return;
    if (records.empty() || records.size() % 16 || records.size() > 65536 * 16 || colours.size() > 8 * 1024 * 1024)
        throw std::invalid_argument("Voxy model table ABI/budget");
    auto model = std::make_shared<VoxyModels>();
    model->count = uint32_t(records.size() / 16);
    model->colourCount = uint32_t(colours.size());
    model->cpu.assign(records.begin(), records.end());
    std::set<uint32_t> pages;
    for (size_t i = 0; i < records.size(); i += 16) {
        if (records[i + 13] != 0x56585054) continue;
        if (records[i + 9] >= 4096 || records[i + 12] >= 256 || records[i + 11] > 2)
            throw std::invalid_argument("Voxy model page/material range");
        pages.insert(records[i + 9]);
        if ((records[i + 6] & 2) && records[i + 7] >= colours.size())
            throw std::invalid_argument("Voxy model biome index range");
    }
    auto textures = Renderer::instance().textures();
    std::lock_guard texturesLock(textures->mtx_);
    for (uint32_t page : pages) {
        auto image = textures->texture(page);
        auto sampler = textures->sampler(page);
        if (!image || !sampler) throw std::invalid_argument("Voxy model texture has no owned image");
        model->images.push_back(image);
        model->samplers.push_back(sampler);
    }
    model->records = upload(framework_, records.data(), records.size_bytes());
    model->colours = upload(framework_, colours.data(), colours.size_bytes());
    models_ = std::move(model);
}

bool VoxySections::section(uint64_t epoch, uint64_t key, uint64_t revision, std::span<const uint64_t> quads) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return false;
    const auto level = uint32_t(key >> 60);
    if (level > 4 || (key & 15) || quads.size() > 8 * 65536 || revision == 0)
        throw std::invalid_argument("Voxy section ABI/budget");
    const auto old = sections_.find(key);
    if (old != sections_.end() && old->second->revision >= revision) return true;
    const uint64_t bytes = quads.size() * (8 + 64 + 24);
    const auto oldBytes = old == sections_.end() ? 0 : old->second->bytes;
    if (bytes > budget || bytes_ - oldBytes > budget - bytes || (old == sections_.end() && sections_.size() >= 8192))
        return false;
    if (!quads.empty() && !models_) throw std::logic_error("Voxy section before model publication");
    for (uint64_t q : quads) {
        const auto id = uint32_t((q >> 26) & 65535);
        const auto face = uint32_t(q & 7);
        if (face > 5 || id >= models_->count || models_->cpu[id * 16 + 13] != 0x56585054 ||
            models_->cpu[id * 16 + face] == UINT32_MAX)
            throw std::invalid_argument("Voxy quad references an unpublished model/face");
        if ((models_->cpu[id * 16 + 6] & 2) &&
            uint64_t(models_->cpu[id * 16 + 7]) + ((q >> 46) & 511) >= models_->colourCount)
            throw std::invalid_argument("Voxy quad biome exceeds published table");
    }
    auto node = std::make_shared<VoxySection>();
    node->key = key;
    node->epoch = epoch_;
    node->revision = revision;
    node->level = level;
    node->count = uint32_t(quads.size());
    node->bytes = bytes;
    const int x = int(int64_t(key << 36) >> 40);
    const int y = int(int64_t(key << 4) >> 56);
    const int z = int(int64_t(key << 12) >> 40);
    const int64_t scale = 32ll << level;
    for (const auto coordinate : {x, y, z})
        if (coordinate * scale < INT32_MIN || coordinate * scale > INT32_MAX)
            throw std::invalid_argument("Voxy section exceeds world coordinate range");
    node->origin = {int(x * scale), int(y * scale), int(z * scale)};
    if (!quads.empty()) {
        auto &f = framework_;
        node->quads = upload(f, quads.data(), quads.size_bytes());
        node->decodeModels = models_;
        node->positions = vk::DeviceLocalBuffer::create(f.vma(), f.device(), false, quads.size() * 64, geometryUsage);
        node->indices = vk::DeviceLocalBuffer::create(f.vma(), f.device(), false, quads.size() * 24, geometryUsage);
        node->table = vk::DescriptorTableBuilder{}
                          .beginDescriptorLayoutSet()
                          .beginDescriptorLayoutSetBinding()
                          .defineDescriptorLayoutSetBinding(
                              {0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                          .defineDescriptorLayoutSetBinding(
                              {1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                          .defineDescriptorLayoutSetBinding(
                              {2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                          .defineDescriptorLayoutSetBinding(
                              {3, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                          .endDescriptorLayoutSetBinding()
                          .endDescriptorLayoutSet()
                          .definePushConstant({VK_SHADER_STAGE_COMPUTE_BIT, 0, 8})
                          .build(f.device());
        node->table->bindBuffer(node->quads, 0, 0)
            ->bindBuffer(models_->records, 0, 1)
            ->bindBuffer(node->positions, 0, 2)
            ->bindBuffer(node->indices, 0, 3);
        if (!pipeline_) {
            auto shader =
                vk::Shader::create(f.device(), (Renderer::folderPath / "shaders/world/voxy_convert_comp.spv").string());
            pipeline_ =
                vk::ComputePipelineBuilder{}.defineShader(shader).definePipelineLayout(node->table).build(f.device());
        }
        node->builder = vk::BLASBuilder::create();
        node->builder->beginGeometries()
            ->defineTriangleGeomrtry<vk::VertexFormat::PositionVertex>(node->positions->bufferAddress(),
                                                                       node->count * 4, node->indices->bufferAddress(),
                                                                       node->count * 6, false)
            .endGeometries();
        node->blas = node->builder->defineBuildProperty(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                         ->querySizeInfo(f.device())
                         ->allocateBuffers(f.physicalDevice(), f.device(), f.vma())
                         ->build(f.device());
        node->bytes += node->blas->blasBuffer()->size();
        if (node->bytes > budget || bytes_ - oldBytes > budget - node->bytes) return false;
    } else
        node->recorded = true;
    bytes_ = bytes_ - oldBytes + node->bytes;
    sections_[key] = std::move(node);
    ++farRevision_;
    if (!quads.empty())
        pending_.insert(key);
    else
        pending_.erase(key);
    return true;
}

void VoxySections::select(uint64_t epoch, std::span<const uint64_t> keys, voxy::RootCoverage roots) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return;
    voxy::validateCut(keys);
    std::set<uint64_t> selection(keys.begin(), keys.end());
    for (auto key : selection)
        if (!sections_.contains(key)) throw std::invalid_argument("Voxy selected section has no owned version");
    selected_ = std::move(selection);
    requestedRoots_ = std::move(roots);
    ++farRevision_;
}

void VoxySections::retain(uint64_t epoch, std::span<const uint64_t> keys) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return;
    std::set<uint64_t> keep(keys.begin(), keys.end());
    for (auto it = sections_.begin(); it != sections_.end();) {
        if (keep.contains(it->first)) {
            ++it;
            continue;
        }
        // Recorded frames retain the immutable node independently of residency.
        bytes_ -= it->second->bytes;
        selected_.erase(it->first);
        pending_.erase(it->first);
        it = sections_.erase(it);
        ++farRevision_;
    }
}

void VoxySections::record(const std::shared_ptr<vk::CommandBuffer> &commands) {
    std::lock_guard lock(mutex_);
    if (!models_) return;
    auto &f = framework_;
    f.frameResourceRetainer().retain(models_);
    if (!models_->recorded) {
        models_->records->uploadToBuffer(commands);
        models_->colours->uploadToBuffer(commands);
        models_->recorded = true;
    }
    auto cmd = commands->vkCommandBuffer();
    for (auto key : pending_) {
        auto &node = sections_.at(key);
        if (node->recorded) continue;
        f.frameResourceRetainer().retain(node);
        if (!node->decodeModels->recorded) {
            node->decodeModels->records->uploadToBuffer(commands);
            node->decodeModels->colours->uploadToBuffer(commands);
            node->decodeModels->recorded = true;
        }
        node->quads->uploadToBuffer(commands);
        // Every descriptor table is immutable and retains the precise model buffer used to decode it.
        commands->barriersMemory(
            {vk::CommandBuffer::MemoryBarrier{.srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                              .srcAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
                                              .dstStageMask = VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                                              .dstAccessMask = VK_ACCESS_2_SHADER_STORAGE_READ_BIT}});
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline_->vkPipeline());
        auto layout = node->table->vkPipelineLayout();
        const auto &set = node->table->descriptorSet().at(0);
        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, layout, 0, 1, &set, 0, nullptr);
        const uint32_t push[]{node->count, node->level};
        vkCmdPushConstants(cmd, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(push), push);
        vkCmdDispatch(cmd, (node->count + 63) / 64, 1, 1);
        commands->barriersMemory({vk::CommandBuffer::MemoryBarrier{
            .srcStageMask = VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
            .srcAccessMask = VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT,
            .dstStageMask = VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                            VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
            .dstAccessMask = VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR | VK_ACCESS_2_SHADER_STORAGE_READ_BIT}});
        node->builder->submit(commands);
        f.frameResourceRetainer().retain(node->builder);
        f.frameResourceRetainer().retain(node->table);
        f.frameResourceRetainer().retain(node->decodeModels);
        node->builder.reset();
        node->table.reset();
        node->decodeModels.reset();
        node->recorded = true;
        ++built_;
        ++farRevision_;
    }
    pending_.clear();
    commands->barriersMemory({vk::CommandBuffer::MemoryBarrier{
        .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
        .srcAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
        .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
        .dstAccessMask = VK_ACCESS_2_SHADER_STORAGE_READ_BIT}});
}
std::vector<std::shared_ptr<VoxySection>> VoxySections::selected() {
    std::lock_guard lock(mutex_);
    std::vector<std::shared_ptr<VoxySection>> result;
    for (auto key : selected_) {
        const auto &node = sections_.at(key);
        if (node->recorded && node->count) result.push_back(node);
    }
    return result;
}
std::shared_ptr<VoxyModels> VoxySections::modelSnapshot() {
    std::lock_guard lock(mutex_);
    return models_;
}
std::array<uint64_t, 5> VoxySections::counters(uint64_t epoch) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return {};
    return {sections_.size(), selected_.size(), models_ ? models_->count : 0, bytes_, built_};
}
} // namespace mcvr
