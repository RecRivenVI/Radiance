#include "core/render/draw_colour.hpp"
#include "core/render/source_emission.hpp"
#include "core/render/emissive_eye_composition.hpp"
#include "core/render/entity_gpu_conversion.hpp"
#include "core/render/scene_scope.hpp"
#include <cstdlib>
#include <unordered_set>
#include <glm/gtc/type_ptr.hpp>
#include "core/diagnostics/frame_profile.hpp"
#include "core/render/material_faces.hpp"
#include "core/render/water_clip.hpp"
#include "core/render/geometry_topology.hpp"
#include "common/final_position.hpp"
#include "core/diagnostics/geometry_audit.hpp"
#include "core/render/entities.hpp"
#include "core/render/line_geometry.hpp"

#include "core/logging.hpp"

#include "core/render/buffers.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/world_mesh_contract.hpp"
#include "common/pbr_source.hpp"
#include "core/vulkan/vertex.hpp"
#include "core/render/renderer.hpp"

#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstring>
#include <iostream>
#include <limits>
#include <mutex>
#include <optional>
#include <set>
#include <unordered_map>

using Vertex = glm::vec3;
using Triangle = std::array<Vertex, 3>;
using VertexIdentifier = std::array<uint32_t, 2>;
using TriangleIdentifier = std::array<VertexIdentifier, 3>;

namespace {
// Only final-position twins leave the deferred conversion path. Ordinary geometry
// retains its GPU input/ownership path; this scan does not regenerate material data.
bool rawHasTwinQuads(const EntitiesBuildTask &task,
                     size_t geometry,
                     int owner,
                     const std::string &source,
                     double x,
                     double y,
                     double z) {
    const bool quads = task.indexFormats[geometry] == static_cast<int>(World::DrawMode::QUADS);
    const uint32_t count = task.vertexCounts[geometry];
    const bool compact = task.vertexFormats[geometry] == World::PBR_COMPACT_V1;
    const uint32_t stride = compact ? mcvr::abi::PBR_COMPACT_BYTES / 4 : mcvr::abi::PBR_CANONICAL_BYTES / 4;
    const auto *bytes = static_cast<const std::byte *>(task.vertices[geometry]);
    if (count != 0 && bytes == nullptr) throw std::invalid_argument("Deferred topology source is null");
    if (task.directEntityInput)
        mcvr::checkedEntitySourceBytes(count, stride * 4,
                                       static_cast<uint64_t>(task.geometryVertexByteCounts[geometry]));
    const auto read = [&](size_t word) {
        uint32_t value;
        std::memcpy(&value, bytes + word * 4, 4);
        return value;
    };
    const auto f = [&](size_t word) { return std::bit_cast<float>(read(word)); };
    const auto point = [&](size_t v) -> mcvr::topology::Point {
        const size_t at = v * stride;
        glm::vec3 pos{f(at), f(at + 1), f(at + 2)};
        const uint32_t normalMode = compact ? (read(at + 24) & 1u) : read(at + 3);
        if (task.normalOffset && normalMode) {
            const size_t n = at + (compact ? 3 : 4);
            pos = mcvr::geometry::finalPosition(pos, glm::vec3(f(n), f(n + 1), f(n + 2)), true);
        }
        return {pos.x, pos.y, pos.z};
    };
    const bool twins = quads ? mcvr::topology::hasReversePolygonPair<4>(count, point) :
                               mcvr::topology::hasReversePolygonPair<3>(count, point);
    if (!mcvr::geometry::isolatedAudit() || twins) return twins;
    std::vector<mcvr::topology::Point> positions;
    positions.reserve(count);
    for (size_t v = 0; v < count; ++v) positions.push_back(point(v));
    std::vector<uint32_t> indices;
    if (quads)
        indices = mcvr::topology::sequentialQuads(count);
    else {
        indices.resize(count);
        std::iota(indices.begin(), indices.end(), 0u);
    }
    const auto result = mcvr::topology::reconcile(positions, indices, quads, false);
    if (result.report.nonfinite) throw std::invalid_argument("Nonfinite deferred quad position");
    if (!twins && mcvr::geometry::isolatedAudit()) {
        // CPU reference; the real deferred GPU path stays enabled.
        std::vector<vk::VertexFormat::PBRVertex> reference(count);
        for (size_t v = 0; v < count; ++v) {
            if (compact)
                reference[v] = mcvr::decodeCompactPbr({bytes + v * 100, 100});
            else
                std::memcpy(&reference[v], bytes + v * 128, 128);
            reference[v].pos = {positions[v][0], positions[v][1], positions[v][2]};
        }
        mcvr::audit::geometry(source, owner, geometry, reference, indices, {},
                              mcvr::surface::sourceFaces(static_cast<uint32_t>(task.geometryTypes[geometry])),
                              result.report, "gpu-deferred-cpu-reference", x, y, z, task.frameToken);
    }
    return twins;
}

// CPU reference remains available for controlled comparisons (restart required).
bool gpuEntityConversionEnabled() {
    static const bool enabled = [] {
        const auto *v = std::getenv("MCVR_ENTITY_GPU_CONVERSION");
        return !v || std::string_view(v) != "0";
    }();
    return enabled && !SceneRecordingScope::active();
}


void logInvalidLineWidthOnce(const std::string &contentName, World::DrawMode drawMode, double lineWidth) {
    static std::mutex mutex;
    static std::set<std::string> loggedKeys;

    const std::string key = contentName + "|" + std::to_string(static_cast<int>(drawMode));
    std::lock_guard<std::mutex> lock(mutex);
    if (!loggedKeys.insert(key).second) { return; }

    mcvr::log::warn("Entities") << "Skipping line geometry with invalid width " << lineWidth
                                << " (content=" << contentName << ", drawMode=" << static_cast<int>(drawMode) << ")"
                                << std::endl;
}

std::vector<uint32_t>
decodeDrawIndices(const EntitiesBuildTask &task, uint32_t geometryIndex, uint32_t vertexCount, World::DrawMode mode) {
    const int indexCount = task.geometryIndexCounts[geometryIndex];
    if (indexCount < 0) { throw std::runtime_error("Negative world mesh index count"); }
    std::vector<uint32_t> drawIndices(static_cast<size_t>(indexCount));
    if (task.geometryIndices[geometryIndex] == nullptr) {
        drawIndices.clear();
        if (mode == World::DrawMode::QUADS) {
            if (vertexCount % 4 != 0) { throw std::runtime_error("World quad vertex count is not divisible by four"); }
            drawIndices.reserve(vertexCount / 4 * 6);
            for (uint32_t i = 0; i < vertexCount; i += 4) {
                drawIndices.insert(drawIndices.end(), {i, i + 1, i + 2, i + 2, i + 3, i});
            }
        } else {
            drawIndices.reserve(vertexCount);
            for (uint32_t i = 0; i < vertexCount; ++i) { drawIndices.push_back(i); }
        }
    } else if (task.geometryIndexTypes[geometryIndex] == 0) {
        const auto *source = static_cast<const uint16_t *>(task.geometryIndices[geometryIndex]);
        for (int i = 0; i < indexCount; ++i) { drawIndices[i] = source[i]; }
    } else if (task.geometryIndexTypes[geometryIndex] == 1) {
        const auto *source = static_cast<const uint32_t *>(task.geometryIndices[geometryIndex]);
        std::copy(source, source + indexCount, drawIndices.begin());
    } else {
        throw std::runtime_error("Unsupported world mesh index element type");
    }
    for (uint32_t index : drawIndices) {
        if (index >= vertexCount) { throw std::runtime_error("World mesh index exceeds vertex count"); }
    }
    return drawIndices;
}

size_t worldVertexStride(int format) {
    switch (format) {
        case World::POSITION_COLOR_TEXTURE_LIGHT_NORMAL: return sizeof(vk::VertexFormat::PositionColorTexLightNormal);
        case World::POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL:
            return sizeof(vk::VertexFormat::PositionColorTexOverlayLightNormal);
        case World::POSITION_TEXTURE_COLOR_LIGHT: return sizeof(vk::VertexFormat::PositionTexColorLight);
        case World::POSITION: return sizeof(vk::VertexFormat::PositionOnly);
        case World::POSITION_COLOR: return sizeof(vk::VertexFormat::PositionColor);
        case World::LINES: return sizeof(vk::VertexFormat::PositionColorNormal);
        case World::CAPTURED_LINES: return sizeof(mcvr::lines::SourceVertex);
        case World::POSITION_COLOR_LIGHT: return sizeof(vk::VertexFormat::PositionColorLight);
        case World::POSITION_TEXTURE: return sizeof(vk::VertexFormat::PositionTex);
        case World::POSITION_TEXTURE_COLOR: return sizeof(vk::VertexFormat::PositionTexColor);
        case World::POSITION_COLOR_TEXTURE_LIGHT: return sizeof(vk::VertexFormat::PositionColorTexLight);
        case World::POSITION_TEXTURE_LIGHT_COLOR: return sizeof(vk::VertexFormat::PositionTexLightColor);
        case World::POSITION_TEXTURE_COLOR_NORMAL: return sizeof(vk::VertexFormat::PositionTexColorNormal);
        case World::PBR_TRIANGLE:
        case World::PBR_COMPACT_V1: return mcvr::pbrSourceStride(format);
        default: throw std::runtime_error("Unsupported world mesh vertex format");
    }
}

void validateEntitySourceFormat(const EntitiesBuildTask &task) {
    mcvr::validateEntityInputVersion(task.formatAbiVersion, task.directEntityInput);
    if (task.formatAbiVersion == 0) { return; }
    if (task.entityCount < 0) throw std::invalid_argument("Negative entity source count");
    if (task.entityCount && task.entityGeometryCounts == nullptr)
        throw std::invalid_argument("Entity source geometry counts are missing");

    size_t geometryTotal = 0;
    for (int entity = 0; entity < task.entityCount; ++entity) {
        const int count = task.entityGeometryCounts[entity];
        if (count < 0 || static_cast<size_t>(count) > std::numeric_limits<size_t>::max() - geometryTotal)
            throw std::length_error("Entity source geometry count overflow");
        geometryTotal += static_cast<size_t>(count);
    }
    if (geometryTotal > UINT32_MAX) throw std::length_error("Entity source geometry index overflow");
    if (geometryTotal && (task.geometryVertexByteCounts == nullptr || task.vertices == nullptr ||
                          task.vertexCounts == nullptr || task.vertexFormats == nullptr))
        throw std::invalid_argument("Entity source byte metadata is missing");

    size_t geometry = 0;
    for (int entity = 0; entity < task.entityCount; ++entity) {
        for (int layer = 0; layer < task.entityGeometryCounts[entity]; ++layer, ++geometry) {
            const int count = task.vertexCounts[geometry];
            if (count < 0) throw std::invalid_argument("Negative entity source vertex count");
            const size_t stride = worldVertexStride(task.vertexFormats[geometry]);
            const int64_t declaredBytes = task.geometryVertexByteCounts[geometry];
            if (declaredBytes < 0 || static_cast<uint64_t>(declaredBytes) > std::numeric_limits<size_t>::max())
                throw std::length_error("Entity source byte length is outside the supported range");
            const size_t required = mcvr::checkedEntitySourceBytes(static_cast<uint64_t>(count), stride,
                                                                   static_cast<uint64_t>(declaredBytes));
            mcvr::validateEntitySourceAddress(task.vertices[geometry], required);
        }
    }
}

void composeEmissiveEyeOverlays(std::vector<uint32_t> &geometryMaterialFlags,
                                std::vector<uint32_t> &geometryEmissionPolicies,
                                std::vector<glm::vec4> &geometryEyeColours,
                                std::vector<glm::vec4> &geometryDrawColours,
                                std::vector<bool> &authoredQuads,
                                std::vector<World::GeometryTypes> &geometryTypes,
                                std::vector<std::string> &geometryGroupNames,
                                std::vector<std::string> &geometryContentNames,
                                std::vector<std::vector<vk::VertexFormat::PBRVertex>> &vertices,
                                std::vector<std::vector<uint32_t>> &indices,
                                std::vector<uint32_t> &emissiveOverlayTextureIDs) {
    const auto attachments =
        mcvr::surface::eyeAttachments(geometryMaterialFlags, geometryEmissionPolicies, vertices, indices);
    mcvr::surface::applyEyeAttachments(attachments, geometryDrawColours, geometryEyeColours, emissiveOverlayTextureIDs,
                                       glm::vec4(1.0f), geometryMaterialFlags, geometryEmissionPolicies, authoredQuads,
                                       geometryTypes, geometryGroupNames, geometryContentNames, vertices, indices);
}

} // namespace

struct TriangleHash {
    static inline void hash_combine(std::size_t &seed, std::size_t h) noexcept {
        seed ^= h + 0x9e3779b97f4a7c15ull + (seed << 6) + (seed >> 2);
    }

    std::size_t operator()(Triangle const &k) const noexcept {
        std::size_t seed = 0;
        for (auto const &v : k) { hash_combine(seed, std::hash<glm::vec3>{}(v)); }
        return seed;
    }
};


EntityBuildData::EntityBuildData(int hashCode,
                                 double x,
                                 double y,
                                 double z,
                                 int rayTracingFlag,
                                 int postRenderFlag,
                                 int prebuiltBLAS,
                                 World::Coordinates coordinate,
                                 uint32_t geometryCount,
                                 std::vector<World::GeometryTypes> &&geometryTypes,
                                 std::vector<std::string> &&geometryGroupNames,
                                 std::vector<std::string> &&geometryContentNames,
                                 std::vector<std::vector<vk::VertexFormat::PBRVertex>> &&vertices,
                                 std::vector<std::vector<uint32_t>> &&indices,
                                 std::vector<uint32_t> &&emissiveOverlayTextureIDs,
                                 uint64_t worldToken,
                                 uint64_t frameToken,
                                 uint64_t resourceGeneration,
                                 uint64_t stageToken,
                                 int worldStage,
                                 std::vector<std::string> &&shaderKeys,
                                 std::vector<std::string> &&materialKeys)
    : geometryMaterialFlags(geometryCount, 0),
      hashCode(hashCode),
      x(x),
      y(y),
      z(z),
      rayTracingFlag(rayTracingFlag),
      postRenderFlag(postRenderFlag),
      prebuiltBLAS(prebuiltBLAS),
      coordinate(coordinate),
      geometryCount(geometryCount),
      geometryTypes(std::move(geometryTypes)),
      geometryGroupNames(std::move(geometryGroupNames)),
      geometryContentNames(std::move(geometryContentNames)),
      vertices(std::move(vertices)),
      indices(std::move(indices)),
      emissiveOverlayTextureIDs(std::move(emissiveOverlayTextureIDs)),
      worldToken(worldToken),
      frameToken(frameToken),
      resourceGeneration(resourceGeneration),
      stageToken(stageToken),
      worldStage(worldStage),
      shaderKeys(std::move(shaderKeys)),
      materialKeys(std::move(materialKeys)),
      indexBufferAddresses(),
      positionBufferAddresses(),
      materialBufferAddresses() {}

