#include "lod_scene.hpp"
#include "render_framework.hpp"
#include "renderer.hpp"
#include "textures.hpp"
#include "chunks.hpp"
#include "lod_layout.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include <cstring>
#include <limits>
#include <bit>
#include <cmath>

namespace mcvr {
namespace {
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

void LodScene::begin(uint64_t epoch) {
    std::lock_guard lock(mutex_);
    if (epoch == 0) throw std::invalid_argument("LOD epoch is zero");
    clear();
    epoch_ = epoch;
}
void LodScene::end(uint64_t epoch) {
    std::lock_guard lock(mutex_);
    if (epoch == epoch_) clear();
}
void LodScene::clear(bool releaseNear) {
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

void LodScene::nearEmpty(uint32_t slot, glm::ivec3 origin) {
    std::lock_guard lock(mutex_);
    auto old = emptyNear_.find(slot);
    if (old != emptyNear_.end() && old->second == origin) return;
    emptyNear_[slot] = origin;
    ++emptyRevision_;
}

std::shared_ptr<LodFrameTables> LodScene::tables(Chunks &chunks, const std::shared_ptr<vk::CommandBuffer> &commands) {
    // Caller owns Chunks::mutex; there is no far -> chunk lock acquisition.
    std::lock_guard lock(mutex_);
    if (!models_) return {};
    auto scene = chunks.staticScene();
    if (tables_ && lod::canReuseCoverage(tables_->nearScene == scene, tables_->emptyRevision == emptyRevision_,
                                         tables_->farRevision == farRevision_,
                                         tables_->handoffNear && tables_->handoffNear->instanceCount != 0)) {
        if (profile::enabled()) {
            profile::emit(profile::frame, 5, "lod.coverage.reuse", 1, 0);
            profile::emit(profile::frame, 5, "lod.coverage.far-version-diff", tables_->farRevision != farRevision_, 0);
        }
        if (tables_->farRevision == farRevision_ && tables_->models == models_) return tables_;
        auto result = std::make_shared<LodFrameTables>(*tables_);
        result->models = models_;
        result->farRevision = farRevision_;
        finishTables(result, commands);
        tables_ = result;
        return result;
    }
    profile::Phases timing("lod.coverage.coordinates");
    auto result = std::make_shared<LodFrameTables>();
    result->models = models_;
    result->nearScene = scene;
    result->emptyRevision = emptyRevision_;
    result->farRevision = farRevision_;
    std::set<std::array<int32_t, 3>> coordinates;
    scene->eachInstance([&](const auto &page, size_t i) {
        const auto &instance = page.instances[i];
        coordinates.insert({instance.origin.x / 16, instance.origin.y / 16, instance.origin.z / 16});
    });
    for (auto &[slot, origin] : emptyNear_) {
        if (slot >= chunks.primaryChunkCount()) continue;
        const auto &current = chunks.chunks()[slot];
        if (!current || current->blas || current->x != origin.x || current->y != origin.y || current->z != origin.z)
            continue;
        coordinates.insert({origin.x / 16, origin.y / 16, origin.z / 16});
    }
    // Sources may coarsen covered regions, but must not mistake retained handoff
    // geometry for current near ownership and indefinitely postpone its replacement.
    if (tables_ && tables_->nearScene == scene && tables_->emptyRevision == emptyRevision_) {
        result->primaryCoverage = tables_->primaryCoverage;
        result->coverageRevision = tables_->coverageRevision;
    } else {
        std::vector<int32_t> rows;
        for (const auto &c : coordinates) {
            const size_t size = rows.size();
            if (size && rows[size - 4] == c[0] && rows[size - 3] == c[1] && int64_t(rows[size - 1]) + 1 == c[2])
                rows.back() = c[2];
            else
                rows.insert(rows.end(), {c[0], c[1], c[2], c[2]});
        }
        if (tables_ && *tables_->primaryCoverage == rows) {
            result->primaryCoverage = tables_->primaryCoverage;
            result->coverageRevision = tables_->coverageRevision;
        } else {
            result->primaryCoverage = std::make_shared<const std::vector<int32_t>>(std::move(rows));
            result->coverageRevision = ++coverageRevision_;
        }
    }
    auto keyAt = [](uint32_t level, glm::ivec3 origin) {
        return lod::NodeKey{level, origin.x >> (5 + level), origin.y >> (5 + level), origin.z >> (5 + level)}.encode();
    };
    result->handoffNear = std::make_shared<StaticChunkScene>();
    auto handoffPage = std::make_shared<StaticChunkPage>();
    uint64_t handoffBytes = 0;
    std::set<const void *> handoffBuffers;
    auto keepMissing = [&](const std::shared_ptr<const StaticChunkScene> &old) {
        if (!old) return;
        old->eachInstance([&](const auto &page, size_t i) {
            const auto &instance = page.instances[i];
            const auto origin = instance.origin;
            const std::array<int32_t, 3> cell{origin.x / 16, origin.y / 16, origin.z / 16};
            if (coordinates.contains(cell) || !requestedRoots_.contains(keyAt(4, origin))) return;
            bool replacement = false;
            for (uint32_t level = 0; level <= 4; ++level) {
                const auto key = keyAt(level, origin);
                if (selected_.contains(key) && sections_.at(key)->recorded) {
                    replacement = true;
                    break;
                }
            }
            if (replacement) return;
            for (const auto &[identity, bytes] : page.owners[i]->memoryFootprint)
                if (identity && handoffBuffers.insert(identity).second) handoffBytes += bytes;
            if (handoffBytes > 128ull * 1024 * 1024 || handoffPage->instances.size() >= 2048)
                throw std::length_error("LOD near/far handoff budget exceeded; no valid owner was overwritten");
            handoffPage->add(instance.slot, origin, instance.blas, page.owners[i],
                             page.packedInstances[i].accelerationStructureReference);
            coordinates.insert(cell);
        });
    };
    if (tables_) {
        keepMissing(tables_->nearScene);
        keepMissing(tables_->handoffNear);
    }
    if (!handoffPage->instances.empty()) result->handoffNear->add(std::move(handoffPage));
    size_t capacity = 1;
    while (capacity < coordinates.size() * 2) capacity *= 2;
    if (capacity > 1u << 20) throw std::length_error("LOD near coverage budget");
    std::vector<glm::ivec4> cells(capacity, glm::ivec4(0));
    for (const auto &c : coordinates) {
        size_t index = lod::coverageHash({c[0], c[1], c[2]}) & (capacity - 1);
        while (cells[index].w) index = (index + 1) & (capacity - 1);
        cells[index] = {c[0], c[1], c[2], 1};
    }
    timing.next("lod.coverage.upload");
    if (tables_ && *tables_->cpuCoverage == cells) {
        result->coverage = tables_->coverage;
        result->coverageMask = tables_->coverageMask;
        result->cpuCoverage = tables_->cpuCoverage;
    } else {
        if (profile::enabled())
            profile::emit(profile::frame, 5, "lod.coverage.rebuild", 1, cells.size() * sizeof(cells[0]));
        result->coverage = upload(framework_, cells.data(), cells.size() * sizeof(cells[0]));
        result->coverageMask = uint32_t(capacity - 1);
        result->coverage->uploadToBuffer(commands);
        result->cpuCoverage = std::make_shared<const std::vector<glm::ivec4>>(std::move(cells));
    }
    finishTables(result, commands);
    tables_ = result;
    return result;
}

void LodScene::finishTables(const std::shared_ptr<LodFrameTables> &result,
                            const std::shared_ptr<vk::CommandBuffer> &commands) {
    result->sections.clear();
    result->coveredSections = 0;
    for (auto key : selected_) {
        const auto &node = sections_.at(key);
        if (!node->recorded || !node->count) continue;
        if (lod::fullyCovered(*result->cpuCoverage, node->origin, node->level)) {
            ++result->coveredSections;
            continue;
        }
        result->sections.push_back(node);
    }
    if (profile::enabled()) {
        profile::emit(profile::frame, 5, "lod.cut.active", result->sections.size(), 0);
        profile::emit(profile::frame, 5, "lod.cut.covered", result->coveredSections, 0);
    }
    if (tables_ && tables_->models == result->models && tables_->farRevision == result->farRevision &&
        tables_->cpuCoverage == result->cpuCoverage) {
        result->lightGroups = tables_->lightGroups;
        result->lightIndices = tables_->lightIndices;
        result->lightOwners = tables_->lightOwners;
        result->lightGroupCount = tables_->lightGroupCount;
        result->lightCount = tables_->lightCount;
    } else {
        struct Group {
            uint64_t quads;
            uint32_t first, count;
            glm::ivec4 originLevel;
        };
        static_assert(sizeof(Group) == 32);
        std::vector<Group> groups;
        std::vector<uint32_t> indices;
        result->lightOwners.clear();
        for (const auto &node : result->sections) {
            uint32_t first = uint32_t(indices.size());
            for (uint32_t i = 0; i < node->count; ++i)
                if (std::bit_cast<float>(models_->cpu[node->materialIds[i] * lod::modelWords + 51]) > 0)
                    indices.push_back(i);
            if (indices.size() == first) continue;
            groups.push_back({node->quads->bufferAddress(), first, uint32_t(indices.size()) - first,
                              glm::ivec4(node->origin, int(node->level))});
            result->lightOwners.push_back(node);
        }
        result->lightGroupCount = uint32_t(groups.size());
        result->lightCount = uint32_t(indices.size());
        result->lightGroups = upload(framework_, groups.data(), groups.size() * sizeof(Group));
        result->lightIndices = upload(framework_, indices.data(), indices.size() * sizeof(uint32_t));
        result->lightGroups->uploadToBuffer(commands);
        result->lightIndices->uploadToBuffer(commands);
    }
    struct Header {
        uint64_t models, colours, coverage;
        uint32_t modelCount, colourCount, coverageMask, padding;
        uint64_t lightGroups, lightIndices;
        uint32_t lightGroupCount, lightCount;
    };
    static_assert(sizeof(Header) == 64);
    const Header header{models_->records->bufferAddress(),
                        models_->colours->bufferAddress(),
                        result->coverage->bufferAddress(),
                        models_->count,
                        models_->colourCount,
                        result->coverageMask,
                        0,
                        result->lightGroups->bufferAddress(),
                        result->lightIndices->bufferAddress(),
                        result->lightGroupCount,
                        result->lightCount};
    result->header = upload(framework_, &header, sizeof(header));
    result->header->uploadToBuffer(commands);
    commands->barriersMemory(
        {vk::CommandBuffer::MemoryBarrier{.srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                          .srcAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
                                          .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                                          .dstAccessMask = VK_ACCESS_2_SHADER_STORAGE_READ_BIT}});
    framework_.frameResourceRetainer().retain(result);
}
void LodScene::models(uint64_t epoch, std::span<const uint32_t> records, std::span<const uint32_t> colours) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return;
    if (records.empty() || records.size() % lod::modelWords || records.size() > 65536 * lod::modelWords ||
        colours.size() > 8 * 1024 * 1024)
        throw std::invalid_argument("LOD model table ABI/budget");
    auto model = std::make_shared<LodMaterials>();
    model->count = uint32_t(records.size() / lod::modelWords);
    model->colourCount = uint32_t(colours.size());
    model->cpu.assign(records.begin(), records.end());
    std::set<uint32_t> pages;
    for (size_t i = 0; i < records.size(); i += lod::modelWords) {
        if (!records[i + 48]) continue;
        if (records[i + 48] >= 4096 || records[i + 52] > 2 || records[i + 53] > 3)
            throw std::invalid_argument("LOD material range");
        const auto alpha = (records[i + 50] >> mcvr::abi::LOD_ALPHA_SHIFT) & mcvr::abi::LOD_ALPHA_MASK;
        const float emission = std::bit_cast<float>(records[i + 51]);
        if ((alpha != mcvr::abi::ALPHA_OPAQUE && alpha != mcvr::abi::ALPHA_TRANSMISSION &&
             alpha != mcvr::abi::ALPHA_CUTOUT_LOW && alpha != mcvr::abi::ALPHA_COVERAGE) ||
            !std::isfinite(emission) || emission < 0 || emission > 1)
            throw std::invalid_argument("LOD source alpha/emission range");
        pages.insert(records[i + 48]);
        if ((records[i + 50] & 1) && records[i + 49] >= colours.size()) throw std::invalid_argument("LOD tint index");
    }
    auto textures = Renderer::instance().textures();
    std::lock_guard texturesLock(textures->mtx_);
    for (uint32_t page : pages) {
        auto image = textures->texture(page);
        auto sampler = textures->sampler(page);
        if (!image || !sampler) throw std::invalid_argument("LOD model texture has no owned image");
        model->images.push_back(image);
        model->samplers.push_back(sampler);
    }
    model->records = upload(framework_, records.data(), records.size_bytes());
    model->colours = upload(framework_, colours.data(), colours.size_bytes());
    models_ = std::move(model);
}

bool LodScene::section(uint64_t epoch, uint64_t key, uint64_t revision, std::span<const lod::Quad> quads) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return false;
    const auto coordinates = lod::NodeKey::decode(key);
    const auto level = coordinates.level;
    if (quads.size() > 8 * 65536 || revision == 0) throw std::invalid_argument("LOD section ABI/budget");
    const auto old = sections_.find(key);
    if (old != sections_.end() && old->second->revision >= revision) return true;
    const uint64_t bytes = quads.size() * (sizeof(lod::Quad) + 64 + 24);
    const auto oldBytes = old == sections_.end() ? 0 : old->second->bytes;
    if (old == sections_.end() && sections_.size() >= 8192) return false;
    if (!quads.empty() && !models_) throw std::logic_error("LOD section before model publication");
    for (const auto &q : quads) {
        const auto id = q.material(), face = q.face();
        if (face > 5 || id >= models_->count || !models_->cpu[id * lod::modelWords + 48] ||
            !(models_->cpu[id * lod::modelWords + face * 8 + 7] & 1))
            throw std::invalid_argument("LOD rectangle references an unpublished material/face");
        if ((models_->cpu[id * lod::modelWords + 50] & 1) &&
            uint64_t(models_->cpu[id * lod::modelWords + 49]) + q.tint >= models_->colourCount)
            throw std::invalid_argument("LOD rectangle tint range");
        if ((q.extent & 65535) == 0 || (q.extent >> 16) == 0 || (q.position >> 30) || (q.materialFace >> 27))
            throw std::invalid_argument("LOD rectangle extent/flags");
    }
    auto node = std::make_shared<LodSection>();
    node->key = key;
    node->epoch = epoch_;
    node->revision = revision;
    node->level = level;
    node->count = uint32_t(quads.size());
    node->bytes = bytes;
    const int x = coordinates.x, y = coordinates.y, z = coordinates.z;
    const int64_t scale = 32ll << level;
    for (const auto coordinate : {x, y, z})
        if (coordinate * scale < INT32_MIN || coordinate * scale > INT32_MAX)
            throw std::invalid_argument("LOD section exceeds world coordinate range");
    node->origin = {int(x * scale), int(y * scale), int(z * scale)};
    if (!quads.empty()) {
        auto &f = framework_;
        node->quads = upload(f, quads.data(), quads.size_bytes());
        node->materialIds.reserve(quads.size());
        for (const auto &q : quads) node->materialIds.push_back(uint16_t(q.material()));
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
    } else
        node->recorded = true;
    bytes_ = bytes_ - oldBytes + node->bytes;
    sections_[key] = std::move(node);
    if (selected_.contains(key)) ++farRevision_;
    if (!quads.empty())
        pending_.insert(key);
    else
        pending_.erase(key);
    return true;
}

void LodScene::select(uint64_t epoch, std::span<const uint64_t> keys, lod::RootCoverage roots) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return;
    lod::validateCut(keys);
    std::set<uint64_t> selection(keys.begin(), keys.end());
    for (auto key : selection)
        if (!sections_.contains(key)) throw std::invalid_argument("LOD selected section has no owned version");
    selected_ = std::move(selection);
    requestedRoots_ = std::move(roots);
    ++farRevision_;
}