void EntityBuildDataBatch::addData(std::shared_ptr<EntityBuildData> data) {
    datas.push_back(data);
}

void EntityBuildDataBatch::build(std::shared_ptr<vk::ComputePipeline> *conversionPipeline,
                                 mcvr::CpuPreparationBatch *cpuPreparation) {
    mcvr::profile::Scope auditProfile("entity-batch-build-record");
    auto clearBatch = [this]() {
        datas.clear();
        indexBuffer = nullptr;
        positionBuffer = nullptr;
        materialBuffer = nullptr;
        blasBatchBuilder = nullptr;
        gpuConversion = nullptr;
    };

    if (datas.empty()) {
        clearBatch();
        return;
    }

    auto framework = Renderer::instance().framework();
    auto vma = framework->vma();
    auto device = framework->device();
    auto physicalDevice = framework->physicalDevice();

    mcvr::profile::Phases preparation("entity.layout");
    mcvr::profile::Accumulated topologyProfile("entity.layout.topology"), geometryAuditProfile("entity.layout.audit"),
        layerComposeProfile("entity.layout.layers-compose"), layerEraseProfile("entity.layout.layers-erase"),
        layerUploadProfile("entity.layout.layers-upload");
    uint64_t preparedVertices = 0, preparedTriangles = 0, layerOwners = 0, attachedTriangles = 0;
    std::vector<uint32_t> instanceOffsets;
    std::vector<mcvr::geometry::PreparedTopology> cpuResults;
    if (cpuPreparation) {
        mcvr::profile::Scope dispatchProfile("entity.layout.topology-dispatch");
        struct Work {
            EntityBuildData *data;
            size_t geometry;
        };
        std::vector<Work> work;
        std::unordered_set<EntityBuildData *> owners;
        bool independent = true;
        size_t vertexCount = 0;
        for (const auto &data : datas) {
            if (data->topologyPrepared) continue;
            if (!owners.insert(data.get()).second) {
                independent = false;
                break;
            }
            for (size_t g = 0; g < data->geometryCount; ++g) {
                if (!data->rawGeometry.empty() && data->rawGeometry[g].present()) continue;
                work.push_back({data.get(), g});
                vertexCount += data->vertices[g].size();
            }
        }
        if (independent && work.size() >= 8 && vertexCount >= 2048) {
            cpuResults.resize(work.size());
            const bool measured = mcvr::profile::enabled();
            std::vector<uint64_t> service(measured ? work.size() : 0);
            cpuPreparation->run(work.size(), [&](size_t i) {
                const auto begin = measured ? mcvr::profile::now() : 0;
                auto [data, g] = work[i];
                cpuResults[i] = mcvr::geometry::prepare(data->vertices[g], data->indices[g],
                                                        !data->authoredQuads.empty() && data->authoredQuads.at(g));
                if (measured) service[i] = mcvr::profile::now() - begin;
            });
            if (measured) {
                uint64_t busy = 0;
                for (auto value : service) busy += value;
                // Sum of task service intervals across threads, NOT main-thread
                // latency or active CPU time; never add this to the frame partition.
                mcvr::profile::emit(mcvr::profile::frame, 1, "entity.topology.task-service", busy, busy);
                mcvr::profile::emit(mcvr::profile::frame, 5, "entity.topology.parallel-jobs", work.size(),
                                    vertexCount * sizeof(vk::VertexFormat::PBRVertex));
                mcvr::profile::emit(mcvr::profile::frame, 5, "entity.topology.background-jobs",
                                    cpuPreparation->backgroundJobs(), 0);
            }
        }
    }
    size_t cpuResultIndex = 0;
    for (auto &data : datas) {
        if (data->topologyPrepared) continue;
        data->topologyVertexFlags.resize(data->geometryCount);
        for (size_t g = 0; g < data->geometryCount; ++g) {
            if (!data->rawGeometry.empty() && data->rawGeometry[g].present()) continue;
            auto result = [&] {
                mcvr::profile::Scope measured(topologyProfile);
                if (!cpuResults.empty()) return std::move(cpuResults.at(cpuResultIndex++));
                return mcvr::geometry::prepare(data->vertices[g], data->indices[g],
                                               !data->authoredQuads.empty() && data->authoredQuads.at(g));
            }();
            preparedVertices += data->vertices[g].size();
            preparedTriangles += data->indices[g].size() / 3;
            data->topologyVertexFlags[g] = std::move(result.vertexFlags);
            if (result.report.pairedQuads || result.report.pairedTriangles)
                data->geometryMaterialFlags[g] |= mcvr::geometry::pairedGeometryBit;
            {
                mcvr::profile::Scope measured(geometryAuditProfile);
                mcvr::audit::geometry(data->geometryContentNames[g] + "/" + data->geometryGroupNames[g], data->hashCode,
                                      g, data->vertices[g], data->indices[g], data->topologyVertexFlags[g],
                                      data->geometryMaterialFlags[g], result.report,
                                      data->surfaceLayerOwner ? "cpu-pre-layers" : "cpu-final", data->x, data->y,
                                      data->z, data->frameToken);
            }
        }
        if (data->surfaceLayerOwner) {
            ++layerOwners;
            mcvr::profile::Phases layersProfile(layerComposeProfile);
            auto layers = mcvr::surface::composeSurfaceLayers(
                data->geometryMaterialFlags, data->vertices, data->indices, data->geometryDrawColours,
                data->topologyVertexFlags, data->surfaceLayerQualification);
            attachedTriangles += layers.attachedTriangles;
            layersProfile.next(layerEraseProfile);
            mcvr::surface::eraseLayerGeometries(
                layers, data->geometryMaterialFlags, data->vertices, data->indices, data->geometryDrawColours,
                data->geometryEyeColours, data->geometryTypes, data->geometryGroupNames, data->geometryContentNames,
                data->authoredQuads, data->rawGeometry, data->shaderKeys, data->materialKeys,
                data->emissiveOverlayTextureIDs, data->surfaceLayerQualification, data->topologyVertexFlags,
                layers.primitives);
            data->geometryCount = static_cast<uint32_t>(data->vertices.size());
            data->surfaceLayoutKey = layers.topologyKey;
            data->surfaceLayers = std::move(layers.primitives);
            layersProfile.next(layerUploadProfile);
            if (layers.attachedTriangles) {
                size_t rangeCount = 0, recordCount = 0;
                for (const auto &geometry : data->surfaceLayers) {
                    rangeCount += geometry.size();
                    for (const auto &stack : geometry) recordCount += stack.size();
                }
                using Range = vk::VertexFormat::SurfaceLayerRange;
                using Record = vk::VertexFormat::SurfaceLayerTriangle;
                if (rangeCount > UINT32_MAX || recordCount > UINT32_MAX ||
                    recordCount > (std::numeric_limits<size_t>::max() - rangeCount * sizeof(Range)) / sizeof(Record))
                    throw std::length_error("Surface layer buffer exceeds supported size");
                const size_t rangeBytes = rangeCount * sizeof(Range), bytes = rangeBytes + recordCount * sizeof(Record);
                data->surfaceLayerBuffer = vk::DeviceLocalBuffer::create(vma, device, false, bytes,
                                                                         VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                                                                             VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
                const auto address = data->surfaceLayerBuffer->bufferAddress();
                data->surfaceLayerBuffer->writeToStagingBuffer([&](void *mapped, size_t mappedBytes) {
                    if (mappedBytes != bytes) throw std::length_error("Surface layer staging size mismatch");
                    auto *dst = static_cast<std::byte *>(mapped);
                    size_t rangeIndex = 0, recordIndex = 0;
                    for (const auto &geometry : data->surfaceLayers) {
                        bool hasLayers = std::any_of(geometry.begin(), geometry.end(),
                                                     [](const auto &stack) { return !stack.empty(); });
                        data->surfaceLayerAddresses.push_back(hasLayers ? address + rangeIndex * sizeof(Range) : 0);
                        for (const auto &stack : geometry) {
                            const auto recordsAddress = address + rangeBytes + recordIndex * sizeof(Record);
                            Range range{uint32_t(recordsAddress), uint32_t(recordsAddress >> 32),
                                        uint32_t(stack.size()), 0};
                            std::memcpy(dst + rangeIndex++ * sizeof(Range), &range, sizeof(range));
                            for (const auto &source : stack) {
                                Record record{};
                                for (size_t j = 0; j < 3; ++j)
                                    record.vertices[j] = vk::Vertex::makeMaterialVertex(source.vertices[j]);
                                record.faces = source.faces;
                                record.primitivePolicy = source.primitivePolicy;
                                record.sourceGeometry = source.sourceGeometry;
                                record.sourcePrimitive = source.sourcePrimitive;
                                std::memcpy(dst + rangeBytes + recordIndex++ * sizeof(Record), &record, sizeof(record));
                            }
                        }
                    }
                });
                Renderer::instance().buffers()->queueImportantWorldUpload(data->surfaceLayerBuffer);
            }
        }
        data->topologyPrepared = true;
    }
    if (mcvr::profile::enabled()) {
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.layout.prepared-vertices", preparedVertices, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.layout.prepared-triangles", preparedTriangles, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.layout.layer-owners", layerOwners, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.layout.attached-triangles", attachedTriangles, 0);
    }
    preparation.next("entity.layout.offsets");
    std::vector<uint32_t> geometryVertexOffsets;
    std::vector<uint32_t> geometryIndexOffsets;
    uint32_t totalGeometryCount = 0;
    uint32_t totalVertexCount = 0;
    uint32_t totalIndexCount = 0;

    for (auto data : datas) {
        instanceOffsets.push_back(totalGeometryCount);
        for (int i = 0; i < data->geometryCount; i++) {
            geometryVertexOffsets.push_back(totalVertexCount);
            geometryIndexOffsets.push_back(totalIndexCount);

            totalVertexCount += data->vertexCount(i);
            totalIndexCount += data->indexCount(i);
        }

        totalGeometryCount += data->geometryCount;
    }

    if (totalGeometryCount == 0 || totalVertexCount == 0 || totalIndexCount == 0) {
        clearBatch();
        return;
    }

    preparation.next("entity.device-buffer-allocate");
    positionBuffer = vk::DeviceLocalBuffer::create(
        vma, device, false, totalVertexCount * sizeof(vk::VertexFormat::PositionVertex),
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
            VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    materialBuffer =
        vk::DeviceLocalBuffer::create(vma, device, false, totalVertexCount * sizeof(vk::VertexFormat::MaterialVertex),
                                      VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    indexBuffer = vk::DeviceLocalBuffer::create(
        vma, device, false, totalIndexCount * sizeof(uint32_t),
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
            VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);

    if (conversionPipeline) {
        gpuConversion = EntityGpuConversion::create(*framework, datas, positionBuffer, materialBuffer, indexBuffer,
                                                    *conversionPipeline);
    } else {
        // Keep the source PBR data for overlay composition and other existing consumers, but
        // write the final streams directly to their owned staging allocations. No intermediate
        // packed vectors, extra copy, global idle, or change to upload/BLAS retirement.
        preparation.next("entity.staging-write");
        positionBuffer->writeToStagingBuffer([&](void *positionData, size_t positionBytes) {
            materialBuffer->writeToStagingBuffer([&](void *materialData, size_t materialBytes) {
                indexBuffer->writeToStagingBuffer([&](void *indexData, size_t indexBytes) {
                    auto positions = std::span(static_cast<vk::VertexFormat::PositionVertex *>(positionData),
                                               positionBytes / sizeof(vk::VertexFormat::PositionVertex));
                    auto materials = std::span(static_cast<vk::VertexFormat::MaterialVertex *>(materialData),
                                               materialBytes / sizeof(vk::VertexFormat::MaterialVertex));
                    auto packedIndices = std::span(static_cast<uint32_t *>(indexData), indexBytes / sizeof(uint32_t));
                    size_t vertexOffset = 0, indexOffset = 0;
                    preparation.next("entity.host-pack");
                    for (const auto &data : datas) {
                        mcvr::profile::Scope packing("entity-pack-vertices");
                        for (size_t i = 0; i < data->vertices.size(); ++i) {
                            const auto &vertices = data->vertices[i];
                            const auto &indices = data->indices[i];
                            if (vertices.size() > positions.size() - vertexOffset ||
                                vertices.size() > materials.size() - vertexOffset ||
                                indices.size() > packedIndices.size() - indexOffset)
                                throw std::out_of_range("Entity packed stream exceeds its allocated buffer");
                            vk::Vertex::writePackedVertices(vertices, data->emissiveOverlayTextureIDs[i],
                                                            positions.subspan(vertexOffset, vertices.size()),
                                                            materials.subspan(vertexOffset, vertices.size()));
                            if (!data->topologyVertexFlags[i].empty())
                                for (size_t v = 0; v < vertices.size(); ++v)
                                    positions[vertexOffset + v].pad0 = data->topologyVertexFlags[i][v];
                            std::memcpy(packedIndices.data() + indexOffset, indices.data(),
                                        indices.size() * sizeof(uint32_t));
                            vertexOffset += vertices.size();
                            indexOffset += indices.size();
                        }
                    }
                    preparation.next("entity.staging-flush");
                });
            });
        });
    }

    preparation.next("entity.blas-prepare");
    blasBatchBuilder = vk::BLASBatchBuilder::create();
    std::vector<uint32_t> nonPrebuildInstances;
    for (int instanceIndex = 0; auto data : datas) {
        auto instanceOffset = instanceOffsets[instanceIndex];
        const mcvr::faces::ModelRules faceRules(data->geometryMaterialFlags);
        std::shared_ptr<vk::BLASBuilder> blasBuilder = nullptr;
        std::shared_ptr<vk::BLASBuilder::BLASGeometryBuilder> blasGeometryBuilder = nullptr;
        if (data->prebuiltBLAS < 0) {
            nonPrebuildInstances.push_back(instanceIndex);
            blasBuilder = blasBatchBuilder->defineBLASBuilder();
            blasGeometryBuilder = blasBuilder->beginGeometries();
        }
        for (int i = 0; i < data->geometryCount; i++) {
            VkDeviceAddress indexBufferAddress =
                indexBuffer->bufferAddress() + geometryIndexOffsets[instanceOffset + i] * sizeof(uint32_t);
            VkDeviceAddress positionBufferAddress =
                positionBuffer->bufferAddress() +
                geometryVertexOffsets[instanceOffset + i] * sizeof(vk::VertexFormat::PositionVertex);
            VkDeviceAddress materialBufferAddress =
                materialBuffer->bufferAddress() +
                geometryVertexOffsets[instanceOffset + i] * sizeof(vk::VertexFormat::MaterialVertex);
            data->indexBufferAddresses.push_back(indexBufferAddress);
            data->positionBufferAddresses.push_back(positionBufferAddress);
            data->materialBufferAddresses.push_back(materialBufferAddress);
            if (data->prebuiltBLAS < 0) {
                blasGeometryBuilder->defineTriangleGeomrtry<vk::VertexFormat::PositionVertex>(
                    positionBufferAddress, data->vertexCount(i), indexBufferAddress, data->indexCount(i),
                    data->geometryTypes[i] == World::WORLD_SOLID &&
                        !faceRules.needsAnyHit(data->geometryMaterialFlags[i]));
            }
        }
        if (data->prebuiltBLAS < 0) {
            blasGeometryBuilder->endGeometries();
            blasBuilder->defineBuildProperty(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                ->querySizeInfo(device);
        }

        instanceIndex++;
    }

    preparation.next("entity.blas-allocate");
    auto blass = blasBatchBuilder->allocateBuffers(physicalDevice, device, vma)->build(device);
    for (int i = 0; i < nonPrebuildInstances.size(); i++) { datas[nonPrebuildInstances[i]]->blas = blass[i]; }
}

void EntityPostBuildDataBatch::addData(std::shared_ptr<EntityBuildData> data) {
    datas.push_back(data);
}

Entity::Entity(std::shared_ptr<EntityBuildData> chunkBuildData) {
    geometryMaterialFlags = chunkBuildData->geometryMaterialFlags;
    geometryDrawColours = chunkBuildData->geometryDrawColours;
    geometryEyeColours = chunkBuildData->geometryEyeColours;
    surfaceLayoutKey = chunkBuildData->surfaceLayoutKey;
    surfaceLayerBuffer = chunkBuildData->surfaceLayerBuffer;
    surfaceLayerAddresses = std::move(chunkBuildData->surfaceLayerAddresses);
    lineLayoutKey = chunkBuildData->lineLayoutKey;
    hashCode = chunkBuildData->hashCode;
    x = chunkBuildData->x;
    y = chunkBuildData->y;
    z = chunkBuildData->z;
    rayTracingFlag = chunkBuildData->rayTracingFlag;
    prebuiltBLAS = chunkBuildData->prebuiltBLAS;
    coordinate = chunkBuildData->coordinate;
    worldToken = chunkBuildData->worldToken;
    frameToken = chunkBuildData->frameToken;
    resourceGeneration = chunkBuildData->resourceGeneration;
    stageToken = chunkBuildData->stageToken;
    worldStage = chunkBuildData->worldStage;
    shaderKeys = std::make_shared<std::vector<std::string>>(std::move(chunkBuildData->shaderKeys));
    materialKeys = std::make_shared<std::vector<std::string>>(std::move(chunkBuildData->materialKeys));

    blas = chunkBuildData->blas;
    indexBufferAddresses =
        std::make_shared<std::vector<VkDeviceAddress>>(std::move(chunkBuildData->indexBufferAddresses));
    positionBufferAddresses =
        std::make_shared<std::vector<VkDeviceAddress>>(std::move(chunkBuildData->positionBufferAddresses));
    materialBufferAddresses =
        std::make_shared<std::vector<VkDeviceAddress>>(std::move(chunkBuildData->materialBufferAddresses));

    geometryCount = chunkBuildData->geometryCount;
    geometryGroupNames = std::make_shared<std::vector<std::string>>(std::move(chunkBuildData->geometryGroupNames));
    geometryContentNames = std::make_shared<std::vector<std::string>>(std::move(chunkBuildData->geometryContentNames));
    vertexCounts = std::make_shared<std::vector<uint32_t>>();
    indexCounts = std::make_shared<std::vector<uint32_t>>();
    vertexCounts->reserve(geometryCount);
    indexCounts->reserve(geometryCount);
    for (uint32_t i = 0; i < geometryCount; i++) {
        if (chunkBuildData->geometryTypes[i] == World::BOAT_WATER_MASK) {
            std::vector<glm::vec3> positions;
            for (const auto &vertex : chunkBuildData->vertices[i]) positions.push_back(vertex.pos);
            waterExclusions.push_back(mcvr::water::clipPlanes(positions, chunkBuildData->indices[i]));
        }
        vertexCounts->push_back(chunkBuildData->vertexCount(i));
        indexCounts->push_back(chunkBuildData->indexCount(i));
    }
}

EntityBatch::EntityBatch(std::shared_ptr<EntityBuildDataBatch> entityBuildDataBatch) {
    for (auto data : entityBuildDataBatch->datas) {
        auto entity = Entity::create(data);
        entity->indexBuffer = entityBuildDataBatch->indexBuffer;
        entity->positionBuffer = entityBuildDataBatch->positionBuffer;
        entity->materialBuffer = entityBuildDataBatch->materialBuffer;
        entities.push_back(entity);
    }

    indexBuffer = entityBuildDataBatch->indexBuffer;
    positionBuffer = entityBuildDataBatch->positionBuffer;
    materialBuffer = entityBuildDataBatch->materialBuffer;
}

EntityPost::EntityPost(std::shared_ptr<EntityBuildData> chunkBuildData) {
    postRenderFlag = chunkBuildData->postRenderFlag;
    x = chunkBuildData->x;
    y = chunkBuildData->y;
    z = chunkBuildData->z;

    geometryCount = chunkBuildData->geometryCount;
    geometryContentNames = std::move(chunkBuildData->geometryContentNames);
    indexCounts.reserve(geometryCount);

    auto framework = Renderer::instance().framework();
    auto vma = framework->vma();
    auto device = framework->device();

    for (int i = 0; i < geometryCount; i++) {
        auto vertexBuffer = vk::DeviceLocalBuffer::create(
            vma, device, false, chunkBuildData->vertices[i].size() * sizeof(vk::VertexFormat::PBRVertex),
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        auto indexBuffer = vk::DeviceLocalBuffer::create(
            vma, device, false, chunkBuildData->indices[i].size() * sizeof(uint32_t), VK_BUFFER_USAGE_INDEX_BUFFER_BIT);

        vertexBuffer->uploadToStagingBuffer(chunkBuildData->vertices[i].data());
        indexBuffer->uploadToStagingBuffer(chunkBuildData->indices[i].data());

        vertexBuffers.push_back(vertexBuffer);
        indexBuffers.push_back(indexBuffer);
        indexCounts.push_back(static_cast<uint32_t>(chunkBuildData->indices[i].size()));
    }
}

EntityPostBatch::EntityPostBatch(std::shared_ptr<EntityPostBuildDataBatch> entityPostBuildDataBatch) {
    for (auto data : entityPostBuildDataBatch->datas) { entities.push_back(EntityPost::create(data)); }
}

Entities::Entities(std::shared_ptr<Framework> framework) {
    // Local feasibility experiment; normal rendering keeps synchronous preparation.
    const char *value = std::getenv("RADIANCE_CPU_TOPOLOGY_WORKERS");
    if (!value || std::string_view(value) == "0") return;
    if (std::string_view(value) != "1" && std::string_view(value) != "2" && std::string_view(value) != "4")
        throw std::invalid_argument("RADIANCE_CPU_TOPOLOGY_WORKERS must be 0, 1, 2 or 4");
    cpuPreparation_ = std::make_unique<mcvr::CpuPreparationBatch>(value[0] - '0');
}

std::shared_ptr<RigidModelResource> Entities::residentRigidModel(const mcvr::rigid::SubmissionRecord &record,
                                                                 const char *const *groups,
                                                                 std::span<const mcvr::rigid::ModelBinding> bindings) {
    auto framework = Renderer::instance().framework();
    auto &frr = framework->frameResourceRetainer();
    auto found = rigidModels_.find(record.model);
    if (found != rigidModels_.end() && found->second->world == activeWorldToken_ &&
        found->second->generation == activeResourceGeneration_)
        return found->second;

    mcvr::profile::Scope creation("entity-rigid-model-create");
    if (mcvr::profile::enabled())
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.rigid.new-input", record.vertexCount,
                            static_cast<uint64_t>(record.vertexCount) * 128);
    auto build = EntityBuildDataBatch::create();
    const auto start = entityBuildDataBatch_->datas.size();
    int hash = 0, post = 0, prebuilt = -1, geometryCount = bindings.empty() ? 1 : static_cast<int>(bindings.size());
    std::vector<int> formats(geometryCount, World::PBR_TRIANGLE),
        modes(geometryCount, static_cast<int>(World::DrawMode::QUADS));
    std::vector<int> types, textures, counts;
    std::vector<void *> inputs;
    std::vector<const char *> names, contents(geometryCount, "");
    std::vector<glm::vec4> colours;
    if (bindings.empty()) {
        types.push_back(record.type);
        textures.push_back(record.texture);
        counts.push_back(record.vertexCount);
        inputs.push_back(reinterpret_cast<void *>(record.vertices));
        names.push_back(groups[record.group]);
        colours.push_back(glm::vec4(1));
    } else
        for (const auto &binding : bindings) {
            types.push_back(binding.type);
            textures.push_back(binding.texture);
            counts.push_back(binding.vertexCount);
            inputs.push_back(reinterpret_cast<void *>(binding.vertices));
            names.push_back(groups[binding.group]);
            colours.push_back(glm::make_vec4(binding.drawColour));
        }
    int mask = record.mask;
    double origin = 0;
    try {
        // Use the existing material/conversion contract; a nonzero world token disables
        // the deferred dynamic-vertex path. This local mesh is expanded only on a miss.
        queueBuild(EntitiesBuildTask{.coordinate = World::WORLD,
                                     .normalOffset = false,
                                     .entityCount = 1,
                                     .entityHashCodes = &hash,
                                     .entityXs = &origin,
                                     .entityYs = &origin,
                                     .entityZs = &origin,
                                     .entityRayTracingFlags = &mask,
                                     .entityPostRenderFlags = &post,
                                     .entityPrebuiltBLASs = &prebuilt,
                                     .entityPosts = &post,
                                     .entityGeometryCounts = &geometryCount,
                                     .geometryTypes = types.data(),
                                     .geometryGroupNames = names.data(),
                                     .geometryContentNames = contents.data(),
                                     .geometryTextures = textures.data(),
                                     .vertexFormats = formats.data(),
                                     .indexFormats = modes.data(),
                                     .vertexCounts = counts.data(),
                                     .vertices = inputs.data(),
                                     // Declared local coatings use the same composition as captured
                                     // owners, once at resource creation. Their CPU input is copied
                                     // before returning to Java, never retained as borrowed pointers.
                                     .worldToken = activeWorldToken_,
                                     .frameToken = activeFrameToken_,
                                     .resourceGeneration = activeResourceGeneration_,
                                     .geometryDrawColours = colours.data(),
                                     .localModelResource = true});
        if (entityBuildDataBatch_->datas.size() != start + 1)
            throw std::logic_error("Rigid model conversion did not produce one model");
        build->datas.push_back(entityBuildDataBatch_->datas.back());
        entityBuildDataBatch_->datas.resize(start);
    } catch (...) {
        entityBuildDataBatch_->datas.resize(start);
        throw;
    }
    build->build();
    auto content = std::make_shared<mcvr::RigidGeometryContent>();
    const auto &converted = *build->datas.front();
    const mcvr::faces::ModelRules shapeFaces(converted.geometryMaterialFlags);
    for (size_t g = 0; g < converted.geometryCount; ++g) {
        auto positions = vk::Vertex::buildPositionVertices(converted.vertices[g]);
        if (!converted.topologyVertexFlags[g].empty())
            for (size_t v = 0; v < positions.size(); ++v) positions[v].pad0 = converted.topologyVertexFlags[g][v];
        const bool opaque = converted.geometryTypes[g] == World::WORLD_SOLID &&
                            !shapeFaces.needsAnyHit(converted.geometryMaterialFlags[g]);
        content->append(opaque ? VK_GEOMETRY_OPAQUE_BIT_KHR : VK_GEOMETRY_NO_DUPLICATE_ANY_HIT_INVOCATION_BIT_KHR,
                        std::as_bytes(std::span(positions)), converted.indices[g]);
    }
    const auto geometryHash = content->hash();
    std::shared_ptr<RigidModelResource> geometryOwner;
    const auto candidates = rigidGeometries_.equal_range(geometryHash);
    for (auto it = candidates.first; it != candidates.second; ++it) {
        auto candidate = it->second.lock();
        if (candidate && candidate->world == activeWorldToken_ && candidate->generation == activeResourceGeneration_ &&
            *candidate->geometryContent == *content) {
            geometryOwner = std::move(candidate);
            break;
        }
    }
    if (geometryOwner) {
        const auto &original = *geometryOwner->batch->entities.front();
        auto &data = *build->datas.front();
        data.blas = geometryOwner->blas;
        data.indexBufferAddresses = *original.indexBufferAddresses;
        data.positionBufferAddresses = *original.positionBufferAddresses;
        build->indexBuffer = geometryOwner->batch->indexBuffer;
        build->positionBuffer = geometryOwner->batch->positionBuffer;
        // The newly allocated AS has never been recorded. Only the material is
        // published; the original traversal input and BLAS stay owned by the root.
        build->blasBatchBuilder.reset();
        if (mcvr::profile::enabled()) mcvr::profile::emit(mcvr::profile::frame, 5, "entity.rigid.geometry-reuse", 1, 0);
    }
    auto buffers = Renderer::instance().buffers();
    if (!geometryOwner) {
        buffers->queueImportantWorldUpload(build->indexBuffer);
        buffers->queueImportantWorldUpload(build->positionBuffer);
    }
    buffers->queueImportantWorldUpload(build->materialBuffer);
    auto resource = std::make_shared<RigidModelResource>();
    resource->build = geometryOwner ? nullptr : build;
    resource->geometryOwner = geometryOwner;
    resource->geometryContent = geometryOwner ? geometryOwner->geometryContent : std::move(content);
    resource->batch = EntityBatch::create(build);
    resource->world = activeWorldToken_;
    resource->generation = activeResourceGeneration_;
    const auto &geometry = *resource->batch->entities.front();
    if (!geometry.geometryCount || !geometry.blas) throw std::logic_error("Rigid model must produce a BLAS");
    resource->blas = geometry.blas;
    resource->groupId = mcvr::hitgroups::intern(geometry.geometryGroupNames && !geometry.geometryGroupNames->empty() ?
                                                    std::string_view(geometry.geometryGroupNames->front()) :
                                                    std::string_view("default"));
    resource->materialFlags = geometry.geometryMaterialFlags.front();
    resource->faces = mcvr::faces::ModelRules(geometry.geometryMaterialFlags);
    resource->indexAddress = geometry.indexBufferAddresses->front();
    resource->positionAddress = geometry.positionBufferAddresses->front();
    resource->materialAddress = geometry.materialBufferAddresses->front();
    resource->vertexCount = 0;
    resource->indexCount = 0;
    for (size_t g = 0; g < geometry.geometryCount; ++g) {
        resource->vertexCount += (*geometry.vertexCounts)[g];
        resource->indexCount += (*geometry.indexCounts)[g];
        resource->geometries.push_back(RigidModelResource::Geometry{
            mcvr::hitgroups::intern((*geometry.geometryGroupNames)[g]), geometry.geometryMaterialFlags[g],
            (*geometry.vertexCounts)[g], (*geometry.indexCounts)[g], (*geometry.indexBufferAddresses)[g],
            (*geometry.positionBufferAddresses)[g], (*geometry.materialBufferAddresses)[g],
            g < geometry.surfaceLayerAddresses.size() ? geometry.surfaceLayerAddresses[g] : 0,
            g < geometry.geometryDrawColours.size() ? geometry.geometryDrawColours[g] : glm::vec4(1),
            g < geometry.geometryEyeColours.size() ? geometry.geometryEyeColours[g] : glm::vec4(1)});
    }
    resource->residentBytes = build->indexBuffer->size() + build->positionBuffer->size() +
                              build->materialBuffer->size() + resource->blas->blasBuffer()->size();
    // Charge shared ownership conservatively, including the canonical CPU key;
    // cache eviction can never drop the geometry retained by a material view.
    resource->residentBytes += resource->geometryContent->storageBytes();
    if (geometryOwner) resource->residentBytes += geometryOwner->residentBytes;
    if (geometry.surfaceLayerBuffer) resource->residentBytes += geometry.surfaceLayerBuffer->size();
    if (!geometryOwner) rigidGeometries_.emplace(geometryHash, resource);
    if (found != rigidModels_.end()) {
        rigidModelBytes_ -= found->second->residentBytes;
        frr.retain(found->second);
        rigidModels_.erase(found);
    }
    // Budget resident bytes rather than model count: small, live appearance variants must
    // not evict each other in the same frame. Queued/in-flight batches retain evicted owners.
    constexpr size_t budget = 64u * 1024 * 1024;
    while (rigidModelBytes_ + resource->residentBytes > budget) {
        auto oldest = rigidModels_.end();
        for (auto it = rigidModels_.begin(); it != rigidModels_.end(); ++it)
            if (it->second->used != activeFrameToken_ &&
                (oldest == rigidModels_.end() || it->second->used < oldest->second->used))
                oldest = it;
        if (oldest == rigidModels_.end()) return resource;
        rigidModelBytes_ -= oldest->second->residentBytes;
        frr.retain(oldest->second);
        rigidModels_.erase(oldest);
    }
    rigidModelBytes_ += resource->residentBytes;
    rigidModels_[record.model] = resource;
    return resource;
}

void Entities::queueRigidModels(const mcvr::rigid::SubmissionRecord *records,
                                size_t count,
                                const char *const *groups,
                                size_t groupCount,
                                std::span<const mcvr::rigid::ModelBinding> bindings) {
    mcvr::profile::Scope timing("entity-rigid-queue");
    if (!worldMeshFrameOpen_ || !activeWorldToken_ || (count && (!records || !groups || !groupCount)))
        throw std::invalid_argument("Invalid rigid model submission batch");
    for (size_t g = 0; g < groupCount; ++g)
        if (!groups[g]) throw std::invalid_argument("Missing rigid model group name");
    // Validate the complete batch first so a rejected record cannot leave a partial frame.
    for (size_t i = 0; i < count; ++i) mcvr::rigid::validate(records[i], groupCount);
    const auto recipes = mcvr::rigid::bindings(bindings, groupCount);
    rigidInstances_.reserve(rigidInstances_.size() + count);
    for (size_t i = 0; i < count; ++i) {
        const auto &record = records[i];
        const auto recipe = recipes.find(record.model);
        auto resource = residentRigidModel(
            record, groups, recipe == recipes.end() ? std::span<const mcvr::rigid::ModelBinding>{} : recipe->second);
        if (resource->used != activeFrameToken_) {
            rigidUsed_.push_back(resource);
            resource->used = activeFrameToken_;
        }
        rigidInstances_.push_back(RigidInstance{resource.get(), record.model, record.history,
                                                glm::make_mat4(record.matrix), record.x, record.y, record.z,
                                                static_cast<uint32_t>(record.mask), glm::make_vec4(record.drawColour)});
    }
}

std::shared_ptr<RigidInstanceBatch> Entities::rigidInstanceBatch() {
    Renderer::instance().framework()->safeAcquireCurrentContext();
    return rigidBatch_;
}

void Entities::recordRigidModels(const std::shared_ptr<vk::CommandBuffer> &commands) {
    if (mcvr::profile::enabled())
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.rigid.resident", rigidModels_.size(), rigidModelBytes_);
    for (auto &model : rigidUsed_) {
        auto &geometry = model->geometryResource();
        geometry.lifecycle.record([&] {
            geometry.build->blasBatchBuilder->submit(commands);
            if (mcvr::profile::enabled())
                mcvr::profile::emit(mcvr::profile::frame, 5, "entity.rigid.blas-build", geometry.batch->entities.size(),
                                    0);
        });
    }
}

void Entities::commitRigidModels() {
    auto &frr = Renderer::instance().framework()->frameResourceRetainer();
    for (auto &model : rigidUsed_) {
        auto &geometry = model->geometryResource();
        if (geometry.lifecycle.submitted()) {
            frr.retain(geometry.build);
            geometry.build.reset();
        }
    }
}

bool Entities::beginCachedCloud(uint64_t revision, double x, double y, double z) {
    if (revision == 0 || cloudCapturing_ || !entityBuildDataBatch_)
        throw std::logic_error("Invalid cloud geometry capture boundary");
    if (auto cached = cloudCache_.find(revision)) {
        for (const auto &entity : cached->entities) {
            // Per-frame transforms must not mutate entities retained by previous TLAS/history.
            auto next = std::make_shared<Entity>(*entity);
            next->x = x;
            next->y = y;
            next->z = z;
            queuedClouds_.push_back(std::move(next));
        }
        return true;
    }
    cloudRevision_ = revision;
    cloudCaptureStart_ = entityBuildDataBatch_->datas.size();
    cloudCapturing_ = true;
    return false;
}

void Entities::endCachedCloud(bool success) {
    if (!cloudCapturing_) throw std::logic_error("Cloud capture was not opened");
    cloudCapturing_ = false;
    auto &datas = entityBuildDataBatch_->datas;
    if (success) {
        cloudBuildData_ = EntityBuildDataBatch::create();
        cloudBuildData_->datas.assign(datas.begin() + cloudCaptureStart_, datas.end());
    }
    datas.erase(datas.begin() + cloudCaptureStart_, datas.end());
}

void Entities::recordGpuConversion(const std::shared_ptr<vk::CommandBuffer> &commands) {
    if (mcvr::profile::enabled() && entityBuildDataBatch_) {
        size_t builds = 0;
        for (const auto &data : entityBuildDataBatch_->datas)
            if (data->blas) ++builds;
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.dynamic.blas-inputs", builds, 0);
    }
    if (entityBuildDataBatch_ && entityBuildDataBatch_->gpuConversion)
        entityBuildDataBatch_->gpuConversion->record(*Renderer::instance().framework(), commands);
}

void Entities::recordCachedCloudBuild(const std::shared_ptr<vk::CommandBuffer> &commands) {
    if (cloudBlasBuilder_ && !cloudBuildRecorded_) {
        cloudBlasBuilder_->submit(commands);
        cloudBuildRecorded_ = true;
    }
}

void Entities::commitCachedCloudBuild() {
    if (cloudBuildRecorded_) cloudCache_.submitted();
}

void Entities::publishCachedEntities(std::vector<std::shared_ptr<Entity>> entities) {
    auto &retainer = Renderer::instance().framework()->frameResourceRetainer();
    retainer.retain(entityBatch_);
    retainer.retain(blasBatchBuilder_);
    blasBatchBuilder_.reset();
    entityBatch_ = EntityBatch::create();
    entityBatch_->entities = std::move(entities);
}

std::shared_ptr<Entity> Entities::buildUiMaterialUpdate(const std::shared_ptr<Entity> &previous) {
    if (!previous || entityBuildDataBatch_->datas.size() != 1) return {};
    const auto &data = entityBuildDataBatch_->datas.front();
    if (data->surfaceLayerOwner || previous->surfaceLayerBuffer || data->geometryCount != previous->geometryCount)
        return {};
    std::vector<vk::VertexFormat::MaterialVertex> materials;
    std::vector<size_t> offsets;
    for (uint32_t i = 0; i < data->geometryCount; ++i) {
        if (data->vertices[i].size() != previous->vertexCounts->at(i) ||
            data->indices[i].size() != previous->indexCounts->at(i) ||
            data->geometryGroupNames[i] != previous->geometryGroupNames->at(i))
            return {};
        offsets.push_back(materials.size());
        auto part = vk::Vertex::buildMaterialVertices(data->vertices[i]);
        for (auto &vertex : part) vertex.emissiveOverlayTextureID = data->emissiveOverlayTextureIDs[i];
        materials.insert(materials.end(), part.begin(), part.end());
    }
    if (materials.empty()) return {};
    auto framework = Renderer::instance().framework();
    auto next = std::make_shared<Entity>(*previous);
    next->materialBuffer = vk::DeviceLocalBuffer::create(
        framework->vma(), framework->device(), false, materials.size() * sizeof(materials[0]),
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    next->materialBuffer->uploadToStagingBuffer(materials.data());
    next->materialBufferAddresses = std::make_shared<std::vector<VkDeviceAddress>>();
    for (auto offset : offsets)
        next->materialBufferAddresses->push_back(next->materialBuffer->bufferAddress() + offset * sizeof(materials[0]));
    Renderer::instance().buffers()->queueImportantWorldUpload(next->materialBuffer);
    framework->frameResourceRetainer().retain(previous);
    return next;
}

void Entities::resetFrame() {
    mcvr::audit::beginFrame();
    auto framework = Renderer::instance().framework();
    framework->safeAcquireCurrentContext();
    auto &frr = framework->frameResourceRetainer();
    for (auto &model : rigidUsed_) {
        frr.retain(model);
        model->geometryResource().lifecycle.beginFrame();
    }
    rigidUsed_.clear();
    rigidInstances_.clear();
    std::erase_if(rigidModels_, [&](const auto &entry) {
        if (activeFrameToken_ <= entry.second->used + 120) return false;
        rigidModelBytes_ -= entry.second->residentBytes;
        frr.retain(entry.second);
        return true;
    });
    std::erase_if(rigidGeometries_, [](const auto &entry) { return entry.second.expired(); });
    frr.retain(cloudBuildData_);
    frr.retain(cloudBlasBuilder_);
    cloudBuildData_.reset();
    cloudBlasBuilder_.reset();
    queuedClouds_.clear();
    cloudCapturing_ = cloudBuildRecorded_ = false;
    cloudCache_.beginFrame();

    frr.retain(entityBuildDataBatch_);
    entityBuildDataBatch_ = EntityBuildDataBatch::create();

    frr.retain(entityPostBuildDataBatch_);
    entityPostBuildDataBatch_ = EntityPostBuildDataBatch::create();

    frr.retain(entityBatch_);
    entityBatch_ = nullptr;

    frr.retain(entityPostBatch_);
    entityPostBatch_ = nullptr;

    frr.retain(blasBatchBuilder_);
    blasBatchBuilder_ = nullptr;
}

int Entities::beginWorldMeshFrame(uint64_t worldToken, uint64_t frameToken, uint64_t resourceGeneration) {
    if (worldToken == 0 || frameToken == 0 || resourceGeneration == 0) return 3;
    if (entityBuildDataBatch_ == nullptr) entityBuildDataBatch_ = EntityBuildDataBatch::create();
    if (entityPostBuildDataBatch_ == nullptr) entityPostBuildDataBatch_ = EntityPostBuildDataBatch::create();
    auto removeWorldMeshes = [](auto &datas) {
        std::erase_if(datas, [](const auto &data) { return data != nullptr && data->worldToken != 0; });
    };
    removeWorldMeshes(entityBuildDataBatch_->datas);
    removeWorldMeshes(entityPostBuildDataBatch_->datas);
    activeWorldToken_ = worldToken;
    activeFrameToken_ = frameToken;
    activeResourceGeneration_ = resourceGeneration;
    frameEntityCheckpoint_ = entityBuildDataBatch_->datas.size();
    framePostCheckpoint_ = entityPostBuildDataBatch_->datas.size();
    worldMeshStages_.clear();
    worldMeshFrameOpen_ = true;
    return 1;
}

int Entities::beginWorldMeshStage(
    uint64_t worldToken, uint64_t frameToken, uint64_t resourceGeneration, uint64_t stageToken, int) {
    if (!worldMeshFrameOpen_ || !acceptsWorldMeshGeneration(worldToken, frameToken, resourceGeneration)) return 2;
    if (stageToken == 0) return 3;
    worldMeshStages_.push_back(
        {stageToken, entityBuildDataBatch_->datas.size(), entityPostBuildDataBatch_->datas.size()});
    return 1;
}

int Entities::queueWorldMesh(const WorldMeshBuildTask &task) {
    if (!worldMeshFrameOpen_ ||
        !acceptsWorldMeshGeneration(task.worldToken, task.frameToken, task.resourceGeneration) ||
        worldMeshStages_.empty() || worldMeshStages_.back().token != task.stageToken)
        return 2;
    if (task.vertices == nullptr || task.vertexCount <= 0 || task.vertexBytes <= 0 || task.indexCount <= 0 ||
        task.indexBytes < 0 || (task.indices == nullptr && task.indexBytes != 0))
        return 3;
    const size_t expectedVertexBytes = worldVertexStride(task.vertexFormat) * static_cast<size_t>(task.vertexCount);
    if (expectedVertexBytes != static_cast<size_t>(task.vertexBytes)) return 3;
    const size_t indexStride = task.indexType == 0 ? sizeof(uint16_t) : task.indexType == 1 ? sizeof(uint32_t) : 0;
    if (indexStride == 0 || (task.indices != nullptr && indexStride * static_cast<size_t>(task.indexCount) !=
                                                            static_cast<size_t>(task.indexBytes)))
        return 3;
    if (task.drawMode < static_cast<int>(World::DrawMode::LINES) ||
        task.drawMode > static_cast<int>(World::DrawMode::QUADS))
        return 3;
    if (task.coordinate < World::Coordinates::WORLD || task.coordinate > World::Coordinates::CAMERA_SHIFT ||
        (task.geometryType & 0xff) < World::GeometryTypes::SHADOW ||
        (task.geometryType & 0xff) >= World::GeometryTypes::NUM_GEOMETRY_TYPES || task.alphaMode < 0 ||
        task.alphaMode > 255 || (task.alphaMode & mcvr::abi::PACKED_ALPHA_MASK) > mcvr::abi::ALPHA_ORDERED_OPAQUE ||
        !std::isfinite(task.emission) || task.emission < 0.0f || task.shaderKey == nullptr ||
        task.materialKey == nullptr)
        return 3;

    // Captured lines carry the source logical order. A foreign explicit index stream
    // requires its own line-index contract, not reinterpretation as a triangle mesh.
    if (task.drawMode <= 3 && task.indices != nullptr) return 3;
    int entityHashCode = task.sourceId;
    double entityX = task.originX, entityY = task.originY, entityZ = task.originZ;
    int rayTracingFlag = task.rayTracingFlag;
    int postRenderFlag = 0, prebuiltBlas = -1, post = 0, geometryCount = 1;
    int geometryType = task.geometryType, texture = task.textureId;
    int vertexFormat = task.vertexFormat, drawMode = task.drawMode, vertexCount = task.vertexCount;
    int indexType = task.indexType, indexCount = task.indexCount, indexBytes = task.indexBytes;
    int alphaMode = task.alphaMode;
    float emission = task.emission;
    void *vertices = task.vertices, *indices = task.indices;
    const char *materialKey = task.materialKey == nullptr ? "" : task.materialKey;
    std::string groupName(materialKey);
    if (const auto separator = groupName.find('|'); separator != std::string::npos) { groupName.erase(separator); }
    if (groupName.empty()) groupName = "world_stage";
    const char *groupNamePtr = groupName.c_str();
    const char *contentNamePtr = materialKey;
    const char *shaderKey = task.shaderKey == nullptr ? "" : task.shaderKey;

    const size_t before = entityBuildDataBatch_->datas.size();
    queueBuild(EntitiesBuildTask{
        .coordinate = task.coordinate,
        .normalOffset = false,
        .entityCount = 1,
        .entityHashCodes = &entityHashCode,
        .entityXs = &entityX,
        .entityYs = &entityY,
        .entityZs = &entityZ,
        .entityRayTracingFlags = &rayTracingFlag,
        .entityPostRenderFlags = &postRenderFlag,
        .entityPrebuiltBLASs = &prebuiltBlas,
        .entityPosts = &post,
        .entityGeometryCounts = &geometryCount,
        .geometryTypes = &geometryType,
        .geometryGroupNames = &groupNamePtr,
        .geometryContentNames = &contentNamePtr,
        .geometryTextures = &texture,
        .vertexFormats = &vertexFormat,
        .indexFormats = &drawMode,
        .vertexCounts = &vertexCount,
        .vertices = &vertices,
        .worldToken = task.worldToken,
        .frameToken = task.frameToken,
        .resourceGeneration = task.resourceGeneration,
        .stageToken = task.stageToken,
        .stage = task.stage,
        .geometryIndexTypes = &indexType,
        .geometryIndexCounts = &indexCount,
        .geometryIndexByteCounts = &indexBytes,
        .geometryIndices = &indices,
        .geometryAlphaModes = &alphaMode,
        .geometryEmissions = &emission,
        .geometryShaderKeys = &shaderKey,
        .geometryMaterialKeys = &materialKey,
        .geometryLineStyles = task.lineStyle,
        .geometryDrawColours = &task.drawColour,
    });
    if (entityBuildDataBatch_->datas.size() <= before) return 3;
    if (task.auditId != 0) {
        for (size_t i = before; i < entityBuildDataBatch_->datas.size(); ++i) {
            if (entityBuildDataBatch_->datas[i] != nullptr) { entityBuildDataBatch_->datas[i]->auditId = task.auditId; }
        }
        worldMeshAuditStates_[task.auditId] = WorldMeshAuditState::Queued;
    }
    return 1;
}

int Entities::pollWorldMeshAudit(uint64_t auditId, bool consumeTerminal) {
    const auto found = worldMeshAuditStates_.find(auditId);
    if (found == worldMeshAuditStates_.end()) return 0;
    const auto status = found->second;
    if (consumeTerminal && status >= WorldMeshAuditState::Built) { worldMeshAuditStates_.erase(found); }
    return static_cast<int>(status);
}

void Entities::endWorldMeshStage(
    uint64_t worldToken, uint64_t frameToken, uint64_t resourceGeneration, uint64_t stageToken, bool commit) {
    if (!acceptsWorldMeshGeneration(worldToken, frameToken, resourceGeneration) || worldMeshStages_.empty() ||
        worldMeshStages_.back().token != stageToken) {
        throw std::runtime_error("World mesh stage closed out of order or after generation change");
    }
    const StageCheckpoint checkpoint = worldMeshStages_.back();
    worldMeshStages_.pop_back();
    if (!commit) {
        for (size_t i = checkpoint.entityCount; i < entityBuildDataBatch_->datas.size(); ++i) {
            const auto &data = entityBuildDataBatch_->datas[i];
            if (data != nullptr && data->auditId != 0) {
                worldMeshAuditStates_[data->auditId] = WorldMeshAuditState::RolledBack;
            }
        }
        entityBuildDataBatch_->datas.resize(checkpoint.entityCount);
        entityPostBuildDataBatch_->datas.resize(checkpoint.postCount);
    }
}

void Entities::endWorldMeshFrame(uint64_t worldToken, uint64_t frameToken, uint64_t resourceGeneration, bool commit) {
    if (!acceptsWorldMeshGeneration(worldToken, frameToken, resourceGeneration)) {
        throw std::runtime_error("World mesh frame closed after generation change");
    }
    if (!worldMeshStages_.empty()) throw std::runtime_error("World mesh frame has active stages");
    if (!commit) {
        rigidInstances_.clear();
        for (size_t i = frameEntityCheckpoint_; i < entityBuildDataBatch_->datas.size(); ++i) {
            const auto &data = entityBuildDataBatch_->datas[i];
            if (data != nullptr && data->auditId != 0) {
                worldMeshAuditStates_[data->auditId] = WorldMeshAuditState::RolledBack;
            }
        }
        entityBuildDataBatch_->datas.resize(frameEntityCheckpoint_);
        entityPostBuildDataBatch_->datas.resize(framePostCheckpoint_);
    } else {
        for (size_t i = frameEntityCheckpoint_; i < entityBuildDataBatch_->datas.size(); ++i) {
            const auto &data = entityBuildDataBatch_->datas[i];
            if (data != nullptr && data->auditId != 0) {
                worldMeshAuditStates_[data->auditId] = WorldMeshAuditState::Committed;
            }
        }
    }
    worldMeshFrameOpen_ = false;
}

void Entities::invalidateWorldMeshGeneration(uint64_t resourceGeneration) {
    auto &retainer = Renderer::instance().framework()->frameResourceRetainer();
    for (const auto &[key, model] : rigidModels_) retainer.retain(model);
    rigidModels_.clear();
    rigidGeometries_.clear();
    rigidModelBytes_ = 0;
    rigidInstances_.clear();
    activeResourceGeneration_ = resourceGeneration;
    worldMeshFrameOpen_ = false;
    worldMeshStages_.clear();
    if (entityBuildDataBatch_ != nullptr) {
        for (const auto &data : entityBuildDataBatch_->datas) {
            if (data != nullptr && data->worldToken != 0 && data->auditId != 0) {
                worldMeshAuditStates_[data->auditId] = WorldMeshAuditState::Stale;
            }
        }
        std::erase_if(entityBuildDataBatch_->datas,
                      [](const auto &data) { return data != nullptr && data->worldToken != 0; });
    }
    if (entityPostBuildDataBatch_ != nullptr) {
        std::erase_if(entityPostBuildDataBatch_->datas,
                      [](const auto &data) { return data != nullptr && data->worldToken != 0; });
    }
}

bool Entities::acceptsWorldMeshGeneration(uint64_t worldToken, uint64_t frameToken, uint64_t resourceGeneration) const {
    return WorldMeshContract::generationMatches(activeWorldToken_, activeFrameToken_, activeResourceGeneration_,
                                                worldToken, frameToken, resourceGeneration);
}

void Entities::queueBuild(EntitiesBuildTask task) {
    mcvr::profile::Scope totalProfile("entity.queue.total");
    {
        mcvr::profile::Scope validationProfile("entity.queue.validate");
        validateEntitySourceFormat(task);
    }
    mcvr::profile::Scope auditProfile("entity-convert-copy-enqueue");
    if (mcvr::profile::enabled()) {
        static constexpr const char *labels[] = {"entity.format.block",
                                                 "entity.format.entity",
                                                 "entity.format.particle",
                                                 "entity.format.position",
                                                 "entity.format.color",
                                                 "entity.format.lines",
                                                 "entity.format.color-light",
                                                 "entity.format.tex",
                                                 "entity.format.tex-color",
                                                 "entity.format.color-tex-light",
                                                 "entity.format.tex-light-color",
                                                 "entity.format.tex-color-normal",
                                                 "entity.format.pbr",
                                                 "entity.format.pbr-compact",
                                                 "entity.format.captured-lines"};
        static_assert(std::size(labels) == World::NUM_VERTEX_FORMATS);
        std::array<uint64_t, World::NUM_VERTEX_FORMATS> counts{};
        size_t geometry = 0;
        for (int e = 0; e < task.entityCount; ++e) {
            for (int i = 0; i < task.entityGeometryCounts[e]; ++i, ++geometry) {
                const int format = task.vertexFormats[geometry];
                if (format >= 0 && format < World::NUM_VERTEX_FORMATS && task.vertexCounts[geometry] > 0)
                    counts[format] += task.vertexCounts[geometry];
            }
        }
        for (int i = 0; i < World::NUM_VERTEX_FORMATS; ++i)
            if (counts[i])
                mcvr::profile::emit(mcvr::profile::frame, 5, labels[i], counts[i], counts[i] * worldVertexStride(i));
    }
    mcvr::profile::Accumulated formatPhase("entity.convert.format"), topologyPhase("entity.convert.topology"),
        materialPhase("entity.convert.material"), recordPhase("entity.queue.record-state"),
        geometryPhase("entity.queue.geometry-state"), smallPairPhase("entity.queue.pair-scan-le64"),
        largePairPhase("entity.queue.pair-scan-gt64"), overlayPhase("entity.queue.eye-overlays"),
        dataPhase("entity.queue.build-record");
    uint64_t smallPairCalls = 0, smallPairVertices = 0, largePairCalls = 0, largePairVertices = 0, pairMatches = 0;
    {
        mcvr::profile::Scope acquireProfile("entity.queue.acquire-context");
        Renderer::instance().framework()->safeAcquireCurrentContext();
    }
    auto framework = Renderer::instance().framework();
    auto vma = framework->vma();
    auto device = framework->device();
    auto physicalDevice = framework->physicalDevice();
    std::vector<std::shared_ptr<EntityBuildData>> preparedEntityData;
    std::vector<std::shared_ptr<EntityBuildData>> preparedPostData;
    mcvr::EntityDirectInputLayout directInputLayout;

    std::set<int> textureIDs;

    uint32_t geometryAccu = 0;
    for (int e = 0; e < task.entityCount; e++) {
        mcvr::profile::Scope recordProfile(recordPhase);
        uint32_t geometryIndex = geometryAccu;
        uint32_t geometryCountIncludeGlint = task.entityGeometryCounts[e];
        geometryAccu += geometryCountIncludeGlint;

        uint32_t allVertexCount = 0, allIndexCount = 0;
        bool hasLineGeometry = false;
        std::vector<uint32_t> geometryMaterialFlags;
        std::vector<glm::vec4> geometryDrawColours;
        std::vector<glm::vec4> geometryEyeColours;
        std::vector<uint32_t> geometryEmissionPolicies;
        geometryDrawColours.reserve(geometryCountIncludeGlint);
        std::vector<bool> authoredQuads;
        std::vector<World::GeometryTypes> geometryTypes;
        std::vector<std::string> geometryGroupNames;
        std::vector<std::string> geometryContentNames;
        std::vector<std::vector<vk::VertexFormat::PBRVertex>> vertices;
        std::vector<std::vector<uint32_t>> indices;
        std::vector<mcvr::EntityRawGeometry> rawGeometry;
        std::vector<uint32_t> emissiveOverlayTextureIDs;
        std::vector<std::string> shaderKeys;
        std::vector<std::string> materialKeys;
        // JNI already supplies these sizes. Reserve without changing filtering or element order.
        geometryMaterialFlags.reserve(geometryCountIncludeGlint);
        geometryTypes.reserve(geometryCountIncludeGlint);
        geometryGroupNames.reserve(geometryCountIncludeGlint);
        geometryContentNames.reserve(geometryCountIncludeGlint);
        vertices.reserve(geometryCountIncludeGlint);
        indices.reserve(geometryCountIncludeGlint);
        rawGeometry.reserve(geometryCountIncludeGlint);
        emissiveOverlayTextureIDs.reserve(geometryCountIncludeGlint);
        shaderKeys.reserve(geometryCountIncludeGlint);
        materialKeys.reserve(geometryCountIncludeGlint);
        int hashCode = task.entityHashCodes[e];
        double x = task.entityXs[e];
        double y = task.entityYs[e];
        double z = task.entityZs[e];
        int rayTracingFlag = task.entityRayTracingFlags[e];
        int postRenderFlag = task.entityPostRenderFlags[e];
        int prebuiltBLAS = task.entityPrebuiltBLASs[e];
        World::Coordinates coordinate = task.coordinate;
        bool post = task.entityPosts[e];

        std::vector<uint8_t> surfaceQualification;
        bool layerOwner = false;
        for (uint32_t i = 0; i < geometryCountIncludeGlint; ++i)
            layerOwner |= (uint32_t(task.geometryTypes[geometryIndex + i]) & mcvr::abi::SOURCE_SURFACE_LAYER_BIT) != 0;
        layerOwner = layerOwner && !post && (task.worldToken == 0 || task.localModelResource);
        bool eyeLayer = false;
        for (uint32_t i = 0; i < geometryCountIncludeGlint; ++i)
            eyeLayer |= mcvr::surface::eyeOverlay(
                mcvr::surface::sourceEmission(static_cast<uint32_t>(task.geometryTypes[geometryIndex + i])));

        uint32_t geometryCountWithoutGlint = 0;
        for (int i = 0; i < task.entityGeometryCounts[e]; i++) {
            mcvr::profile::Scope geometryProfile(geometryPhase);
            // Reject incomplete QUADS before the legacy expansion can read j+3.
            if (task.geometryIndexCounts == nullptr)
                WorldMeshContract::validateSourceSurfaceCount(task.indexFormats[geometryIndex + i],
                                                              task.vertexCounts[geometryIndex + i]);
            World::GeometryTypes geometryType =
                static_cast<World::GeometryTypes>(task.geometryTypes[geometryIndex + i] & 0xff);
            geometryMaterialFlags.push_back(
                mcvr::surface::sourceFaces(static_cast<uint32_t>(task.geometryTypes[geometryIndex + i])));
            geometryEmissionPolicies.push_back(
                mcvr::surface::sourceEmission(static_cast<uint32_t>(task.geometryTypes[geometryIndex + i])));
            auto drawColour = task.geometryDrawColours ? task.geometryDrawColours[geometryIndex + i] : glm::vec4(1.0f);
            mcvr::surface::validateDrawColour(glm::value_ptr(drawColour));
            geometryDrawColours.push_back(drawColour);
            authoredQuads.push_back(task.indexFormats[geometryIndex + i] == static_cast<int>(World::DrawMode::QUADS));
            int geometryTexture = task.geometryTextures[geometryIndex + i];
            geometryTypes.push_back(geometryType);
            surfaceQualification.push_back(
                layerOwner &&
                (uint32_t(task.geometryTypes[geometryIndex + i]) & mcvr::abi::SOURCE_SURFACE_LAYER_BIT) != 0 &&
                (geometryType == World::WORLD_SOLID || geometryType == World::WORLD_TRANSPARENT));
            if (task.geometryGroupNames != nullptr && task.geometryGroupNames[geometryIndex + i] != nullptr) {
                geometryGroupNames.emplace_back(task.geometryGroupNames[geometryIndex + i]);
            } else {
                geometryGroupNames.emplace_back("Entity");
            }
            if (task.geometryContentNames != nullptr && task.geometryContentNames[geometryIndex + i] != nullptr) {
                geometryContentNames.emplace_back(task.geometryContentNames[geometryIndex + i]);
            } else {
                geometryContentNames.emplace_back("");
            }
            shaderKeys.emplace_back(task.geometryShaderKeys != nullptr &&
                                            task.geometryShaderKeys[geometryIndex + i] != nullptr ?
                                        task.geometryShaderKeys[geometryIndex + i] :
                                        "");
            materialKeys.emplace_back(task.geometryMaterialKeys != nullptr &&
                                              task.geometryMaterialKeys[geometryIndex + i] != nullptr ?
                                          task.geometryMaterialKeys[geometryIndex + i] :
                                          "");
            const bool lineInput =
                task.indexFormats[geometryIndex + i] >= 0 && task.indexFormats[geometryIndex + i] <= 3;
            mcvr::lines::Style lineStyle;
            const bool validLineStyle =
                !lineInput ||
                (task.geometryLineStyles != nullptr &&
                 mcvr::lines::decodeStyle(
                     {task.geometryLineStyles + (geometryIndex + i) * mcvr::lines::styleBytes, mcvr::lines::styleBytes},
                     lineStyle));
            hasLineGeometry |= lineInput && validLineStyle;
            const bool semanticEmission = mcvr::surface::forceUnitEmission(geometryEmissionPolicies.back());

            auto &geometryVertices = vertices.emplace_back();
            auto &geometryIndices = indices.emplace_back();

            auto &raw = rawGeometry.emplace_back();
            if (geometryTypes.back() != World::BOAT_WATER_MASK && gpuEntityConversionEnabled() &&
                mcvr::rawEntityConversionEligible({task.vertexFormats[geometryIndex + i],
                                                   task.indexFormats[geometryIndex + i],
                                                   task.vertexCounts[geometryIndex + i], post, task.worldToken != 0,
                                                   task.geometryIndexCounts != nullptr, eyeLayer || layerOwner,
                                                   cloudCapturing_, prebuiltBLAS >= 0}) &&
                ![&] {
                    const auto count = static_cast<uint32_t>(task.vertexCounts[geometryIndex + i]);
                    const uint32_t corners = authoredQuads.back() ? 4u : 3u;
                    const bool small = count / corners <= 64;
                    mcvr::profile::Scope pairProfile(small ? smallPairPhase : largePairPhase);
                    const bool matched =
                        rawHasTwinQuads(task, geometryIndex + i, hashCode,
                                        geometryContentNames.back() + "/" + geometryGroupNames.back(), x, y, z);
                    if (mcvr::profile::enabled()) {
                        (small ? smallPairCalls : largePairCalls)++;
                        (small ? smallPairVertices : largePairVertices) += count;
                        pairMatches += matched;
                    }
                    return matched;
                }()) {
                mcvr::profile::Scope capture(formatPhase);
                const uint32_t count = task.vertexCounts[geometryIndex + i];
                const uint32_t sourceStride = mcvr::pbrSourceStride(task.vertexFormats[geometryIndex + i]);
                const size_t sourceBytes =
                    task.directEntityInput ?
                        mcvr::checkedEntitySourceBytes(
                            count, sourceStride,
                            static_cast<uint64_t>(task.geometryVertexByteCounts[geometryIndex + i])) :
                        mcvr::entitySourceByteCount(count, sourceStride);
                raw.wordCount = mcvr::entityWordCount(sourceBytes);
                raw.deferred = true;
                if (task.directEntityInput) {
                    raw.pendingSource = static_cast<const std::byte *>(task.vertices[geometryIndex + i]);
                    raw.pendingSourceBytes = sourceBytes;
                    raw.pendingSourceOffsetBytes = directInputLayout.append(sourceBytes);
                } else {
                    raw.words = std::make_unique_for_overwrite<uint32_t[]>(raw.wordCount);
                    std::memcpy(raw.words.get(), task.vertices[geometryIndex + i], sourceBytes);
                }
                auto &job = raw.parameters;
                job.sourceWords = sourceStride / 4;
                job.sourceVersion = task.vertexFormats[geometryIndex + i] == World::PBR_COMPACT_V1 ? 1 : 0;
                job.deferred = 1;
                job.vertexCount = count;
                job.quadIndices = task.indexFormats[geometryIndex + i] == static_cast<int>(World::DrawMode::QUADS);
                const uint64_t indexCount = job.quadIndices ? static_cast<uint64_t>(count) / 4 * 6 : count;
                if (indexCount > UINT32_MAX) throw std::length_error("Entity index count overflow");
                job.indexCount = static_cast<uint32_t>(indexCount);
                job.normalOffset = task.normalOffset;
                job.coordinate = static_cast<uint32_t>(coordinate) & 15u;
                job.emissionPolicy = (task.geometryEmissions ? 1u : 0u) | (semanticEmission ? 2u : 0u);
                job.emissionBits =
                    task.geometryEmissions ? std::bit_cast<uint32_t>(task.geometryEmissions[geometryIndex + i]) : 0u;
                // PBR alpha is already encoded. The original contract ignores the RenderType alpha override.
                allVertexCount += count;
                allIndexCount += job.indexCount;
                geometryCountWithoutGlint++;
                continue;
            }

            mcvr::profile::Phases conversion(formatPhase);
            if (task.vertexFormats[geometryIndex + i] == World::PBR_COMPACT_V1) {
                const auto *source = static_cast<const std::byte *>(task.vertices[geometryIndex + i]);
                const size_t count = static_cast<size_t>(task.vertexCounts[geometryIndex + i]);
                geometryVertices.reserve(count);
                for (size_t vertex = 0; vertex < count; ++vertex)
                    geometryVertices.push_back(mcvr::decodeCompactPbr(
                        {source + vertex * mcvr::compactPbrSourceBytes, mcvr::compactPbrSourceBytes}));
            } else if (task.vertexFormats[geometryIndex + i] == World::PBR_TRIANGLE) {
                geometryVertices.resize(task.vertexCounts[geometryIndex + i]);
                std::memcpy(geometryVertices.data(), task.vertices[geometryIndex + i],
                            task.vertexCounts[geometryIndex + i] * sizeof(vk::VertexFormat::PBRVertex));
            } else {
                geometryVertices.reserve(task.vertexCounts[geometryIndex + i]);
                for (int j = 0; j < task.vertexCounts[geometryIndex + i]; j++) {
                    vk::VertexFormat::PBRVertex vertex{};

                    switch (task.vertexFormats[geometryIndex + i]) {
                        case World::POSITION_COLOR_TEXTURE_LIGHT_NORMAL: {
                            vk::VertexFormat::PositionColorTexLightNormal *vertices =
                                static_cast<vk::VertexFormat::PositionColorTexLightNormal *>(
                                    task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            vertex.useTexture = 1;
                            vertex.textureUV = vertices[j].uv0;

                            vertex.useLight = 1;
                            vertex.lightUV = glm::ivec2{
                                vertices[j].uv2 & 0xFFFF,
                                (vertices[j].uv2 >> 16) & 0xFFFF,
                            };

                            vertex.useNorm = 1;
                            vertex.norm = glm::vec3{
                                (int8_t)(vertices[j].normal & 0xFF),
                                (int8_t)((vertices[j].normal >> 8) & 0xFF),
                                (int8_t)((vertices[j].normal >> 16) & 0xFF),
                            };

                            break;
                        }
                        case World::POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL: {
                            vk::VertexFormat::PositionColorTexOverlayLightNormal *vertices =
                                static_cast<vk::VertexFormat::PositionColorTexOverlayLightNormal *>(
                                    task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            vertex.useTexture = 1;
                            vertex.textureUV = vertices[j].uv0;

                            vertex.useOverlay = 1;
                            vertex.overlayUV = glm::ivec2{vertices[j].uv1 & 0xFFFF, (vertices[j].uv1 >> 16) & 0xFFFF};

                            vertex.useLight = 1;
                            vertex.lightUV = glm::vec2{
                                vertices[j].uv2 & 0xFFFF,
                                (vertices[j].uv2 >> 16) & 0xFFFF,
                            };

                            vertex.useNorm = 1;
                            vertex.norm = glm::vec3{
                                (int8_t)(vertices[j].normal & 0xFF),
                                (int8_t)((vertices[j].normal >> 8) & 0xFF),
                                (int8_t)((vertices[j].normal >> 16) & 0xFF),
                            };

                            break;
                        }
                        case World::POSITION_TEXTURE_COLOR_LIGHT: {
                            vk::VertexFormat::PositionTexColorLight *vertices =
                                static_cast<vk::VertexFormat::PositionTexColorLight *>(
                                    task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useTexture = 1;
                            vertex.textureUV = vertices[j].uv0;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            vertex.useLight = 1;
                            vertex.lightUV = glm::vec2{
                                vertices[j].uv2 & 0xFFFF,
                                (vertices[j].uv2 >> 16) & 0xFFFF,
                            };

                            break;
                        }
                        case World::POSITION: {
                            vk::VertexFormat::PositionOnly *vertices =
                                static_cast<vk::VertexFormat::PositionOnly *>(task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            break;
                        }
                        case World::POSITION_COLOR: {
                            vk::VertexFormat::PositionColor *vertices =
                                static_cast<vk::VertexFormat::PositionColor *>(task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            break;
                        }
                        case World::CAPTURED_LINES: {
                            const auto &source =
                                static_cast<const mcvr::lines::SourceVertex *>(task.vertices[geometryIndex + i])[j];
                            vertex.pos = source.position;
                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4(source.color & 255, (source.color >> 8) & 255,
                                                          (source.color >> 16) & 255, (source.color >> 24) & 255) /
                                                255.0f;
                            vertex.useNorm = 1;
                            vertex.norm = glm::vec3(int8_t(source.normal & 255), int8_t((source.normal >> 8) & 255),
                                                    int8_t((source.normal >> 16) & 255));
                            break;
                        }
                        case World::LINES: {
                            vk::VertexFormat::PositionColorNormal *vertices =
                                static_cast<vk::VertexFormat::PositionColorNormal *>(task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            vertex.useNorm = 1;
                            vertex.norm = glm::vec3{
                                (int8_t)(vertices[j].normal & 0xFF),
                                (int8_t)((vertices[j].normal >> 8) & 0xFF),
                                (int8_t)((vertices[j].normal >> 16) & 0xFF),
                            };

                            break;
                        }
                        case World::POSITION_COLOR_LIGHT: {
                            vk::VertexFormat::PositionColorLight *vertices =
                                static_cast<vk::VertexFormat::PositionColorLight *>(task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            vertex.useLight = 1;
                            vertex.lightUV = glm::vec2{
                                vertices[j].uv2 & 0xFFFF,
                                (vertices[j].uv2 >> 16) & 0xFFFF,
                            };

                            break;
                        }
                        case World::POSITION_TEXTURE: {
                            vk::VertexFormat::PositionTex *vertices =
                                static_cast<vk::VertexFormat::PositionTex *>(task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useTexture = 1;
                            vertex.textureUV = vertices[j].uv;

                            break;
                        }
                        case World::POSITION_TEXTURE_COLOR: {
                            vk::VertexFormat::PositionTexColor *vertices =
                                static_cast<vk::VertexFormat::PositionTexColor *>(task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useTexture = 1;
                            vertex.textureUV = vertices[j].uv;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            break;
                        }
                        case World::POSITION_COLOR_TEXTURE_LIGHT: {
                            vk::VertexFormat::PositionColorTexLight *vertices =
                                static_cast<vk::VertexFormat::PositionColorTexLight *>(
                                    task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            vertex.useTexture = 1;
                            vertex.textureUV = vertices[j].uv0;

                            vertex.useLight = 1;
                            vertex.lightUV = glm::vec2{
                                vertices[j].uv2 & 0xFFFF,
                                (vertices[j].uv2 >> 16) & 0xFFFF,
                            };

                            break;
                        }
                        case World::POSITION_TEXTURE_LIGHT_COLOR: {
                            vk::VertexFormat::PositionTexLightColor *vertices =
                                static_cast<vk::VertexFormat::PositionTexLightColor *>(
                                    task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useTexture = 1;
                            vertex.textureUV = vertices[j].uv0;

                            vertex.useLight = 1;
                            vertex.lightUV = glm::vec2{
                                vertices[j].uv2 & 0xFFFF,
                                (vertices[j].uv2 >> 16) & 0xFFFF,
                            };

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            break;
                        }
                        case World::POSITION_TEXTURE_COLOR_NORMAL: {
                            vk::VertexFormat::PositionTexColorNormal *vertices =
                                static_cast<vk::VertexFormat::PositionTexColorNormal *>(
                                    task.vertices[geometryIndex + i]);

                            vertex.pos = vertices[j].position;

                            vertex.useTexture = 1;
                            vertex.textureUV = vertices[j].uv0;

                            vertex.useColorLayer = 1;
                            vertex.colorLayer = glm::vec4{
                                vertices[j].color & 0xFF,
                                (vertices[j].color >> 8) & 0xFF,
                                (vertices[j].color >> 16) & 0xFF,
                                (vertices[j].color >> 24) & 0xFF,
                            };
                            vertex.colorLayer /= 255.0;

                            vertex.useNorm = 1;
                            vertex.norm = glm::vec3{
                                (int8_t)(vertices[j].normal & 0xFF),
                                (int8_t)((vertices[j].normal >> 8) & 0xFF),
                                (int8_t)((vertices[j].normal >> 16) & 0xFF),
                            };

                            break;
                        }
                    }

                    vertex.textureID = geometryTexture;

                    geometryVertices.push_back(vertex);
                }
            }

            conversion.next(topologyPhase);
            const auto drawMode = static_cast<World::DrawMode>(task.indexFormats[geometryIndex + i]);
            const bool hasExplicitIndexContract = task.geometryIndexCounts != nullptr &&
                                                  task.geometryIndexTypes != nullptr && task.geometryIndices != nullptr;
            if (hasExplicitIndexContract && !lineInput) {
                geometryIndices = WorldMeshContract::triangulate(
                    static_cast<int>(drawMode),
                    decodeDrawIndices(task, geometryIndex + i, static_cast<uint32_t>(geometryVertices.size()),
                                      drawMode));
                for (auto &vertex : geometryVertices) {
                    if (task.normalOffset && vertex.useNorm) {
                        vertex.pos = mcvr::geometry::finalPosition(vertex.pos, vertex.norm, true);
                    }
                    vertex.coordinate = coordinate;
                    if (post) { vertex.postBase = {x, y, z}; }
                    if (vertex.useTexture) { textureIDs.insert(vertex.textureID); }
                }
            } else
                switch (drawMode) {
                    case World::DrawMode::TRIANGLES: {
                        for (int j = 0; j + 2 < task.vertexCounts[geometryIndex + i]; j += 3) {
                            geometryIndices.push_back(j);
                            geometryIndices.push_back(j + 1);
                            geometryIndices.push_back(j + 2);
                        }
                        for (auto &vertex : geometryVertices) {
                            if (task.normalOffset && vertex.useNorm) {
                                vertex.pos = mcvr::geometry::finalPosition(vertex.pos, vertex.norm, true);
                            }
                            vertex.coordinate = coordinate;
                            if (post) { vertex.postBase = {x, y, z}; }
                            if (vertex.useTexture) { textureIDs.insert(vertex.textureID); }
                        }
                        break;
                    }
                    case World::DrawMode::QUADS: {
                        for (int j = 0; j < task.vertexCounts[geometryIndex + i]; j += 4) {
                            geometryIndices.push_back(j + 0);
                            geometryIndices.push_back(j + 1);
                            geometryIndices.push_back(j + 2);
                            geometryIndices.push_back(j + 2);
                            geometryIndices.push_back(j + 3);
                            geometryIndices.push_back(j + 0);

                            if (task.normalOffset) {
                                if (geometryVertices[j + 0].useNorm)
                                    geometryVertices[j + 0].pos = mcvr::geometry::finalPosition(
                                        geometryVertices[j + 0].pos, geometryVertices[j + 0].norm, true);
                                if (geometryVertices[j + 1].useNorm)
                                    geometryVertices[j + 1].pos = mcvr::geometry::finalPosition(
                                        geometryVertices[j + 1].pos, geometryVertices[j + 1].norm, true);
                                if (geometryVertices[j + 2].useNorm)
                                    geometryVertices[j + 2].pos = mcvr::geometry::finalPosition(
                                        geometryVertices[j + 2].pos, geometryVertices[j + 2].norm, true);
                                if (geometryVertices[j + 3].useNorm)
                                    geometryVertices[j + 3].pos = mcvr::geometry::finalPosition(
                                        geometryVertices[j + 3].pos, geometryVertices[j + 3].norm, true);
                            }

                            geometryVertices[j + 0].coordinate = coordinate;
                            geometryVertices[j + 1].coordinate = coordinate;
                            geometryVertices[j + 2].coordinate = coordinate;
                            geometryVertices[j + 3].coordinate = coordinate;

                            if (post) {
                                geometryVertices[j + 0].postBase = {x, y, z};
                                geometryVertices[j + 1].postBase = {x, y, z};
                                geometryVertices[j + 2].postBase = {x, y, z};
                                geometryVertices[j + 3].postBase = {x, y, z};
                            }

                            if (geometryVertices[j + 3].useTexture) {
                                textureIDs.insert(geometryVertices[j + 0].textureID);
                                textureIDs.insert(geometryVertices[j + 1].textureID);
                                textureIDs.insert(geometryVertices[j + 2].textureID);
                                textureIDs.insert(geometryVertices[j + 3].textureID);
                            }
                        }

                        break;
                    }
                    case World::DrawMode::TRIANGLE_STRIP: {
                        geometryIndices = WorldMeshContract::sequentialSurfaceTriangles(static_cast<int>(drawMode),
                                                                                        geometryVertices.size());
                        // Keep each source vertex and all of its attributes; a strip is not a series
                        // of replacement quads, and odd counts need no speculative j+1 access.
                        for (auto &vertex : geometryVertices) {
                            vertex.coordinate = coordinate;
                            if (post) vertex.postBase = {x, y, z};
                            if (vertex.useTexture) textureIDs.insert(vertex.textureID);
                        }
                        break;
                    }
                    case World::DrawMode::DEBUG_LINE_STRIP:
                    case World::DrawMode::LINE_STRIP:
                    case World::DrawMode::DEBUG_LINES:
                    case World::DrawMode::LINES: {
                        if (!validLineStyle) {
                            logInvalidLineWidthOnce(geometryContentNames.back(), drawMode, 0);
                            geometryVertices.clear();
                            break;
                        }
                        std::vector<vk::VertexFormat::PBRVertex> expanded;
                        const auto &owner = lineStyle.owner;
                        const mcvr::lines::Frame ownerFrame{{owner[0], owner[1], owner[2]},
                                                            {owner[3], owner[4], owner[5]},
                                                            {owner[6], owner[7], owner[8]}};
                        const bool captured = task.vertexFormats[geometryIndex + i] == World::CAPTURED_LINES;
                        const auto *lineSources =
                            captured ?
                                static_cast<const mcvr::lines::SourceVertex *>(task.vertices[geometryIndex + i]) :
                                nullptr;
                        for (const auto [first, second] : mcvr::lines::segments(
                                 static_cast<int>(drawMode), static_cast<uint32_t>(geometryVertices.size()))) {
                            const auto &from = geometryVertices[first];
                            const auto &to = geometryVertices[second];
                            // Zero-alpha strip connectors are source separators, not opaque geometry.
                            if (from.colorLayer.a <= 0.0f && to.colorLayer.a <= 0.0f) continue;
                            auto frame =
                                captured ? mcvr::lines::drawingFrame(lineSources[first], ownerFrame) : ownerFrame;
                            auto [success, corners] = mcvr::lines::prism(from.pos, to.pos, lineStyle.width, frame);
                            if (!success) continue;
                            const auto base = static_cast<uint32_t>(expanded.size());
                            for (int k = 0; k < 8; ++k) {
                                expanded.push_back({.pos = corners[k],
                                                    .useColorLayer = 1,
                                                    .colorLayer = k < 4 ? from.colorLayer : to.colorLayer});
                                expanded.back().coordinate = coordinate;
                                if (post) expanded.back().postBase = {x, y, z};
                            }
                            for (uint32_t index : mcvr::lines::prismIndices) geometryIndices.push_back(base + index);
                        }
                        geometryVertices = std::move(expanded);
                        break;
                    }
                    default: {
                        throw std::runtime_error("Shouldn't be touched");
                    }
                }

            conversion.next(materialPhase);
            if (lineInput && validLineStyle) {
                for (auto &vertex : geometryVertices) {
                    vertex.alphaMode = lineStyle.alphaMode;
                    vertex.albedoEmission = lineStyle.emission;
                }
            }
            const bool pbrSource = task.vertexFormats[geometryIndex + i] == World::PBR_TRIANGLE ||
                                   task.vertexFormats[geometryIndex + i] == World::PBR_COMPACT_V1;
            for (auto &vertex : geometryVertices) {
                if (!lineInput && task.geometryAlphaModes != nullptr) {
                    vertex.alphaMode = WorldMeshContract::alphaMode(
                        pbrSource, vertex.alphaMode, static_cast<uint32_t>(task.geometryAlphaModes[geometryIndex + i]));
                }
                if (!lineInput && task.geometryEmissions != nullptr) {
                    vertex.albedoEmission =
                        WorldMeshContract::emission(vertex.albedoEmission, task.geometryEmissions[geometryIndex + i]);
                }
            }

            if (!lineInput && semanticEmission) {
                for (auto &vertex : geometryVertices) { vertex.albedoEmission = 1.0f; }
            }

            if (geometryVertices.empty() || geometryIndices.empty()) {
                vertices.pop_back();
                indices.pop_back();
                rawGeometry.pop_back();
                geometryMaterialFlags.pop_back();
                geometryDrawColours.pop_back();
                geometryEmissionPolicies.pop_back();
                authoredQuads.pop_back();
                geometryTypes.pop_back();
                surfaceQualification.pop_back();
                geometryGroupNames.pop_back();
                geometryContentNames.pop_back();
                shaderKeys.pop_back();
                materialKeys.pop_back();
            } else {
                allVertexCount += geometryVertices.size();
                allIndexCount += geometryIndices.size();
                geometryCountWithoutGlint++;
            }
        }

        {
            mcvr::profile::Scope overlayProfile(overlayPhase);
            if (eyeLayer) rawGeometry.clear();
            if (!post && (task.worldToken == 0 || task.localModelResource) && !layerOwner) {
                composeEmissiveEyeOverlays(geometryMaterialFlags, geometryEmissionPolicies, geometryEyeColours,
                                           geometryDrawColours, authoredQuads, geometryTypes, geometryGroupNames,
                                           geometryContentNames, vertices, indices, emissiveOverlayTextureIDs);
                geometryCountWithoutGlint = static_cast<uint32_t>(vertices.size());
                shaderKeys.assign(vertices.size(), "");
                materialKeys.assign(vertices.size(), "");
            } else {
                emissiveOverlayTextureIDs.assign(vertices.size(), 0u);
                geometryEyeColours.assign(vertices.size(), glm::vec4(1.0f));
            }
        }

        if (geometryCountWithoutGlint == 0) { continue; }

        std::shared_ptr<EntityBuildData> chunkBuildData;
        {
            mcvr::profile::Scope dataProfile(dataPhase);
            chunkBuildData = EntityBuildData::create(
                hashCode, x, y, z, rayTracingFlag, postRenderFlag, prebuiltBLAS, coordinate, geometryCountWithoutGlint,
                std::move(geometryTypes), std::move(geometryGroupNames), std::move(geometryContentNames),
                std::move(vertices), std::move(indices), std::move(emissiveOverlayTextureIDs), task.worldToken,
                task.frameToken, task.resourceGeneration, task.stageToken, task.stage, std::move(shaderKeys),
                std::move(materialKeys));
        }

        if (hasLineGeometry) {
            bool hasLine = false;
            uint64_t layout = 1469598103934665603ull;
            for (int geometry = 0; geometry < task.entityGeometryCounts[e]; ++geometry) {
                const auto index = geometryIndex + geometry;
                mcvr::lines::Style style;
                const bool line =
                    task.indexFormats[index] >= 0 && task.indexFormats[index] <= 3 &&
                    mcvr::lines::decodeStyle(
                        {task.geometryLineStyles + index * mcvr::lines::styleBytes, mcvr::lines::styleBytes}, style);
                hasLine |= line;
                layout = (layout ^ (line ? style.source : 0u)) * 1099511628211ull;
                layout = (layout ^ uint32_t(task.indexFormats[index])) * 1099511628211ull;
                if (task.geometryGroupNames && task.geometryGroupNames[index])
                    for (const unsigned char c : std::string_view(task.geometryGroupNames[index]))
                        layout = (layout ^ c) * 1099511628211ull;
            }
            chunkBuildData->lineLayoutKey = hasLine ? layout : 0;
        }
        chunkBuildData->geometryMaterialFlags = std::move(geometryMaterialFlags);
        chunkBuildData->geometryDrawColours = std::move(geometryDrawColours);
        chunkBuildData->geometryEyeColours = std::move(geometryEyeColours);
        chunkBuildData->authoredQuads = std::move(authoredQuads);
        chunkBuildData->rawGeometry = std::move(rawGeometry);
        chunkBuildData->surfaceLayerOwner = layerOwner && !eyeLayer;
        chunkBuildData->surfaceLayerQualification = std::move(surfaceQualification);
        if (task.directEntityInput) {
            if (post)
                preparedPostData.push_back(std::move(chunkBuildData));
            else
                preparedEntityData.push_back(std::move(chunkBuildData));
        } else if (post) {
            entityPostBuildDataBatch_->addData(std::move(chunkBuildData));
        } else {
            entityBuildDataBatch_->addData(std::move(chunkBuildData));
        }

        // mcvr::log::info("Entities") << "used texture ids: ";
        // for (auto id : textureIDs) { mcvr::log::info("Entities") << id << " "; }
        // mcvr::log::info("Entities") << std::endl;
    }

    if (mcvr::profile::enabled()) {
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.queue.pair-le64-calls", smallPairCalls, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.queue.pair-le64-vertices", smallPairVertices, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.queue.pair-gt64-calls", largePairCalls, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.queue.pair-gt64-vertices", largePairVertices, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.queue.pair-matches", pairMatches, 0);
    }
    if (!task.directEntityInput) return;

    mcvr::profile::Phases directProfile("entity.queue.direct-ranges");

    const size_t directBytes = directInputLayout.totalBytes();
    const auto storageLimit = physicalDevice->properties().limits.maxStorageBufferRange;
    const bool directWithinLimits =
        directInputLayout.withinLimit() && directBytes != 0 && directBytes <= static_cast<size_t>(storageLimit);
    if (directWithinLimits) {
        const uint32_t totalWords = mcvr::entityWordCount(directBytes);
        auto validateRanges = [totalWords](const auto &datas) {
            for (const auto &data : datas)
                for (const auto &raw : data->rawGeometry) {
                    if (!raw.pendingSource) continue;
                    if (raw.pendingSourceOffsetBytes % sizeof(uint32_t) != 0)
                        throw std::length_error("Entity direct source offset is not word aligned");
                    const uint32_t first = mcvr::entityWordCount(raw.pendingSourceOffsetBytes);
                    if (first > totalWords || raw.wordCount > totalWords - first)
                        throw std::length_error("Entity direct source range exceeds its input buffer");
                }
        };
        validateRanges(preparedEntityData);
        validateRanges(preparedPostData);
    }

    std::shared_ptr<mcvr::EntityDirectInputBatch> directInput;
    directProfile.next("entity.queue.direct-allocation");
    if (directWithinLimits && mcvr::EntityDirectInputBudget::tryReserve(directBytes)) {
        try {
            directInput = std::make_shared<mcvr::EntityDirectInputBatch>();
            directInput->byteCount = directBytes;
            directInput->ownsBudgetReservation = true;
        } catch (...) {
            mcvr::EntityDirectInputBudget::release(directBytes);
            throw;
        }
        directInput->buffer =
            vk::DeviceLocalBuffer::create(vma, device, false, directBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        directInput->buffer->writeToStagingBuffer([&](void *mapped, size_t mappedBytes) {
            mcvr::profile::Scope copyProfile("entity.queue.direct-copy");
            if (mappedBytes < directBytes)
                throw std::length_error("Entity direct input staging is shorter than its source data");
            auto copySources = [&](const auto &datas) {
                for (const auto &data : datas)
                    for (const auto &raw : data->rawGeometry) {
                        if (!raw.pendingSource) continue;
                        if (raw.pendingSourceOffsetBytes > directBytes ||
                            raw.pendingSourceBytes > directBytes - raw.pendingSourceOffsetBytes)
                            throw std::length_error("Entity direct source exceeds its input allocation");
                        std::memcpy(static_cast<std::byte *>(mapped) + raw.pendingSourceOffsetBytes, raw.pendingSource,
                                    raw.pendingSourceBytes);
                    }
            };
            copySources(preparedEntityData);
            copySources(preparedPostData);
        });

        directProfile.next("entity.queue.direct-bind");
        auto bindSources = [&](auto &datas) {
            for (auto &data : datas)
                for (auto &raw : data->rawGeometry) {
                    if (!raw.pendingSource) continue;
                    raw.sourceWordOffset = mcvr::entityWordCount(raw.pendingSourceOffsetBytes);
                    raw.directInput = directInput;
                    raw.pendingSource = nullptr;
                    raw.pendingSourceBytes = 0;
                    raw.pendingSourceOffsetBytes = 0;
                }
        };
        bindSources(preparedEntityData);
        bindSources(preparedPostData);
    } else {
        directProfile.next("entity.queue.legacy-copy");
        auto copyToLegacyOwner = [&](auto &datas) {
            for (auto &data : datas)
                for (auto &raw : data->rawGeometry) {
                    if (!raw.pendingSource) continue;
                    raw.words = std::make_unique_for_overwrite<uint32_t[]>(raw.wordCount);
                    std::memcpy(raw.words.get(), raw.pendingSource, raw.pendingSourceBytes);
                    raw.pendingSource = nullptr;
                    raw.pendingSourceBytes = 0;
                    raw.pendingSourceOffsetBytes = 0;
                }
        };
        copyToLegacyOwner(preparedEntityData);
        copyToLegacyOwner(preparedPostData);
    }

    directProfile.next("entity.queue.publish");
    auto reserveAppend = [](auto &destination, size_t count) {
        if (count > destination.max_size() - destination.size())
            throw std::length_error("Entity submission batch size overflow");
        destination.reserve(destination.size() + count);
    };
    auto &entityDestination = entityBuildDataBatch_->datas;
    auto &postDestination = entityPostBuildDataBatch_->datas;
    reserveAppend(entityDestination, preparedEntityData.size());
    reserveAppend(postDestination, preparedPostData.size());
    entityDestination.insert(entityDestination.end(), std::make_move_iterator(preparedEntityData.begin()),
                             std::make_move_iterator(preparedEntityData.end()));
    postDestination.insert(postDestination.end(), std::make_move_iterator(preparedPostData.begin()),
                           std::make_move_iterator(preparedPostData.end()));

    if (mcvr::profile::enabled() && directBytes) {
        if (directInput) {
            mcvr::profile::emit(mcvr::profile::frame, 5, "entity.source.direct-upload", 1, directBytes);
        } else {
            mcvr::profile::emit(mcvr::profile::frame, 5, "entity.source.direct-budget-fallback", 1, directBytes);
        }
    }
}

void Entities::build() {
    mcvr::profile::Scope auditProfile("entity-build-dispatch");
    Renderer::instance().framework()->safeAcquireCurrentContext();
    auto framework = Renderer::instance().framework();
    auto vma = framework->vma();
    auto device = framework->device();
    auto physicalDevice = framework->physicalDevice();

    entityBuildDataBatch_->build(gpuEntityConversionEnabled() ? &conversionPipeline_ : nullptr,
                                 SceneRecordingScope::active() ? nullptr : cpuPreparation_.get());

    for (const auto &data : entityBuildDataBatch_->datas) {
        if (data != nullptr && data->auditId != 0) {
            worldMeshAuditStates_[data->auditId] = WorldMeshAuditState::Built;
        }
    }

    if (auto conversion = entityBuildDataBatch_->gpuConversion) {
        auto buffers = Renderer::instance().buffers();
        for (const auto &group : conversion->sourceGroups) {
            buffers->queueImportantWorldUpload(group.input);
            buffers->queueImportantWorldUpload(group.jobs);
        }
    } else {
        Renderer::instance().buffers()->queueImportantWorldUpload(entityBuildDataBatch_->indexBuffer);
        Renderer::instance().buffers()->queueImportantWorldUpload(entityBuildDataBatch_->positionBuffer);
        Renderer::instance().buffers()->queueImportantWorldUpload(entityBuildDataBatch_->materialBuffer);
    }
    blasBatchBuilder_ = entityBuildDataBatch_->blasBatchBuilder;

    entityBatch_ = EntityBatch::create(entityBuildDataBatch_);
    if (cloudBuildData_) {
        cloudBuildData_->build();
        auto buffers = Renderer::instance().buffers();
        buffers->queueImportantWorldUpload(cloudBuildData_->indexBuffer);
        buffers->queueImportantWorldUpload(cloudBuildData_->positionBuffer);
        buffers->queueImportantWorldUpload(cloudBuildData_->materialBuffer);
        cloudBlasBuilder_ = cloudBuildData_->blasBatchBuilder;
        auto batch = EntityBatch::create(cloudBuildData_);
        cloudCache_.store(cloudRevision_, batch);
        queuedClouds_.insert(queuedClouds_.end(), batch->entities.begin(), batch->entities.end());
    }
    entityBatch_->entities.insert(entityBatch_->entities.end(), queuedClouds_.begin(), queuedClouds_.end());
    rigidBatch_ = RigidInstanceBatch::create();
    rigidBatch_->models = rigidUsed_;
    rigidBatch_->instances = rigidInstances_;
    rigidBatch_->worldToken = activeWorldToken_;
    rigidBatch_->frameToken = activeFrameToken_;
    rigidBatch_->resourceGeneration = activeResourceGeneration_;
    if (mcvr::profile::enabled()) {
        uint64_t vertices = 0;
        for (const auto &instance : rigidInstances_) vertices += instance.model->vertexCount;
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.rigid.instances", rigidInstances_.size(), 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.rigid.referenced-vertices", vertices, 0);
        mcvr::profile::emit(mcvr::profile::frame, 5, "entity.rigid.resident-models", rigidModels_.size(), 0);
    }
    entityPostBatch_ = EntityPostBatch::create(entityPostBuildDataBatch_);

    for (auto entity : entityPostBatch_->entities) {
        for (int i = 0; i < entity->geometryCount; i++) {
            Renderer::instance().buffers()->queueImportantWorldUpload(entity->vertexBuffers[i],
                                                                      entity->indexBuffers[i]);
        }
    }
}

void Entities::releaseScene() {
    rigidModels_.clear();
    rigidGeometries_.clear();
    rigidModelBytes_ = 0;
    rigidInstances_.clear();
    rigidUsed_.clear();
    rigidBatch_ = nullptr;
    cloudCache_.clear();
    cloudBuildData_.reset();
    cloudBlasBuilder_.reset();
    queuedClouds_.clear();
    cloudCapturing_ = cloudBuildRecorded_ = false;
    entityBatch_ = nullptr;
    entityPostBatch_ = nullptr;
    blasBatchBuilder_ = nullptr;
    entityBuildDataBatch_ = EntityBuildDataBatch::create();
    entityPostBuildDataBatch_ = EntityPostBuildDataBatch::create();
}

void Entities::close() {
    cpuPreparation_.reset();
    rigidModels_.clear();
    rigidGeometries_.clear();
    rigidModelBytes_ = 0;
    rigidInstances_.clear();
    rigidUsed_.clear();
    rigidBatch_ = nullptr;
    conversionPipeline_.reset();
    cloudCache_.clear();
    cloudBuildData_.reset();
    cloudBlasBuilder_.reset();
    queuedClouds_.clear();
    cloudCapturing_ = cloudBuildRecorded_ = false;
    for (auto &[auditId, status] : worldMeshAuditStates_) {
        if (status < WorldMeshAuditState::Built) status = WorldMeshAuditState::Stale;
    }
    worldMeshFrameOpen_ = false;
    worldMeshStages_.clear();
    activeWorldToken_ = 0;
    activeFrameToken_ = 0;
    activeResourceGeneration_ = 0;
    entityBatch_ = nullptr;
    entityPostBatch_ = nullptr;
    entityBuildDataBatch_ = nullptr;
    entityPostBuildDataBatch_ = nullptr;
    blasBatchBuilder_ = nullptr;
}

std::shared_ptr<EntityBatch> Entities::entityBatch() {
    Renderer::instance().framework()->safeAcquireCurrentContext();

    if (entityBatch_)
        return entityBatch_;
    else
        return nullptr;
}

std::shared_ptr<EntityPostBatch> Entities::entityPostBatch() {
    Renderer::instance().framework()->safeAcquireCurrentContext();

    if (entityPostBatch_)
        return entityPostBatch_;
    else
        return nullptr;
}

std::shared_ptr<vk::BLASBatchBuilder> Entities::blasBatchBuilder() {
    return blasBatchBuilder_;
}