void LodScene::retain(uint64_t epoch, std::span<const uint64_t> keys) {
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
        const bool selected = selected_.erase(it->first) != 0;
        pending_.erase(it->first);
        it = sections_.erase(it);
        if (selected) ++farRevision_;
    }
}

void LodScene::record(const std::shared_ptr<vk::CommandBuffer> &commands) {
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
        if (selected_.contains(key)) ++farRevision_;
    }
    pending_.clear();
    commands->barriersMemory({vk::CommandBuffer::MemoryBarrier{
        .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
        .srcAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
        .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
        .dstAccessMask = VK_ACCESS_2_SHADER_STORAGE_READ_BIT}});
}
std::shared_ptr<LodMaterials> LodScene::modelSnapshot() {
    std::lock_guard lock(mutex_);
    return models_;
}
std::array<uint64_t, 10> LodScene::counters(uint64_t epoch) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_) return {};
    return {sections_.size(),
            selected_.size(),
            models_ ? models_->count : 0,
            bytes_,
            built_,
            tables_ ? tables_->lightCount : 0,
            tables_ ? tables_->lightGroupCount : 0,
            tables_ ? tables_->sections.size() : 0,
            tables_ ? tables_->coveredSections : 0,
            tables_ ? tables_->coverageRevision : 0};
}

std::vector<int64_t> LodScene::coverage(uint64_t epoch, uint64_t previous) {
    std::lock_guard lock(mutex_);
    if (epoch != epoch_ || !tables_ || tables_->coverageRevision == previous) return {};
    std::vector<int64_t> result;
    result.reserve(tables_->primaryCoverage->size() + 1);
    result.push_back(int64_t(tables_->coverageRevision));
    result.insert(result.end(), tables_->primaryCoverage->begin(), tables_->primaryCoverage->end());
    return result;
}
} // namespace mcvr
