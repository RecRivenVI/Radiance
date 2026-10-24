#include "core/diagnostics/frame_profile.hpp"
#include "core/render/chunk_trace.hpp"
#include "core/render/material_faces.hpp"
#include "core/render/geometry_topology.hpp"
#include "core/diagnostics/geometry_audit.hpp"
#include "core/render/chunk_scheduling.hpp"
#include "core/render/chunk_geometry_layout.hpp"
#include "core/render/chunks.hpp"
#include "core/render/chunk_build_submission.hpp"
#include "core/failure_state.hpp"

#include "core/diagnostics/alloc_trace.hpp"
#include "core/diagnostics/device_loss_trace.hpp"
#include "core/render/buffers.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"
#include "core/vulkan/vertex.hpp"

#include <algorithm>
#include <array>
#include <cassert>
#include <cmath>
#include <cstddef>
#include <cstring>
#include <cstdlib>
#include <limits>
#include <map>
#include <sstream>
#include <stdexcept>
#include <string_view>

namespace {
struct ChunkPerformanceStats {
    std::atomic<uint64_t> buildInputCount{0};
    std::atomic<uint64_t> buildInputCpuNs{0};
    std::atomic<uint64_t> batchBuildCount{0};
    std::atomic<uint64_t> batchBuildCpuNs{0};
    std::atomic<uint64_t> batchSubmitCount{0};
    std::atomic<uint64_t> batchSubmitCpuNs{0};
    std::atomic<uint64_t> batchCompleteCount{0};
    std::atomic<uint64_t> batchQueueGpuWallNs{0};
};

ChunkPerformanceStats chunkPerformance;
const bool chunkPerformanceEnabled = std::getenv("RADIANCE_CHUNK_PERF") != nullptr;

uint64_t elapsedNs(std::chrono::steady_clock::time_point start) {
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count());
}

// Diagnostics only (RADIANCE_CHUNK_CENSUS=1): weak references to every device buffer a chunk batch
// creates, with the section and version it was built for, so the census can find memory that
// outlives both its published slot and its build batch.
struct TrackedChunkBuffer {
    std::weak_ptr<vk::DeviceLocalBuffer> buffer;
    int64_t id, version;
};
const bool chunkCensusEnabled = [] {
    const char *value = std::getenv("RADIANCE_CHUNK_CENSUS");
    return value && std::string_view(value) == "1";
}();
std::mutex trackedChunkBuffersMutex;
std::vector<TrackedChunkBuffer> trackedChunkBuffers;

void trackChunkBuffer(const std::shared_ptr<vk::DeviceLocalBuffer> &buffer, int64_t id, int64_t version) {
    if (!chunkCensusEnabled || !buffer) return;
    std::lock_guard lock(trackedChunkBuffersMutex);
    trackedChunkBuffers.push_back({buffer, id, version});
}
} // namespace

struct LightData {
    glm::vec4 p0Area;
    glm::vec4 p1;
    glm::vec4 p2;
    glm::vec4 p3;
    glm::vec4 normal;
    glm::vec4 radiance;
    glm::vec4 sourceIDData;
};
static_assert(sizeof(LightData) == sizeof(glm::vec4) * 7);

static void buildChunkPackedVertices(const std::vector<std::vector<vk::VertexFormat::PBRVertex>> &vertices,
                                     const std::vector<std::vector<uint32_t>> &indices,
                                     std::vector<vk::VertexFormat::PositionVertex> &packedPositions,
                                     std::vector<vk::VertexFormat::MaterialVertex> &packedMaterials,
                                     std::vector<uint32_t> &packedIndices,
                                     const std::vector<std::vector<uint32_t>> &topologyFlags) {
    mcvr::profile::Scope auditProfile("chunk-pack-vertices");
    for (int i = 0; i < static_cast<int>(vertices.size()); i++) {
        const auto &geometryVertices = vertices[i];
        const auto &geometryIndices = indices[i];

        packedIndices.insert(packedIndices.end(), geometryIndices.begin(), geometryIndices.end());

        for (size_t v = 0; v < geometryVertices.size(); ++v) {
            auto position = vk::Vertex::makePositionVertex(geometryVertices[v]);
            if (topologyFlags.size() > static_cast<size_t>(i) && !topologyFlags[i].empty())
                position.pad0 = topologyFlags[i].at(v);
            packedPositions.push_back(position);
            packedMaterials.push_back(vk::Vertex::makeMaterialVertex(geometryVertices[v]));
        }
    }
}

static uint64_t hashCombine64(uint64_t seed, uint64_t value) {
    seed ^= value + 0x9e3779b97f4a7c15ull + (seed << 6) + (seed >> 2);
    return seed;
}

static float packUintBits(uint32_t value) {
    float packed = 0.0f;
    std::memcpy(&packed, &value, sizeof(uint32_t));
    return packed;
}

static float triangleArea(const glm::vec3 &a, const glm::vec3 &b, const glm::vec3 &c) {
    return 0.5f * glm::length(glm::cross(b - a, c - a));
}

static float cross2D(const glm::vec2 &a, const glm::vec2 &b) {
    return a.x * b.y - a.y * b.x;
}

static void clipUvPolygonAgainstEdge(
    const std::vector<glm::vec2> &input, std::vector<glm::vec2> &output, int axis, float value, bool keepGreater) {
    output.clear();
    if (input.empty()) { return; }

    auto coordinate = [&](const glm::vec2 &uv) -> float { return axis == 0 ? uv.x : uv.y; };
    auto inside = [&](const glm::vec2 &uv) -> bool {
        return keepGreater ? coordinate(uv) >= value : coordinate(uv) <= value;
    };
    auto intersect = [&](const glm::vec2 &a, const glm::vec2 &b) -> glm::vec2 {
        float delta = coordinate(b) - coordinate(a);
        if (std::abs(delta) <= 1e-8f) { return a; }
        float t = (value - coordinate(a)) / delta;
        return glm::mix(a, b, std::clamp(t, 0.0f, 1.0f));
    };

    glm::vec2 previous = input.back();
    bool previousInside = inside(previous);
    for (const glm::vec2 &current : input) {
        bool currentInside = inside(current);
        if (currentInside != previousInside) { output.push_back(intersect(previous, current)); }
        if (currentInside) { output.push_back(current); }
        previous = current;
        previousInside = currentInside;
    }
}

static std::vector<glm::vec2>
clipUvTriangleToRect(const std::array<glm::vec2, 3> &triangle, const glm::vec2 &rectMin, const glm::vec2 &rectMax) {
    std::vector<glm::vec2> polygon(triangle.begin(), triangle.end());
    std::vector<glm::vec2> scratch;

    clipUvPolygonAgainstEdge(polygon, scratch, 0, rectMin.x, true);
    polygon.swap(scratch);
    clipUvPolygonAgainstEdge(polygon, scratch, 0, rectMax.x, false);
    polygon.swap(scratch);
    clipUvPolygonAgainstEdge(polygon, scratch, 1, rectMin.y, true);
    polygon.swap(scratch);
    clipUvPolygonAgainstEdge(polygon, scratch, 1, rectMax.y, false);
    polygon.swap(scratch);
    return polygon;
}

static void releaseChunkBuildDataStaging(const std::shared_ptr<ChunkBuildData> &chunkBuildData) {
    if (chunkBuildData->indexBuffer != nullptr) { chunkBuildData->indexBuffer->releaseStaging(); }
    if (chunkBuildData->positionBuffer != nullptr) { chunkBuildData->positionBuffer->releaseStaging(); }
    if (chunkBuildData->materialBuffer != nullptr) { chunkBuildData->materialBuffer->releaseStaging(); }
    if (chunkBuildData->lightBuffer != nullptr) { chunkBuildData->lightBuffer->releaseStaging(); }
}

static bool
barycentricFromUv(const glm::vec2 &uv, const std::array<glm::vec2, 3> &triangleUv, glm::vec3 &outBarycentric) {
    glm::vec2 e0 = triangleUv[1] - triangleUv[0];
    glm::vec2 e1 = triangleUv[2] - triangleUv[0];
    float denominator = cross2D(e0, e1);
    if (std::abs(denominator) <= 1e-8f) { return false; }

    glm::vec2 relative = uv - triangleUv[0];
    float b1 = cross2D(relative, e1) / denominator;
    float b2 = cross2D(e0, relative) / denominator;
    outBarycentric = glm::vec3(1.0f - b1 - b2, b1, b2);
    return true;
}

static bool uvToTrianglePosition(const glm::vec2 &uv,
                                 const std::array<glm::vec2, 3> &triangleUv,
                                 const std::array<glm::vec3, 3> &trianglePos,
                                 glm::vec3 &outPosition) {
    glm::vec3 barycentric;
    if (!barycentricFromUv(uv, triangleUv, barycentric)) { return false; }

    outPosition = trianglePos[0] * barycentric.x + trianglePos[1] * barycentric.y + trianglePos[2] * barycentric.z;
    return true;
}

static bool uvToTriangleTint(const glm::vec2 &uv,
                             const std::array<glm::vec2, 3> &triangleUv,
                             const std::array<glm::vec3, 3> &triangleTint,
                             glm::vec3 &outTint) {
    glm::vec3 barycentric;
    if (!barycentricFromUv(uv, triangleUv, barycentric)) { return false; }

    outTint = triangleTint[0] * barycentric.x + triangleTint[1] * barycentric.y + triangleTint[2] * barycentric.z;
    return true;
}

static glm::vec3 emissionVertexTint(const vk::VertexFormat::PBRVertex &vertex) {
    if (vertex.useColorLayer == 0) { return glm::vec3(1.0f); }

    return glm::clamp(glm::vec3(vertex.colorLayer), glm::vec3(0.0f), glm::vec3(1.0f));
}

static uint64_t
buildChunkLightStableID(int64_t chunkId, uint32_t geometryIndex, uint32_t quadIndex, uint64_t cellStableKey) {
    uint64_t seed = 0xcbf29ce484222325ull;
    seed = hashCombine64(seed, static_cast<uint64_t>(chunkId));
    seed = hashCombine64(seed, static_cast<uint64_t>(geometryIndex));
    seed = hashCombine64(seed, static_cast<uint64_t>(quadIndex));
    seed = hashCombine64(seed, cellStableKey);
    return seed;
}

static ChunkPackedData makeChunkPackedData(
    int32_t x, int32_t y, int32_t z, uint32_t geometryCount, uint32_t lightCount, VkDeviceAddress lightBufferAddress) {
    return {
        .x = x,
        .y = y,
        .z = z,
        .geometryCount = geometryCount,
        .lightCount = lightCount,
        .lightBufferAddress = lightBufferAddress,
    };
}

static void storeChunkPackedData(std::vector<ChunkPackedData> &chunkPackedData,
                                 int64_t id,
                                 int32_t x,
                                 int32_t y,
                                 int32_t z,
                                 uint32_t geometryCount,
                                 uint32_t lightCount,
                                 VkDeviceAddress lightBufferAddress) {
    if (!Renderer::options.collectChunkEmission) { return; }

    chunkPackedData[id] = makeChunkPackedData(x, y, z, geometryCount, lightCount, lightBufferAddress);
}

static LightData packLight(const LightInfo &light, const glm::vec3 &chunkOrigin) {
    LightData gpuLight{};
    glm::vec3 p0 = light.p0 + chunkOrigin;
    glm::vec3 p1 = light.p1 + chunkOrigin;
    glm::vec3 p2 = light.p2 + chunkOrigin;
    glm::vec3 p3 = light.p3 + chunkOrigin;

    gpuLight.p0Area = glm::vec4(p0, light.area);
    // Spare lanes retain exact source UVs for water-emitter exclusion; the packed
    // record stays seven vec4s. Texture IDs occupy bits 8..19, outside face bits.
    gpuLight.p1 = glm::vec4(p1, light.uv0.x);
    gpuLight.p2 = glm::vec4(p2, light.uv0.y);
    gpuLight.p3 = glm::vec4(p3, light.uv1.x);
    gpuLight.normal = glm::vec4(light.normal, packUintBits(light.sourceFaceFlags | (light.textureID << 8u)));
    gpuLight.radiance = glm::vec4(light.radiance, light.uv2.y);
    uint32_t stableIDLow = static_cast<uint32_t>(light.stableID & 0xffffffffull);
    uint32_t stableIDHigh = static_cast<uint32_t>((light.stableID >> 32u) & 0xffffffffull);
    gpuLight.sourceIDData = glm::vec4(packUintBits(stableIDLow), packUintBits(stableIDHigh), light.uv1.y, light.uv2.x);
    return gpuLight;
}

void ChunkBuildData::buildLightBuffer(const std::shared_ptr<vk::VMA> &vma,
                                      const std::shared_ptr<vk::Device> &device,
                                      bool persistStaging) {
    lightBuffer = nullptr;
    lightCount = 0;

    if (!collectChunkEmission) {
        (void)vma;
        (void)device;
        return;
    }

    if (lightInfos.empty()) { return; }

    std::vector<LightData> gpuLights;
    gpuLights.reserve(lightInfos.size());

    glm::vec3 chunkOrigin(static_cast<float>(x), static_cast<float>(y), static_cast<float>(z));
    for (const auto &light : lightInfos) {
        float maxRadiance = std::max({light.radiance.r, light.radiance.g, light.radiance.b});
        if (light.area <= 1e-6f || maxRadiance <= 1e-6f) { continue; }

        gpuLights.push_back(packLight(light, chunkOrigin));
    }

    if (gpuLights.empty()) { return; }

    lightCount = static_cast<uint32_t>(gpuLights.size());

    size_t lightBytes = static_cast<size_t>(lightCount) * sizeof(LightData);
    lightBuffer =
        vk::DeviceLocalBuffer::create(vma, device, persistStaging, lightBytes,
                                      VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, 0,
                                      VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, 16);
#ifdef DEBUG
    if ((lightBuffer->bufferAddress() & (16 - 1)) != 0) {
        throw std::runtime_error("Chunk light buffer address is not 16-byte aligned");
    }
#endif
    lightBuffer->uploadToStagingBuffer(gpuLights.data(), lightBytes, 0);
}

ChunkBuildData::ChunkBuildData(int64_t id,
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
                               std::vector<std::vector<uint32_t>> &&indices)
    : id(id),
      x(x),
      y(y),
      z(z),
      version(version),
      collectChunkEmission(collectChunkEmission),
      allVertexCount(allVertexCount),
      allIndexCount(allIndexCount),
      geometryCount(geometryCount),
      geometryTypes(std::move(geometryTypes)),
      geometryGroupNames(std::move(geometryGroupNames)),
      geometryMaterialFlags(std::move(geometryMaterialFlags)),
      vertices(std::move(vertices)),
      indices(std::move(indices)),
      indexBufferAddresses(),
      positionBufferAddresses(),
      materialBufferAddresses(),
      indexBuffer(nullptr),
      positionBuffer(nullptr),
      materialBuffer(nullptr),
      blas(nullptr),
      blasBuilder(nullptr),
      lightInfos(),
      lightBuffer(nullptr),
      lightCount(0) {
    mcvr::profile::Scope topologyProfile("chunk-input-topology");
    topologyVertexFlags.resize(this->vertices.size());
    this->allVertexCount = 0;
    this->allIndexCount = 0;
    for (size_t g = 0; g < this->vertices.size(); ++g) {
        auto result = mcvr::geometry::prepare(this->vertices[g], this->indices[g], true);
        topologyVertexFlags[g] = std::move(result.vertexFlags);
        if (result.report.pairedQuads || result.report.pairedTriangles)
            this->geometryMaterialFlags[g] |= mcvr::geometry::pairedGeometryBit;
        mcvr::audit::geometry("chunk/" + this->geometryGroupNames[g], id, g, this->vertices[g], this->indices[g],
                              topologyVertexFlags[g], this->geometryMaterialFlags[g], result.report);
        this->allVertexCount += static_cast<uint32_t>(this->vertices[g].size());
        this->allIndexCount += static_cast<uint32_t>(this->indices[g].size());
    }
}

void ChunkBuildData::buildLightInfos(const Emission &emission) {
    lightInfos.clear();

    if (!collectChunkEmission) {
        (void)emission;
        return;
    }

    for (uint32_t geometryIndex = 0; geometryIndex < vertices.size(); geometryIndex++) {
        const uint32_t authoredFaces = geometryMaterialFlags.at(geometryIndex) & mcvr::faces::mask;
        auto &geometryVertices = vertices[geometryIndex];
        if (geometryVertices.size() < 4) { continue; }

        uint32_t quadIndex = 0;
        for (size_t vertexIndex = 0; vertexIndex + 3 < geometryVertices.size(); vertexIndex += 4, quadIndex++) {
            const auto &v0 = geometryVertices[vertexIndex + 0];
            const auto &v1 = geometryVertices[vertexIndex + 1];
            const auto &v2 = geometryVertices[vertexIndex + 2];
            const auto &v3 = geometryVertices[vertexIndex + 3];
            const size_t firstIndex = static_cast<size_t>(quadIndex) * 6;
            if (firstIndex + 5 >= indices[geometryIndex].size()) throw std::logic_error("Emission quad topology range");
            const uint32_t leading = indices[geometryIndex][firstIndex];
            const uint32_t policy =
                topologyVertexFlags[geometryIndex].empty() ? 0 : topologyVertexFlags[geometryIndex].at(leading);
            uint32_t sourceFaces = authoredFaces;
            if (mcvr::geometry::twoSided()) {
                if ((policy & mcvr::topology::paired) == 0)
                    sourceFaces = authoredFaces & mcvr::faces::clockwise;
                else if (mcvr::faces::effective(sourceFaces) == 0 ||
                         mcvr::faces::effective(sourceFaces) == (mcvr::faces::back | mcvr::faces::front))
                    sourceFaces = (sourceFaces & mcvr::faces::clockwise) | mcvr::faces::back;
            }
            if (mcvr::faces::effective(sourceFaces) == (mcvr::faces::back | mcvr::faces::front)) continue;

            glm::vec3 tint0 = emissionVertexTint(v0);
            glm::vec3 tint1 = emissionVertexTint(v1);
            glm::vec3 tint2 = emissionVertexTint(v2);
            glm::vec3 tint3 = emissionVertexTint(v3);

            if (v0.useTexture == 0 || v1.useTexture == 0 || v2.useTexture == 0 || v3.useTexture == 0) { continue; }

            std::array<glm::vec2, 4> quadUv = {v0.textureUV, v1.textureUV, v2.textureUV, v3.textureUV};
            glm::vec2 uvMin(std::numeric_limits<float>::max());
            glm::vec2 uvMax(std::numeric_limits<float>::lowest());
            for (glm::vec2 uv : quadUv) {
                uvMin = glm::min(uvMin, uv);
                uvMax = glm::max(uvMax, uv);
            }

            glm::vec2 uvSize = uvMax - uvMin;
            if (uvSize.x <= 1e-6f || uvSize.y <= 1e-6f) { continue; }

            glm::vec3 triNormal0 = glm::cross(v1.pos - v0.pos, v2.pos - v0.pos);
            glm::vec3 triNormal1 = glm::cross(v2.pos - v0.pos, v3.pos - v0.pos);
            glm::vec3 baseNormal = triNormal0 + triNormal1;
            float baseNormalLength = glm::length(baseNormal);
            if (baseNormalLength <= 1e-6f) { continue; }

            glm::vec3 normal = baseNormal / baseNormalLength;
            float quadArea = triangleArea(v0.pos, v1.pos, v2.pos) + triangleArea(v0.pos, v2.pos, v3.pos);
            if (quadArea <= 1e-6f) { continue; }

            thread_local std::vector<std::shared_ptr<const EmissionCell>> candidateCells;
            emission.collectCells(v0.textureID, uvMin, uvMax, candidateCells);
            for (const auto &candidateCell : candidateCells) {
                if (candidateCell == nullptr) { continue; }

                const auto &cell = *candidateCell;
                glm::vec2 overlapMin = glm::max(uvMin, cell.uvMin);
                glm::vec2 overlapMax = glm::min(uvMax, cell.uvMax);
                glm::vec2 overlapSize = overlapMax - overlapMin;
                if (overlapSize.x <= 1e-6f || overlapSize.y <= 1e-6f || cell.avgEmission <= 0.0f) { continue; }

                uint64_t baseStableID = buildChunkLightStableID(id, geometryIndex, quadIndex, cell.stableKey);
                std::array<std::array<glm::vec2, 3>, 2> triangleUv = {{
                    {v0.textureUV, v1.textureUV, v2.textureUV},
                    {v0.textureUV, v2.textureUV, v3.textureUV},
                }};
                std::array<std::array<glm::vec3, 3>, 2> trianglePos = {{
                    {v0.pos, v1.pos, v2.pos},
                    {v0.pos, v2.pos, v3.pos},
                }};
                std::array<std::array<glm::vec3, 3>, 2> triangleTint = {{
                    {tint0, tint1, tint2},
                    {tint0, tint2, tint3},
                }};

                for (size_t tri = 0; tri < 2; ++tri)
                    for (size_t corner = 0; corner < 3; ++corner) {
                        const auto &vertex = geometryVertices.at(indices[geometryIndex][firstIndex + tri * 3 + corner]);
                        triangleUv[tri][corner] = vertex.textureUV;
                        trianglePos[tri][corner] = vertex.pos;
                        triangleTint[tri][corner] = emissionVertexTint(vertex);
                    }
                baseStableID = hashCombine64(baseStableID, policy);
                uint64_t trianglePieceIndex = 0;
                for (int triangleIndex = 0; triangleIndex < 2; ++triangleIndex) {
                    std::vector<glm::vec2> clippedPolygon =
                        clipUvTriangleToRect(triangleUv[triangleIndex], overlapMin, overlapMax);
                    if (clippedPolygon.size() < 3) { continue; }

                    glm::vec3 fanOrigin;
                    glm::vec3 fanOriginTint;
                    if (!uvToTrianglePosition(clippedPolygon[0], triangleUv[triangleIndex], trianglePos[triangleIndex],
                                              fanOrigin) ||
                        !uvToTriangleTint(clippedPolygon[0], triangleUv[triangleIndex], triangleTint[triangleIndex],
                                          fanOriginTint)) {
                        continue;
                    }

                    for (size_t polygonIndex = 1; polygonIndex + 1 < clippedPolygon.size(); ++polygonIndex) {
                        glm::vec3 p1;
                        glm::vec3 p2;
                        glm::vec3 p1Tint;
                        glm::vec3 p2Tint;
                        if (!uvToTrianglePosition(clippedPolygon[polygonIndex], triangleUv[triangleIndex],
                                                  trianglePos[triangleIndex], p1) ||
                            !uvToTriangleTint(clippedPolygon[polygonIndex], triangleUv[triangleIndex],
                                              triangleTint[triangleIndex], p1Tint) ||
                            !uvToTrianglePosition(clippedPolygon[polygonIndex + 1], triangleUv[triangleIndex],
                                                  trianglePos[triangleIndex], p2) ||
                            !uvToTriangleTint(clippedPolygon[polygonIndex + 1], triangleUv[triangleIndex],
                                              triangleTint[triangleIndex], p2Tint)) {
                            continue;
                        }

                        float triangleLightArea = triangleArea(fanOrigin, p1, p2);
                        if (triangleLightArea <= 1e-6f) { continue; }

                        glm::vec3 lightNormal = glm::cross(p1 - fanOrigin, p2 - fanOrigin);
                        float lightNormalLength = glm::length(lightNormal);
                        lightNormal = lightNormalLength > 1e-6f ? lightNormal / lightNormalLength : normal;
                        glm::vec3 avgTint = glm::max((fanOriginTint + p1Tint + p2Tint) / 3.0f, glm::vec3(0.0f));

                        LightInfo info{
                            .p0 = fanOrigin,
                            .p1 = p1,
                            .p2 = p2,
                            .p3 = p2,
                            .normal = lightNormal,
                            .radiance = cell.avgColor * avgTint * cell.avgEmission,
                            .area = triangleLightArea,
                            .textureID = v0.textureID,
                            .uv0 = clippedPolygon[0],
                            .uv1 = clippedPolygon[polygonIndex],
                            .uv2 = clippedPolygon[polygonIndex + 1],
                            .sourceFaceFlags = sourceFaces,
                            .stableID = hashCombine64(hashCombine64(baseStableID, sourceFaces), trianglePieceIndex++),
                        };
                        lightInfos.push_back(info);
                    }
                }
            }
        }
    }
}

ChunkBuildDataBatch::ChunkBuildDataBatch(std::vector<std::shared_ptr<ChunkBuildData>> &&batchData)
    : batchData(std::move(batchData)) {}

ChunkBuildDataBatch::ChunkBuildDataBatch(uint32_t maxBatchSize,
                                         std::set<int64_t> &queuedIndexSet,
                                         std::vector<std::shared_ptr<Chunk1>> &chunks,
                                         std::vector<std::shared_ptr<ChunkBuildData>> &chunkBuildDatas,
                                         glm::vec3 cameraPos,
                                         uint64_t inFlightBytes,
                                         uint64_t admissionSequence,
                                         uint64_t byteBudget) {
    auto currentTime = std::chrono::steady_clock::now();
    auto queuedChunkPos = [&](int64_t id) -> glm::vec3 {
        const auto &chunkBuildData = chunkBuildDatas[id];
        if (chunkBuildData != nullptr) {
            return {
                static_cast<float>(chunkBuildData->x),
                static_cast<float>(chunkBuildData->y),
                static_cast<float>(chunkBuildData->z),
            };
        }

        return {
            static_cast<float>(chunks[id]->x),
            static_cast<float>(chunks[id]->y),
            static_cast<float>(chunks[id]->z),
        };
    };
    auto key = [&](int64_t id) {
        const auto &data = chunkBuildDatas[id];
        return mcvr::chunkScheduling::Key{data->priority, data->queuedAt, glm::distance(cameraPos, queuedChunkPos(id))};
    };
    auto bytesOf = [&](int64_t id) {
        const auto &data = chunkBuildDatas[id];
        return uint64_t(data->allVertexCount) * sizeof(vk::VertexFormat::PBRVertex) +
               uint64_t(data->allIndexCount) * sizeof(uint32_t) + uint64_t(data->lightInfos.size()) * sizeof(LightInfo);
    };
    auto selected = mcvr::chunkScheduling::selectBatch(queuedIndexSet, maxBatchSize, currentTime, key, bytesOf,
                                                       inFlightBytes, admissionSequence, byteBudget);
    for (auto id : selected) {
        queuedIndexSet.erase(id);
        batchData.push_back(std::exchange(chunkBuildDatas[id], nullptr));
    }
}

void ChunkBuildDataBatch::build() {
    mcvr::profile::Scope auditProfile("chunk-batch-build-record");
    // Own allocation tag so Audit reports separate chunk geometry from per-frame scene data.
    mcvr::diag::AllocTraceTagScope allocationTag("Chunk batch build");
    auto framework = Renderer::instance().framework();
    auto vma = framework->vma();
    auto device = framework->device();
    auto physicalDevice = framework->physicalDevice();

    for (auto &data : batchData) {
        if (data == nullptr) { continue; }
        data->buildLightBuffer(vma, device, true);
    }

    std::vector<std::vector<mcvr::chunkGeometry::Geometry>> geometryCounts(batchData.size());
    for (size_t chunkIndex = 0; chunkIndex < batchData.size(); chunkIndex++) {
        const auto &data = batchData[chunkIndex];
        if (data == nullptr) { continue; }
        auto &counts = geometryCounts[chunkIndex];
        counts.reserve(data->geometryCount);
        for (int i = 0; i < data->geometryCount; i++) {
            counts.push_back({data->vertices[i].size(), data->indices[i].size()});
        }
    }
    const auto layout =
        mcvr::chunkGeometry::plan(geometryCounts, {sizeof(vk::VertexFormat::PositionVertex),
                                                   sizeof(vk::VertexFormat::MaterialVertex), sizeof(uint32_t)});

    if (layout.geometryCount == 0) {
        for (const auto &data : batchData) {
            data->indexBufferAddresses.clear();
            data->positionBufferAddresses.clear();
            data->materialBufferAddresses.clear();
            data->indexBuffer = nullptr;
            data->positionBuffer = nullptr;
            data->materialBuffer = nullptr;
            data->blas = nullptr;
            data->blasBuilder = nullptr;
        }
        return;
    }

    std::vector<vk::VertexFormat::PositionVertex> packedPositions;
    std::vector<vk::VertexFormat::MaterialVertex> packedMaterials;
    std::vector<uint32_t> packedIndices;
    packedPositions.reserve(layout.vertexCount);
    packedMaterials.reserve(layout.vertexCount);
    packedIndices.reserve(layout.indexCount);
    for (const auto &data : batchData) {
        if (data == nullptr) { continue; }
        buildChunkPackedVertices(data->vertices, data->indices, packedPositions, packedMaterials, packedIndices,
                                 data->topologyVertexFlags);
    }
    if (packedPositions.size() != layout.vertexCount || packedIndices.size() != layout.indexCount) {
        throw std::logic_error("Packed chunk geometry does not match its upload layout");
    }

    // One staging allocation per batch, released with the batch after its fence completes.
    geometryStaging = vk::HostVisibleBuffer::create(
        vma, device, mcvr::chunkGeometry::allocationBytes(layout.stagingBytes), VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
    auto *staging = static_cast<std::byte *>(geometryStaging->mappedPtr());
    auto stage = [&](uint64_t offset, const void *source, uint64_t bytes) {
        if (bytes != 0) { std::memcpy(staging + offset, source, bytes); }
    };
    const uint64_t materialBase = layout.vertexCount * sizeof(vk::VertexFormat::PositionVertex);
    const uint64_t indexBase = materialBase + layout.vertexCount * sizeof(vk::VertexFormat::MaterialVertex);
    stage(0, packedPositions.data(), materialBase);
    stage(materialBase, packedMaterials.data(), indexBase - materialBase);
    stage(indexBase, packedIndices.data(), layout.stagingBytes - indexBase);
    geometryStaging->flush();

    blasBatchBuilder = vk::BLASBatchBuilder::create();
    std::vector<uint32_t> nonEmptyInstanceIndices;
    nonEmptyInstanceIndices.reserve(batchData.size());
    geometryUploads.reserve(batchData.size() * 3);

    for (size_t chunkIndex = 0; chunkIndex < batchData.size(); chunkIndex++) {
        const auto &data = batchData[chunkIndex];
        if (data == nullptr) { continue; }
        const auto &chunk = layout.chunks[chunkIndex];
        data->indexBufferAddresses.clear();
        data->positionBufferAddresses.clear();
        data->materialBufferAddresses.clear();
        data->indexBufferAddresses.reserve(data->geometryCount);
        data->positionBufferAddresses.reserve(data->geometryCount);
        data->materialBufferAddresses.reserve(data->geometryCount);

        if (data->geometryCount == 0) {
            data->indexBuffer = nullptr;
            data->positionBuffer = nullptr;
            data->materialBuffer = nullptr;
            data->blas = nullptr;
            data->blasBuilder = nullptr;
            continue;
        }

        auto createStream = [&](const mcvr::chunkGeometry::Copy &copy, VkBufferUsageFlags usage) {
            auto buffer = vk::DeviceLocalBuffer::create(vma, device, false,
                                                        mcvr::chunkGeometry::allocationBytes(copy.bytes), usage);
            if (copy.bytes != 0) { geometryUploads.push_back({buffer, copy.stagingOffset, copy.bytes}); }
            return buffer;
        };
        data->indexBuffer =
            createStream(chunk.indices, VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                                            VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
                                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        data->positionBuffer =
            createStream(chunk.positions, VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                                              VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
                                              VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        data->materialBuffer = createStream(chunk.materials, VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                                                                 VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);

        auto blasBuilder = blasBatchBuilder->defineBLASBuilder();
        auto blasGeometryBuilder = blasBuilder->beginGeometries();
        const mcvr::faces::ModelRules faceRules(data->geometryMaterialFlags);

        for (int i = 0; i < data->geometryCount; i++) {
            const VkDeviceAddress geometryIndexAddress =
                data->indexBuffer->bufferAddress() + chunk.indexOffsets[i] * sizeof(uint32_t);
            const VkDeviceAddress geometryPositionAddress =
                data->positionBuffer->bufferAddress() +
                chunk.vertexOffsets[i] * sizeof(vk::VertexFormat::PositionVertex);
            const VkDeviceAddress geometryMaterialAddress =
                data->materialBuffer->bufferAddress() +
                chunk.vertexOffsets[i] * sizeof(vk::VertexFormat::MaterialVertex);

            data->indexBufferAddresses.push_back(geometryIndexAddress);
            data->positionBufferAddresses.push_back(geometryPositionAddress);
            data->materialBufferAddresses.push_back(geometryMaterialAddress);

            blasGeometryBuilder->defineTriangleGeomrtry<vk::VertexFormat::PositionVertex>(
                geometryPositionAddress, data->vertices[i].size(), geometryIndexAddress, data->indices[i].size(),
                data->geometryTypes[i] == World::WORLD_SOLID && !faceRules.needsAnyHit(data->geometryMaterialFlags[i]));
        }

        blasGeometryBuilder->endGeometries();
        data->blasBuilder = blasBuilder->defineBuildProperty(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_BUILD_BIT_KHR)
                                ->querySizeInfo(device);
        nonEmptyInstanceIndices.push_back(static_cast<uint32_t>(chunkIndex));
    }

    // Each chunk's BLAS gets its own storage so it is freed with that chunk; scratch stays shared.
    auto blases =
        blasBatchBuilder->allocateBuffers(physicalDevice, device, vma, vk::BLASBatchBuilder::Storage::separate)
            ->build(device);
    for (int i = 0; i < nonEmptyInstanceIndices.size(); i++) {
        batchData[nonEmptyInstanceIndices[i]]->blas = blases[i];
    }
    if (chunkCensusEnabled) {
        for (const auto &data : batchData) {
            if (data == nullptr) continue;
            for (const auto &buffer :
                 {data->indexBuffer, data->positionBuffer, data->materialBuffer, data->lightBuffer})
                trackChunkBuffer(buffer, data->id, data->version);
            if (data->blas) trackChunkBuffer(data->blas->blasBuffer(), data->id, data->version);
        }
    }
    // After staging and AS descriptors exist, no GPU command references these
    // CPU arrays. Their lifetime must not grow with publication/frame retirement.
    for (const auto &data : batchData) {
        decltype(data->vertices){}.swap(data->vertices);
        decltype(data->indices){}.swap(data->indices);
        decltype(data->topologyVertexFlags){}.swap(data->topologyVertexFlags);
    }
}

ChunkBuildScheduler::ChunkBuildScheduler(std::set<int64_t> &queuedIndex,
                                         std::vector<std::shared_ptr<Chunk1>> &chunks,
                                         std::vector<std::shared_ptr<ChunkBuildData>> &chunkBuildDatas,
                                         std::recursive_mutex &mutex,
                                         std::vector<ChunkPackedData> &chunkPackedData,
                                         mcvr::ExternalChunkHandleTable &externalHandles,
                                         uint32_t chunkBuildingBatchSize,
                                         uint32_t chunkBuildingTotalBatches)
    : queuedIndex_(queuedIndex),
      chunks_(chunks),
      chunkBuildDatas_(chunkBuildDatas),
      mutex_(mutex),
      chunkPackedData_(chunkPackedData),
      externalHandles_(externalHandles),
      chunkBuildingBatchSize_(chunkBuildingBatchSize),
      chunkBuildingTotalBatches_(chunkBuildingTotalBatches) {
    auto framework = Renderer::instance().framework();
    auto device = framework->device();
    auto physicalDevice = framework->physicalDevice();
    useSecondaryQueue_ = physicalDevice->mainQueueIndex() == physicalDevice->secondaryQueueIndex();
    commandPool_ = vk::CommandPool::create(physicalDevice, device,
                                           useSecondaryQueue_ ? physicalDevice->secondaryQueueIndex() :
                                                                physicalDevice->mainQueueIndex());

    uint32_t numFences = chunkBuildingTotalBatches_;
    for (int i = 0; i < numFences; i++) {
        freeFences_.push(vk::Fence::create(device));
        freeCommandBuffers_.push(vk::CommandBuffer::create(device, commandPool_));
    }
}

ChunkBuildScheduler::~ChunkBuildScheduler() {
    stopWorker();
}

void ChunkBuildScheduler::stopWorker() {
    worker_.stop();
}

void ChunkBuildScheduler::notifyWork() {
    worker_.notify();
}

void ChunkBuildScheduler::pollBatches(bool wait) {
    auto framework = Renderer::instance().framework();
    auto device = framework->device();
    std::lock_guard pipeline(pipelineMutex_);
    auto fence = buildingFences_.begin();
    auto commands = buildingCommandBuffers_.begin();
    auto batch = buildingBatches_.begin();
    while (fence != buildingFences_.end()) {
        const VkResult result =
            vkWaitForFences(device->vkDevice(), 1, &(*fence)->vkFence(), true, wait ? UINT64_MAX : 0);
        if (result == VK_TIMEOUT || result == VK_NOT_READY) {
            ++fence;
            ++commands;
            ++batch;
            continue;
        }
        if (result != VK_SUCCESS) {
            framework->recordFailure(result, "vkWaitForFences(chunk worker)");
            return;
        }
        mcvr::diagnostics::device_loss::note("chunk-fence-completed", result, 0, 0,
                                             (uint64_t)(*commands)->vkCommandBuffer(), (uint64_t)(*fence)->vkFence());
        if (chunkPerformanceEnabled) {
            ++chunkPerformance.batchCompleteCount;
            if ((*batch)->submittedAt.time_since_epoch().count())
                chunkPerformance.batchQueueGpuWallNs += elapsedNs((*batch)->submittedAt);
        }
        for (const auto &data : (*batch)->batchData) {
            mcvr::chunkTrace::note("gpu-complete", data->id, data->version, 0, data->slotGeneration);
            data->completion = mcvr::scene::Completion::gpuComplete;
        }
        const VkResult reset = vkResetFences(device->vkDevice(), 1, &(*fence)->vkFence());
        if (reset != VK_SUCCESS) {
            framework->recordFailure(reset, "vkResetFences(chunk worker)");
            return;
        }
        const VkResult resetCommands = vkResetCommandBuffer((*commands)->vkCommandBuffer(), 0);
        if (resetCommands != VK_SUCCESS) {
            framework->recordFailure(resetCommands, "vkResetCommandBuffer(chunk worker)");
            return;
        }
        // The fence completed every transfer/build use. Keep only publication
        // owners, not the upload and BLAS scratch allocation for another frame.
        (*batch)->blasBatchBuilder.reset();
        (*batch)->geometryStaging.reset();
        (*batch)->geometryUploads.clear();
        completed_.push_back({*batch});
        // GPU completion releases recording capacity independently of the next
        // render frame. Publication data still holds section/byte credits.
        freeFences_.push(*fence);
        freeCommandBuffers_.push(*commands);
        fence = buildingFences_.erase(fence);
        commands = buildingCommandBuffers_.erase(commands);
        batch = buildingBatches_.erase(batch);
    }
}

void ChunkBuildScheduler::publishCompleted() {
    std::vector<Completed> ready;
    {
        std::lock_guard lock(pipelineMutex_);
        ready.swap(completed_);
    }
    if (ready.empty()) return;
    auto framework = Renderer::instance().framework();
    auto device = framework->device();
    std::vector<VkSemaphore> semaphores;
    for (const auto &entry : ready) {
        framework->frameResourceRetainer().retain(entry.batch);
        if (entry.batch->readySemaphore) semaphores.push_back(entry.batch->readySemaphore->vkSemaphore());
    }
    if (!semaphores.empty()) {
        // Fence polling proves completion to the CPU; this wait supplies the memory
        // dependency from the chunk queue to every later main-queue scene submission.
        std::vector<VkPipelineStageFlags> stages(semaphores.size(), VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        VkSubmitInfo wait{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        wait.waitSemaphoreCount = uint32_t(semaphores.size());
        wait.pWaitSemaphores = semaphores.data();
        wait.pWaitDstStageMask = stages.data();
        auto result = vkQueueSubmit(device->mainVkQueue(), 1, &wait, VK_NULL_HANDLE);
        if (result != VK_SUCCESS) {
            framework->recordFailure(result, "vkQueueSubmit(chunk publication wait)");
            return;
        }
    }
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    for (const auto &entry : ready) {
        for (const auto &data : entry.batch->batchData) {
            releaseChunkBuildDataStaging(data);
            if (externalHandles_.isCurrent(uint32_t(data->id), data->slotGeneration) &&
                chunks_[data->id]->enqueue(data))
                storeChunkPackedData(chunkPackedData_, data->id, data->x, data->y, data->z, data->geometryCount,
                                     data->lightCount, data->lightBuffer ? data->lightBuffer->bufferAddress() : 0);
        }
        selectedCount_.fetch_sub(entry.batch->batchData.size());
        selectedBytes_.fetch_sub(entry.batch->reservedInputBytes);
    }
    notifyWork();
}

void ChunkBuildScheduler::tryCheckBatchesFinish() {
    if (!useSecondaryQueue_) pollBatches(false);
    publishCompleted();
}

void ChunkBuildScheduler::waitAllBatchesFinish() {
    stopWorker();
    pollBatches(true);
    publishCompleted();
    // World leave/reload may clear the frame retainer immediately after this
    // boundary. Consume the publication waits before their semaphore owners retire.
    auto framework = Renderer::instance().framework();
    if (!framework->isDeviceLost()) framework->waitRenderQueueIdle();
}

void ChunkBuildScheduler::tryScheduleBatches(uint32_t maxBatchSize) {
    bool moved;
    {
        std::lock_guard lock(mutex_);
        auto next = glm::vec3(Renderer::instance().world()->getCameraPos());
        moved = next != camera_;
        camera_ = next;
    }
    if (!useSecondaryQueue_) {
        scheduleBatches(maxBatchSize);
        return;
    }
    auto framework = Renderer::instance().framework();
    if (!worker_.running())
        worker_.start(
            [this, framework](std::stop_token stop) {
                if (!framework->isRunning()) return mcvr::ChunkBuildWorker::State::stop;
                mcvr::failure::throwIfFatal();
                pollBatches(false);
                if (stop.stop_requested()) return mcvr::ChunkBuildWorker::State::stop;
                mcvr::failure::throwIfFatal();
                scheduleBatches(chunkBuildingBatchSize_);
                std::lock_guard lock(mutex_);
                return selectedCount_.load() || !queuedIndex_.empty() ? mcvr::ChunkBuildWorker::State::busy :
                                                                        mcvr::ChunkBuildWorker::State::idle;
            },
            [framework](std::exception_ptr failure) noexcept {
                try {
                    std::rethrow_exception(failure);
                } catch (const mcvr::failure::FatalError &error) {
                    mcvr::failure::record(error.kind(), error.result(), error.operation(), error.what());
                    framework->recordFailure(error.result(), "chunk worker");
                } catch (const std::exception &error) {
                    mcvr::failure::record(mcvr::failure::Kind::runtime, VK_ERROR_UNKNOWN, "chunk worker", error.what());
                    framework->recordFailure(VK_ERROR_UNKNOWN, "chunk worker");
                } catch (...) {
                    mcvr::failure::record(mcvr::failure::Kind::runtime, VK_ERROR_UNKNOWN, "chunk worker",
                                          "unknown exception");
                    framework->recordFailure(VK_ERROR_UNKNOWN, "chunk worker");
                }
            });
    if (moved) notifyWork();
}

void ChunkBuildScheduler::scheduleBatches(uint32_t maxBatchSize) {
    if (!Renderer::instance().framework()->isRunning()) return;
    uint32_t submittedThisFrame = 0;
    uint64_t submittedBytes = 0;
    const auto roundStart = mcvr::chunkScheduling::Clock::now();
    while (mcvr::chunkScheduling::withinBudget(submittedThisFrame, submittedBytes,
                                               mcvr::chunkScheduling::Clock::now() - roundStart)) {
        std::shared_ptr<vk::Fence> fence;
        std::shared_ptr<vk::CommandBuffer> commandBuffer;
        std::shared_ptr<ChunkBuildDataBatch> chunkBuildDataBatch;
        {
            std::unique_lock<std::recursive_mutex> lock(mutex_);
            std::unique_lock pipeline(pipelineMutex_);
            const auto remaining = mcvr::chunkScheduling::remainingSections(selectedCount_.load(), maxBatchSize);
            if (freeFences_.empty() || freeCommandBuffers_.empty() || remaining == 0) return;
            // Reject stale work before packing or reserving GPU storage. A slot generation and
            // its latest requested mesh revision must both still match.
            for (auto it = queuedIndex_.begin(); it != queuedIndex_.end();) {
                auto &data = chunkBuildDatas_[*it];
                if (!data || !externalHandles_.isCurrent(uint32_t(*it), data->slotGeneration) ||
                    data->version != chunks_[*it]->desiredVersion) {
                    data = nullptr;
                    it = queuedIndex_.erase(it);
                } else
                    ++it;
            }
            if (queuedIndex_.empty()) return;
            bool interactive = false;
            auto oldest = mcvr::chunkScheduling::Clock::now();
            for (auto id : queuedIndex_) {
                interactive |= chunkBuildDatas_[id]->priority > 0;
                oldest = std::min(oldest, chunkBuildDatas_[id]->queuedAt);
            }
            if (!mcvr::chunkScheduling::ready(interactive, uint32_t(queuedIndex_.size()), maxBatchSize,
                                              mcvr::chunkScheduling::Clock::now() - oldest))
                return;
            // Small interactive batches do not wait to fill. Weighted admission keeps old
            // background owners progressing without placing the entire old queue before interaction.
            const uint64_t inFlightBytes = selectedBytes_.load();
            chunkBuildDataBatch =
                ChunkBuildDataBatch::create(interactive ? std::min(4u, remaining) : remaining, queuedIndex_, chunks_,
                                            chunkBuildDatas_, camera_, inFlightBytes, admissionSequence_,
                                            interactive ? mcvr::chunkScheduling::uploadBudget :
                                                          uploadPacer_.available(mcvr::chunkScheduling::Clock::now()));
            if (chunkBuildDataBatch->batchData.empty()) return;
            admissionSequence_ += chunkBuildDataBatch->batchData.size();
            const auto previousBytes = submittedBytes;
            for (const auto &data : chunkBuildDataBatch->batchData)
                submittedBytes += uint64_t(data->allVertexCount) * sizeof(vk::VertexFormat::PBRVertex) +
                                  uint64_t(data->allIndexCount) * sizeof(uint32_t) +
                                  uint64_t(data->lightInfos.size()) * sizeof(LightInfo);
            selectedCount_ += chunkBuildDataBatch->batchData.size();
            chunkBuildDataBatch->reservedInputBytes = submittedBytes - previousBytes;
            selectedBytes_ += chunkBuildDataBatch->reservedInputBytes;
            ++preparingBatches_;
            fence = freeFences_.front();
            freeFences_.pop();
            commandBuffer = freeCommandBuffers_.front();
            freeCommandBuffers_.pop();
        }

        const auto batchBuildStart = std::chrono::steady_clock::now();
        // Admission includes CPU batch assembly as well as GPU submission. This keeps a burst of
        // light-only or otherwise upload-free batches from consuming an unbounded frame budget.
        submittedThisFrame++;
        for (const auto &data : chunkBuildDataBatch->batchData)
            mcvr::chunkTrace::note("build-prepare", data->id, data->version, 0, data->slotGeneration);
        chunkBuildDataBatch->build();
        mcvr::failure::throwIfFatal();
        if (chunkPerformanceEnabled) {
            chunkPerformance.batchBuildCount.fetch_add(1, std::memory_order_relaxed);
            chunkPerformance.batchBuildCpuNs.fetch_add(elapsedNs(batchBuildStart), std::memory_order_relaxed);
        }

        {
            std::unique_lock owner(mutex_);
            const bool current = std::any_of(
                chunkBuildDataBatch->batchData.begin(), chunkBuildDataBatch->batchData.end(), [&](const auto &data) {
                    return externalHandles_.isCurrent(uint32_t(data->id), data->slotGeneration) &&
                           data->version == chunks_[data->id]->desiredVersion;
                });
            if (!current) {
                selectedCount_ -= chunkBuildDataBatch->batchData.size();
                selectedBytes_ -= chunkBuildDataBatch->reservedInputBytes;
                std::lock_guard pipeline(pipelineMutex_);
                --preparingBatches_;
                freeFences_.push(fence);
                freeCommandBuffers_.push(commandBuffer);
                continue;
            }
        }

        bool hasLightUploads = false;
        for (const auto &chunkBuildData : chunkBuildDataBatch->batchData) {
            if (chunkBuildData->lightBuffer != nullptr) {
                hasLightUploads = true;
                break;
            }
        }

        if (chunkBuildDataBatch->blasBatchBuilder == nullptr && !hasLightUploads) {
            for (const auto &data : chunkBuildDataBatch->batchData) data->completion = mcvr::scene::Completion::empty;
            std::lock_guard pipeline(pipelineMutex_);
            completed_.push_back({chunkBuildDataBatch});
            freeFences_.push(fence);
            freeCommandBuffers_.push(commandBuffer);
            --preparingBatches_;
            continue;
        }

        auto framework = Renderer::instance().framework();
        auto device = framework->device();
        auto physicalDevice = framework->physicalDevice();
        const auto queueFamilyIndex =
            useSecondaryQueue_ ? physicalDevice->secondaryQueueIndex() : physicalDevice->mainQueueIndex();

        commandBuffer->begin();
        // Every chunk's geometry streams are filled from the batch staging buffer.
        for (const auto &upload : chunkBuildDataBatch->geometryUploads) {
            const VkBufferCopy region{upload.stagingOffset, 0, upload.bytes};
            vkCmdCopyBuffer(commandBuffer->vkCommandBuffer(), chunkBuildDataBatch->geometryStaging->vkBuffer(),
                            upload.buffer->vkBuffer(), 1, &region);
        }
        for (const auto &chunkBuildData : chunkBuildDataBatch->batchData) {
            if (chunkBuildData->lightBuffer != nullptr) { chunkBuildData->lightBuffer->uploadToBuffer(commandBuffer); }
        }

        if (!chunkBuildDataBatch->geometryUploads.empty()) {
            // Same queue family on both sides, so one global barrier covers all chunk streams.
            commandBuffer->barriersMemory({{
                .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                .dstStageMask = VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                                VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
            }});
        }

        std::vector<vk::CommandBuffer::BufferMemoryBarrier> lightBarriers;
        for (const auto &chunkBuildData : chunkBuildDataBatch->batchData) {
            if (chunkBuildData->lightBuffer == nullptr) { continue; }

            lightBarriers.push_back({
                .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                .dstStageMask = VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                .srcQueueFamilyIndex = queueFamilyIndex,
                .dstQueueFamilyIndex = queueFamilyIndex,
                .buffer = chunkBuildData->lightBuffer,
            });
        }
        if (!lightBarriers.empty()) { commandBuffer->barriersBufferImage(lightBarriers, {}); }

        if (chunkBuildDataBatch->blasBatchBuilder != nullptr) {
            device->checkpoint(commandBuffer->vkCommandBuffer(), "chunk.BLAS.build");
            chunkBuildDataBatch->blasBatchBuilder->submit(commandBuffer);
        }
        device->checkpoint(commandBuffer->vkCommandBuffer(), "chunk.batch.end");
        commandBuffer->end();

        VkSubmitInfo vkSubmitInfo = {};
        vkSubmitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        vkSubmitInfo.commandBufferCount = 1;
        vkSubmitInfo.pCommandBuffers = &commandBuffer->vkCommandBuffer();

        if (useSecondaryQueue_) {
            chunkBuildDataBatch->readySemaphore = vk::Semaphore::create(device);
            vkSubmitInfo.signalSemaphoreCount = 1;
            vkSubmitInfo.pSignalSemaphores = &chunkBuildDataBatch->readySemaphore->vkSemaphore();
        }
        const auto submitStart = std::chrono::steady_clock::now();
        std::unique_lock queueLock(device->secondaryQueueMutex(), std::defer_lock);
        if (useSecondaryQueue_) queueLock.lock();
        mcvr::failure::throwIfFatal();
        if (!framework->isRunning()) return;
        const VkResult submitResult = useSecondaryQueue_ ?
                                          vkQueueSubmit(device->secondaryQueue(), 1, &vkSubmitInfo, fence->vkFence()) :
                                          vkQueueSubmit(device->mainVkQueue(), 1, &vkSubmitInfo, fence->vkFence());
        if (queueLock.owns_lock()) queueLock.unlock();
        mcvr::diagnostics::device_loss::note(
            "chunk-queue-submit", submitResult, static_cast<uint32_t>(chunkBuildDataBatch->batchData.size()), 0,
            (uint64_t)commandBuffer->vkCommandBuffer(), (uint64_t)fence->vkFence(), 0,
            (uint64_t)(useSecondaryQueue_ ? device->secondaryQueue() : device->mainVkQueue()));
        if (chunkPerformanceEnabled) {
            chunkPerformance.batchSubmitCount.fetch_add(1, std::memory_order_relaxed);
            chunkPerformance.batchSubmitCpuNs.fetch_add(elapsedNs(submitStart), std::memory_order_relaxed);
        }
        if (submitResult != VK_SUCCESS) {
            framework->recordFailure(submitResult, "vkQueueSubmit(chunk build)");
            commandBuffer->reset();
            std::lock_guard pipeline(pipelineMutex_);
            --preparingBatches_;
            freeFences_.push(fence);
            freeCommandBuffers_.push(commandBuffer);
            for (const auto &chunkBuildData : chunkBuildDataBatch->batchData) {
                releaseChunkBuildDataStaging(chunkBuildData);
            }
            return;
        }

        uploadPacer_.consumed(chunkBuildDataBatch->reservedInputBytes, mcvr::chunkScheduling::Clock::now());

        for (const auto &data : chunkBuildDataBatch->batchData)
            mcvr::chunkTrace::note("build-submit", data->id, data->version, 0, data->slotGeneration);
        if (mcvr::chunkTrace::enabled) {
            uint64_t bytes = 0;
            for (const auto &upload : chunkBuildDataBatch->geometryUploads) bytes += upload.bytes;
            for (const auto &data : chunkBuildDataBatch->batchData)
                if (data->lightBuffer) bytes += data->lightBuffer->size();
            // Payload buffers submitted by this chunk batch; excludes AS scratch/output, TLAS and SDK memory.
            mcvr::chunkTrace::note("upload-bytes", int64_t(bytes), int64_t(chunkBuildDataBatch->batchData.size()));
        }
        if (chunkPerformanceEnabled) chunkBuildDataBatch->submittedAt = std::chrono::steady_clock::now();
        std::lock_guard pipeline(pipelineMutex_);
        --preparingBatches_;
        buildingFences_.push_back(fence);
        buildingCommandBuffers_.push_back(commandBuffer);
        buildingBatches_.push_back(chunkBuildDataBatch);
    }
}

uint32_t ChunkBuildScheduler::chunkBuildingBatchSize() {
    return chunkBuildingBatchSize_;
}

uint32_t ChunkBuildScheduler::chunkBuildingTotalBatches() {
    return chunkBuildingTotalBatches_;
}

size_t ChunkBuildScheduler::pendingBuildCount() const {
    return preparingBatches_.load();
}

size_t ChunkBuildScheduler::activeBatchCount() const {
    std::lock_guard lock(pipelineMutex_);
    return buildingBatches_.size() + completed_.size();
}

std::pair<uint64_t, uint64_t> ChunkBuildScheduler::pendingInput() const {
    return {selectedCount_.load(), selectedBytes_.load()};
}

void ChunkBuildScheduler::collectInFlightBuffers(std::unordered_set<const void *> &buffers) const {
    std::lock_guard pipeline(pipelineMutex_);
    auto collect = [&](const auto &batch) {
        for (const auto &data : batch->batchData) {
            for (const auto &buffer : {data->indexBuffer, data->positionBuffer, data->materialBuffer})
                if (buffer) buffers.insert(buffer.get());
            if (data->lightBuffer) buffers.insert(data->lightBuffer.get());
            if (data->blas) buffers.insert(data->blas->blasBuffer().get());
        }
    };
    for (const auto &batch : buildingBatches_) collect(batch);
    for (const auto &entry : completed_) collect(entry.batch);
}

std::string Chunks::orphanCensus() {
    if (!chunkCensusEnabled) return {};
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    std::unordered_set<const void *> owned;
    for (const auto &chunk : chunks_) {
        if (chunk == nullptr) continue;
        for (const auto &buffer :
             {chunk->indexBuffer, chunk->positionBuffer, chunk->materialBuffer, chunk->lightBuffer})
            if (buffer) owned.insert(buffer.get());
        if (chunk->blas) owned.insert(chunk->blas->blasBuffer().get());
    }
    if (chunkBuildScheduler_ != nullptr) chunkBuildScheduler_->collectInFlightBuffers(owned);
    std::lock_guard trackedLock(trackedChunkBuffersMutex);
    uint64_t alive = 0, aliveBytes = 0, orphans = 0, orphanBytes = 0;
    std::map<long, uint64_t> owners;
    std::ostringstream samples;
    int sampled = 0;
    std::erase_if(trackedChunkBuffers, [&](const TrackedChunkBuffer &tracked) {
        auto buffer = tracked.buffer.lock();
        if (!buffer) return true;
        ++alive;
        aliveBytes += buffer->size();
        if (!owned.contains(buffer.get())) {
            const long others = buffer.use_count() - 1;
            ++orphans;
            orphanBytes += buffer->size();
            ++owners[others];
            if (sampled < 4 && tracked.id >= 0 && tracked.id < static_cast<int64_t>(chunks_.size())) {
                const auto &chunk = chunks_[tracked.id];
                samples << ' ' << tracked.id << ':' << tracked.version << "/blas" << chunk->blasVersion << "/desired"
                        << chunk->desiredVersion << "/owners" << others;
                ++sampled;
            }
        }
        return false;
    });
    std::ostringstream result;
    result << " tracked=" << alive << " trackedBytes=" << aliveBytes << " orphans=" << orphans
           << " orphanBytes=" << orphanBytes << " orphanOwners=";
    for (const auto &[count, buffers] : owners) result << count << 'x' << buffers << ',';
    result << " samples=" << samples.str();
    return result.str();
}

ChunkBuildScheduler::Census ChunkBuildScheduler::census() const {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    std::lock_guard pipeline(pipelineMutex_);
    Census result;
    result.queued = queuedIndex_.size();
    auto collect = [&](const auto &batch) {
        ++result.batches;
        result.chunks += batch->batchData.size();
        for (const auto &data : batch->batchData) {
            for (const auto &buffer :
                 {data->indexBuffer, data->positionBuffer, data->materialBuffer, data->lightBuffer})
                if (buffer) result.bytes += buffer->size();
            if (data->blas) result.bytes += data->blas->blasBuffer()->size();
        }
    };
    for (const auto &batch : buildingBatches_) collect(batch);
    for (const auto &entry : completed_) collect(entry.batch);
    return result;
}

float Chunk1::buildFactor(std::chrono::steady_clock::time_point currentTime, glm::vec3 cameraPos, glm::vec3 chunkPos) {
    double tDiff = std::chrono::duration<double, std::milli>(currentTime - lastUpdate).count();
    double dDiff = glm::distance(cameraPos, chunkPos);

    double tScore = 1 - exp(-tDiff / T_HALF);
    double nearScore = 1 / (1 + pow(dDiff / D_HALF, D_SENSITIVITY));
    double dScore = D_PRIORITY_FLOOR + (1.0 - D_PRIORITY_FLOOR) * nearScore;
    double score = pow(tScore, T_WEIGHT) * pow(dScore, D_WEIGHT);

    return score;
}

bool Chunk1::enqueue(std::shared_ptr<ChunkBuildData> chunkBuildData) {
    auto framework = Renderer::instance().framework();
    auto &frr = framework->frameResourceRetainer();

    lastUpdate = std::chrono::steady_clock::now();

    const mcvr::scene::ResourceVersion built{chunkBuildData->slotGeneration, uint64_t(chunkBuildData->id),
                                             chunkBuildData->version};
    const mcvr::scene::ResourceVersion desired{chunkBuildData->slotGeneration, uint64_t(chunkBuildData->id),
                                               desiredVersion};
    if (mcvr::scene::mayPublish(desired, built, blasVersion, chunkBuildData->completion)) {
        if (scenePublications) scenePublications->changed(sceneSlot, bool(chunkBuildData->blas));
        sceneMetadataCache.reset();
        blasVersion = chunkBuildData->version;
        mcvr::chunkTrace::note("publish", chunkBuildData->id, blasVersion, 0, chunkBuildData->slotGeneration);
        x = chunkBuildData->x;
        y = chunkBuildData->y;
        z = chunkBuildData->z;

        frr.retain(blas);
        blas = chunkBuildData->blas;

        frr.retain(indexBufferAddresses);
        indexBufferAddresses =
            std::make_shared<std::vector<VkDeviceAddress>>(std::move(chunkBuildData->indexBufferAddresses));

        frr.retain(positionBufferAddresses);
        positionBufferAddresses =
            std::make_shared<std::vector<VkDeviceAddress>>(std::move(chunkBuildData->positionBufferAddresses));

        frr.retain(materialBufferAddresses);
        materialBufferAddresses =
            std::make_shared<std::vector<VkDeviceAddress>>(std::move(chunkBuildData->materialBufferAddresses));

        frr.retain(indexBuffer);
        indexBuffer = chunkBuildData->indexBuffer;

        frr.retain(positionBuffer);
        positionBuffer = chunkBuildData->positionBuffer;

        frr.retain(materialBuffer);
        materialBuffer = chunkBuildData->materialBuffer;

        frr.retain(lightInfos);
        lightInfos = std::make_shared<std::vector<LightInfo>>(std::move(chunkBuildData->lightInfos));

        frr.retain(lightBuffer);
        lightBuffer = chunkBuildData->lightBuffer;
        lightCount = chunkBuildData->lightCount;

        geometryCount = chunkBuildData->geometryCount;

        frr.retain(geometryGroupNames);
        geometryGroupNames = std::make_shared<std::vector<std::string>>(std::move(chunkBuildData->geometryGroupNames));

        frr.retain(geometryMaterialFlags);
        geometryMaterialFlags =
            std::make_shared<std::vector<uint32_t>>(std::move(chunkBuildData->geometryMaterialFlags));
        return true;
    } else {
        frr.retain(chunkBuildData->blas);
        frr.retain(chunkBuildData->indexBuffer);
        frr.retain(chunkBuildData->positionBuffer);
        frr.retain(chunkBuildData->materialBuffer);
        frr.retain(chunkBuildData->lightBuffer);
        return false;
    }
}

void Chunk1::markDirty(int64_t generation) {
    if (generation >= 0) {
        desiredVersion = std::max(desiredVersion, generation);
        latestVersion = std::max(latestVersion, generation + 1);
    }
    lastUpdate = std::chrono::steady_clock::now();
}

void Chunk1::invalidate(int64_t generation) {
    if (scenePublications) scenePublications->changed(sceneSlot, false);
    sceneMetadataCache.reset();
    auto framework = Renderer::instance().framework();
    auto &frr = framework->frameResourceRetainer();

    lastUpdate = std::chrono::steady_clock::now();

    if (generation >= 0) {
        markDirty(generation);
    } else {
        blasVersion = latestVersion++;
        desiredVersion = blasVersion;
    }

    frr.retain(blas);
    blas = nullptr;

    frr.retain(indexBufferAddresses);
    indexBufferAddresses = nullptr;

    frr.retain(positionBufferAddresses);
    positionBufferAddresses = nullptr;

    frr.retain(materialBufferAddresses);
    materialBufferAddresses = nullptr;

    frr.retain(indexBuffer);
    indexBuffer = nullptr;

    frr.retain(positionBuffer);
    positionBuffer = nullptr;

    frr.retain(materialBuffer);
    materialBuffer = nullptr;

    frr.retain(lightInfos);
    lightInfos = nullptr;

    frr.retain(lightBuffer);
    lightBuffer = nullptr;
    lightCount = 0;

    geometryCount = 0;

    frr.retain(geometryGroupNames);
    geometryGroupNames = nullptr;

    frr.retain(geometryMaterialFlags);
    geometryMaterialFlags = nullptr;
}

void Chunk1::retainResources(FrameResourceRetainer &frr) {
    frr.retain(blas);
    frr.retain(indexBufferAddresses);
    frr.retain(positionBufferAddresses);
    frr.retain(materialBufferAddresses);
    frr.retain(indexBuffer);
    frr.retain(positionBuffer);
    frr.retain(materialBuffer);
    frr.retain(lightInfos);
    frr.retain(lightBuffer);
}

void Chunk1::releaseEmissionResources(FrameResourceRetainer &frr) {
    // Snapshots also own the published light resources; refresh rather than pin
    // an obsolete light buffer indefinitely after disabling emission collection.
    if (scenePublications) scenePublications->changed(sceneSlot, bool(blas));
    sceneMetadataCache.reset();
    frr.retain(lightInfos);
    lightInfos = nullptr;

    frr.retain(lightBuffer);
    lightBuffer = nullptr;

    lightCount = 0;
}

std::shared_ptr<ChunkRenderData> Chunk1::tryGetValid() {
    auto ret = ChunkRenderData::create();
    ret->x = x;
    ret->y = y;
    ret->z = z;
    ret->blas = blas;
    ret->indexBufferAddresses = indexBufferAddresses;
    ret->positionBufferAddresses = positionBufferAddresses;
    ret->materialBufferAddresses = materialBufferAddresses;
    ret->indexBuffer = indexBuffer;
    ret->positionBuffer = positionBuffer;
    ret->materialBuffer = materialBuffer;
    ret->lightInfos = lightInfos;
    ret->lightBuffer = lightBuffer;
    ret->lightCount = lightCount;
    ret->geometryCount = geometryCount;
    ret->geometryGroupNames = geometryGroupNames;
    ret->geometryMaterialFlags = geometryMaterialFlags;

    return ret;
}

const std::shared_ptr<mcvr::ChunkSceneMetadata> &Chunk1::sceneMetadata() {
    if (!blas) throw std::logic_error("Cannot cache an unpublished chunk");
    const mcvr::ChunkSceneKey key{blasVersion,
                                  geometryCount,
                                  {blas.get(), indexBufferAddresses.get(), positionBufferAddresses.get(),
                                   materialBufferAddresses.get(), indexBuffer.get(), positionBuffer.get(),
                                   materialBuffer.get(), lightInfos.get(), lightBuffer.get(), geometryGroupNames.get(),
                                   geometryMaterialFlags.get()}};
    return sceneMetadataCache.get(key, [&] {
        auto snapshot = tryGetValid();
        if (!snapshot->indexBufferAddresses || !snapshot->positionBufferAddresses ||
            !snapshot->materialBufferAddresses || !snapshot->geometryMaterialFlags)
            throw std::logic_error("Missing published chunk geometry arrays");
        const auto names = snapshot->geometryGroupNames ? std::span<const std::string>(*snapshot->geometryGroupNames) :
                                                          std::span<const std::string>{};
        auto metadata = std::make_shared<mcvr::ChunkSceneMetadata>(
            snapshot, snapshot->geometryCount, *snapshot->indexBufferAddresses, *snapshot->positionBufferAddresses,
            *snapshot->materialBufferAddresses, names, *snapshot->geometryMaterialFlags);
        size_t memoryIndex = 0;
        for (const auto &buffer :
             {snapshot->indexBuffer, snapshot->positionBuffer, snapshot->materialBuffer, snapshot->lightBuffer})
            if (buffer) {
                metadata->retainedBytes += buffer->size();
                metadata->memoryFootprint[memoryIndex++] = {buffer.get(), buffer->size()};
            }
        metadata->retainedBytes += snapshot->blas->blasBuffer()->size();
        metadata->memoryFootprint[memoryIndex] = {snapshot->blas->blasBuffer().get(),
                                                  snapshot->blas->blasBuffer()->size()};
        return metadata;
    });
}

Chunks::Chunks(std::shared_ptr<Framework> framework) {}

void Chunks::allocateChunkPackedDataBuffers() {
    auto framework = Renderer::instance().framework();
    auto device = framework->device();
    auto vma = framework->vma();

    chunkPackedDataBuffers_.clear();
    chunkPackedDataBuffers_.resize(framework->swapchain()->imageCount());
    if (chunkPackedData_.empty()) { return; }

    const size_t chunkPackedDataBytes = chunkPackedData_.size() * sizeof(ChunkPackedData);
    for (auto &chunkPackedDataBuffer : chunkPackedDataBuffers_) {
        chunkPackedDataBuffer =
            vk::DeviceLocalBuffer::create(vma, device, false, chunkPackedDataBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    }
}

void Chunks::releaseEmissionResources() {
    auto framework = Renderer::instance().framework();
    auto &frr = framework->frameResourceRetainer();

    for (auto &chunk : chunks_) {
        if (chunk != nullptr) { chunk->releaseEmissionResources(frr); }
    }

    for (auto &chunkBuildData : chunkBuildDatas_) {
        if (chunkBuildData == nullptr) { continue; }
        chunkBuildData->lightInfos.clear();
        frr.retain(chunkBuildData->lightBuffer);
        chunkBuildData->lightBuffer = nullptr;
        chunkBuildData->lightCount = 0;
    }

    std::fill(chunkPackedData_.begin(), chunkPackedData_.end(), ChunkPackedData{});
    for (auto &chunkPackedDataBuffer : chunkPackedDataBuffers_) {
        frr.retain(chunkPackedDataBuffer);
        chunkPackedDataBuffer = nullptr;
    }
    chunkPackedDataBuffers_.clear();
}

void Chunks::reset(uint32_t numChunks, uint32_t sizeX, uint32_t sizeY, uint32_t sizeZ, int32_t bottomSectionCoord) {
    if (chunkBuildScheduler_) chunkBuildScheduler_->stopWorker();
    std::unique_lock<std::recursive_mutex> lock(mutex_);

    auto framework = Renderer::instance().framework();
    auto device = framework->device();
    if (framework->waitRenderQueueIdle() != VK_SUCCESS || framework->waitBackendQueueIdle() != VK_SUCCESS) { return; }

    sizeX_ = static_cast<int32_t>(sizeX);
    sizeY_ = static_cast<int32_t>(sizeY);
    sizeZ_ = static_cast<int32_t>(sizeZ);
    bottomSectionCoord_ = bottomSectionCoord;
    primaryChunkCount_ = numChunks;
    externalHandles_.reset(numChunks);
    chunkStorageSectionPos_ = glm::ivec3(0, bottomSectionCoord, 0);


    chunks_.clear();
    chunks_.resize(numChunks);
    staticSceneCache_.reset();
    scenePublications_ = std::make_shared<mcvr::ChunkScenePublications>();
    chunkPackedData_.assign(numChunks, ChunkPackedData{});
    chunkPackedDataBuffers_.clear();
    if (Renderer::options.collectChunkEmission) { allocateChunkPackedDataBuffers(); }
    chunkBuildDatas_.clear();
    chunkBuildDatas_.resize(numChunks);
    queuedIndex_.clear();

    for (int i = 0; i < numChunks; i++) {
        chunks_[i] = Chunk1::create();
        chunks_[i]->scenePublications = scenePublications_;
        chunks_[i]->sceneSlot = uint32_t(i);
        chunkBuildDatas_[i] = nullptr;
    }

    uint32_t chunkBuildingBatchSize = Renderer::instance().options.chunkBuildingBatchSize;
    uint32_t chunkBuildingTotalBatches = Renderer::instance().options.chunkBuildingTotalBatches;
    chunkBuildScheduler_ =
        ChunkBuildScheduler::create(queuedIndex_, chunks_, chunkBuildDatas_, mutex_, chunkPackedData_, externalHandles_,
                                    chunkBuildingBatchSize, chunkBuildingTotalBatches);
}

void Chunks::resetScheduler() {
    if (chunkBuildScheduler_) chunkBuildScheduler_->stopWorker();
    std::unique_lock<std::recursive_mutex> lock(mutex_);

    if (chunkBuildScheduler_ == nullptr) return;

    mcvr::failure::runCheckedStage([&] { chunkBuildScheduler_->waitAllBatchesFinish(); });

    uint32_t chunkBuildingBatchSize = Renderer::instance().options.chunkBuildingBatchSize;
    uint32_t chunkBuildingTotalBatches = Renderer::instance().options.chunkBuildingTotalBatches;
    chunkBuildScheduler_ =
        ChunkBuildScheduler::create(queuedIndex_, chunks_, chunkBuildDatas_, mutex_, chunkPackedData_, externalHandles_,
                                    chunkBuildingBatchSize, chunkBuildingTotalBatches);
}

void Chunks::setCollectChunkEmission(bool collect) {
    if (chunkBuildScheduler_) chunkBuildScheduler_->stopWorker();
    std::unique_lock<std::recursive_mutex> lock(mutex_);

    if (chunkBuildScheduler_ != nullptr) {
        mcvr::failure::runCheckedStage([&] { chunkBuildScheduler_->waitAllBatchesFinish(); });
    }

    auto textures = Renderer::instance().textures();
    auto emission = textures != nullptr ? textures->emission() : nullptr;
    for (auto &chunkBuildData : chunkBuildDatas_) {
        if (chunkBuildData == nullptr) { continue; }

        chunkBuildData->collectChunkEmission = collect;
        if (collect && emission != nullptr) {
            chunkBuildData->buildLightInfos(*emission);
        } else {
            chunkBuildData->lightInfos.clear();
            chunkBuildData->lightCount = 0;
            chunkBuildData->lightBuffer = nullptr;
        }
    }

    if (collect) {
        if (chunkPackedDataBuffers_.empty()) { allocateChunkPackedDataBuffers(); }
        return;
    }

    releaseEmissionResources();
}

void Chunks::resetFrame() {
    auto context = Renderer::instance().framework()->safeAcquireCurrentContext();
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    auto framework = Renderer::instance().framework();
    auto &frr = framework->frameResourceRetainer();


    if (Renderer::options.collectChunkEmission && !chunkPackedData_.empty() &&
        context->frameIndex < chunkPackedDataBuffers_.size()) {
        auto &chunkPackedDataBuffer = chunkPackedDataBuffers_[context->frameIndex];
        if (chunkPackedDataBuffer != nullptr) {
            const size_t chunkPackedDataBytes = chunkPackedData_.size() * sizeof(ChunkPackedData);
            chunkPackedDataBuffer->uploadToStagingBuffer(chunkPackedData_.data(), chunkPackedDataBytes, 0);
            Renderer::instance().buffers()->queueImportantWorldUpload(chunkPackedDataBuffer);
        }
    }
}

void Chunks::markChunkDirty(int64_t handle, int64_t generation) {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    auto resolved = externalHandles_.resolve(handle);
    if (!resolved) return;
    const uint32_t id = *resolved;
    chunks_[id]->markDirty(generation);
    if (chunkBuildDatas_[id] != nullptr && chunkBuildDatas_[id]->version < generation) {
        auto &frr = Renderer::instance().framework()->frameResourceRetainer();
        frr.retain(chunkBuildDatas_[id]);
        chunkBuildDatas_[id] = nullptr;
        queuedIndex_.erase(id);
    }
}

void Chunks::invalidateChunk(int64_t handle, int64_t generation) {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    auto resolved = externalHandles_.resolve(handle);
    if (!resolved) return;
    const uint32_t id = *resolved;
    auto framework = Renderer::instance().framework();
    auto &frr = framework->frameResourceRetainer();

    queuedIndex_.erase(id);

    frr.retain(chunkBuildDatas_[id]);
    chunkBuildDatas_[id] = nullptr;

    chunks_[id]->invalidate(generation);

    storeChunkPackedData(chunkPackedData_, id, chunks_[id]->x, chunks_[id]->y, chunks_[id]->z, 0, 0, 0);
}

void Chunks::relocateChunk(int64_t handle, int x, int y, int z, int64_t generation) {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    auto resolved = externalHandles_.resolve(handle);
    if (!resolved) return;
    const uint32_t id = *resolved;
    auto framework = Renderer::instance().framework();
    auto &frr = framework->frameResourceRetainer();

    queuedIndex_.erase(id);

    frr.retain(chunkBuildDatas_[id]);
    chunkBuildDatas_[id] = nullptr;

    chunks_[id]->x = x;
    chunks_[id]->y = y;
    chunks_[id]->z = z;
    chunks_[id]->invalidate(generation);

    storeChunkPackedData(chunkPackedData_, id, x, y, z, 0, 0, 0);
}

void Chunks::queueChunkBuild(ChunkBuildTask task) {
    mcvr::profile::Scope auditProfile("chunk-copy-enqueue");
    const auto inputStart = std::chrono::steady_clock::now();
    const auto recordInputTiming = [&]() {
        if (!chunkPerformanceEnabled) return;
        chunkPerformance.buildInputCount.fetch_add(1, std::memory_order_relaxed);
        chunkPerformance.buildInputCpuNs.fetch_add(elapsedNs(inputStart), std::memory_order_relaxed);
    };
    bool copiedInput = false;
    mcvr::chunkInput::submit(
        mutex_, externalHandles_, chunks_, task.id, task.generation,
        [&](const auto &admission) {
            task.id = admission.slot;
            copiedInput = true;
            uint32_t allVertexCount = 0, allIndexCount = 0;
            std::vector<World::GeometryTypes> geometryTypes;
            std::vector<std::string> geometryGroupNames;
            std::vector<uint32_t> geometryMaterialFlags;
            std::vector<std::vector<vk::VertexFormat::PBRVertex>> vertices;
            std::vector<std::vector<uint32_t>> indices;

            for (int i = 0; i < task.geometryCount; i++) {
                World::GeometryTypes geometryType = static_cast<World::GeometryTypes>(task.geometryTypes[i]);
                auto &geometryVertices = vertices.emplace_back();
                auto &geometryIndices = indices.emplace_back();

                geometryVertices.resize(task.vertexCounts[i]);
                std::memcpy(geometryVertices.data(), task.vertices[i],
                            task.vertexCounts[i] * sizeof(vk::VertexFormat::PBRVertex));

                for (int j = 0; j < task.vertexCounts[i]; j += 4) {
                    geometryIndices.push_back(j + 0);
                    geometryIndices.push_back(j + 1);
                    geometryIndices.push_back(j + 2);
                    geometryIndices.push_back(j + 2);
                    geometryIndices.push_back(j + 3);
                    geometryIndices.push_back(j + 0);
                }

                if (geometryVertices.empty() || geometryIndices.empty()) {
                    vertices.pop_back();
                    indices.pop_back();
                    continue;
                }

                geometryTypes.push_back(geometryType);
                if (task.geometryGroupNames != nullptr && task.geometryGroupNames[i] != nullptr) {
                    geometryGroupNames.emplace_back(task.geometryGroupNames[i]);
                } else {
                    geometryGroupNames.emplace_back("default");
                }
                geometryMaterialFlags.push_back(
                    task.geometryMaterialFlags == nullptr ? 0u : static_cast<uint32_t>(task.geometryMaterialFlags[i]));

                allVertexCount += geometryVertices.size();
                allIndexCount += geometryIndices.size();
            }
            auto chunkBuildData = ChunkBuildData::create(
                task.id, task.x, task.y, task.z, admission.version, false, allVertexCount, allIndexCount,
                static_cast<uint32_t>(vertices.size()), std::move(geometryTypes), std::move(geometryGroupNames),
                std::move(geometryMaterialFlags), std::move(vertices), std::move(indices));
            chunkBuildData->slotGeneration = admission.generation;
            chunkBuildData->priority = task.priority;
            return chunkBuildData;
        },
        [&](const auto &admission, std::shared_ptr<ChunkBuildData> chunkBuildData) {
            mcvr::failure::throwIfFatal();
            const bool collectChunkEmission = Renderer::options.collectChunkEmission && task.collectEmission;
            chunkBuildData->collectChunkEmission = collectChunkEmission;
            mcvr::chunkTrace::note("native-enqueue", task.id, admission.version, 0, admission.generation);
            if (collectChunkEmission && (Renderer::instance().textures() != nullptr)) {
                auto textures = Renderer::instance().textures();
                if (auto emission = textures->emission(); emission != nullptr) {
                    chunkBuildData->buildLightInfos(*emission);
                }
            }

            queuedIndex_.insert(task.id);
            chunkBuildDatas_[task.id] = std::move(chunkBuildData);
            if (chunkBuildScheduler_) chunkBuildScheduler_->notifyWork();
        });
    if (copiedInput) recordInputTiming();
}

int64_t Chunks::allocateExternalChunk() {
    std::unique_lock<std::recursive_mutex> lock(mutex_);

    const auto allocation = externalHandles_.allocate(static_cast<uint32_t>(chunks_.size()));
    const uint32_t id = allocation.slot;
    if (allocation.reused) {
        chunks_[id] = Chunk1::create();
        chunkBuildDatas_[id] = nullptr;
        chunkPackedData_[id] = ChunkPackedData{};
    } else {
        chunks_.push_back(Chunk1::create());
        chunkBuildDatas_.push_back(nullptr);
        chunkPackedData_.emplace_back();
    }

    if (!allocation.reused && Renderer::options.collectChunkEmission && !chunkPackedDataBuffers_.empty()) {
        auto &frr = Renderer::instance().framework()->frameResourceRetainer();
        for (auto &buffer : chunkPackedDataBuffers_) { frr.retain(buffer); }
        allocateChunkPackedDataBuffers();
    }

    return allocation.handle;
}

void Chunks::updateExternalChunkTransform(int64_t handle, const glm::dmat4 &transform) {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    auto resolved = externalHandles_.resolve(handle);
    if (!resolved || *resolved < primaryChunkCount_) return;
    const uint32_t id = *resolved;

    chunks_[id]->hasCustomTransform = true;
    chunks_[id]->customTransform = transform;
}

void Chunks::releaseExternalChunk(int64_t handle) {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    auto resolved = externalHandles_.resolve(handle);
    if (!resolved || *resolved < primaryChunkCount_) return;
    const uint32_t id = *resolved;

    queuedIndex_.erase(id);
    auto &frr = Renderer::instance().framework()->frameResourceRetainer();
    frr.retain(chunkBuildDatas_[id]);
    chunkBuildDatas_[id] = nullptr;
    chunks_[id]->invalidate();
    chunks_[id]->hasCustomTransform = false;
    chunkPackedData_[id] = ChunkPackedData{};
    externalHandles_.release(handle);
}

bool Chunks::isChunkReady(int64_t handle) {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    auto resolved = externalHandles_.resolve(handle);
    if (!resolved) return false;
    const uint32_t id = *resolved;
    auto chunkRenderData = chunks_[id]->tryGetValid();
    return chunkRenderData->blas != nullptr;
}

uint32_t Chunks::countReadyPrimaryChunks() {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    uint32_t ready = 0;
    const size_t count = std::min(static_cast<size_t>(primaryChunkCount_), chunks_.size());
    for (size_t i = 0; i < count; i++) {
        // This count is protected by the publication mutex. It does not need an
        // owning snapshot and must not allocate one for every reserved empty slot.
        if (chunks_[i] != nullptr && chunks_[i]->blas != nullptr) { ready++; }
    }
    return ready;
}

std::string Chunks::performanceSnapshot() {
    if (!chunkPerformanceEnabled) return "disabled (set RADIANCE_CHUNK_PERF=1)";
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    auto take = [](std::atomic<uint64_t> &value) { return value.exchange(0, std::memory_order_relaxed); };
    std::ostringstream result;
    result << "build_input_count=" << take(chunkPerformance.buildInputCount)
           << "; build_input_cpu_ns=" << take(chunkPerformance.buildInputCpuNs)
           << "; batch_build_count=" << take(chunkPerformance.batchBuildCount)
           << "; batch_build_cpu_ns=" << take(chunkPerformance.batchBuildCpuNs)
           << "; batch_submit_count=" << take(chunkPerformance.batchSubmitCount)
           << "; batch_submit_cpu_ns=" << take(chunkPerformance.batchSubmitCpuNs)
           << "; batch_complete_count=" << take(chunkPerformance.batchCompleteCount)
           << "; batch_queue_gpu_wall_ns=" << take(chunkPerformance.batchQueueGpuWallNs)
           << "; queued_builds=" << queuedIndex_.size() << "; pending_batch_builds="
           << (chunkBuildScheduler_ == nullptr ? 0 : chunkBuildScheduler_->pendingBuildCount())
           << "; active_gpu_batches="
           << (chunkBuildScheduler_ == nullptr ? 0 : chunkBuildScheduler_->activeBatchCount());
    return result.str();
}

std::pair<uint64_t, uint64_t> Chunks::buildBacklog() {
    std::unique_lock lock(mutex_);
    auto result = chunkBuildScheduler_ ? chunkBuildScheduler_->pendingInput() : std::pair<uint64_t, uint64_t>{};
    result.first += queuedIndex_.size();
    for (auto slot : queuedIndex_) {
        const auto &data = chunkBuildDatas_[slot];
        if (data)
            result.second += uint64_t(data->allVertexCount) * sizeof(vk::VertexFormat::PBRVertex) +
                             uint64_t(data->allIndexCount) * sizeof(uint32_t) +
                             data->lightInfos.size() * sizeof(LightInfo);
    }
    return result;
}

void Chunks::close() {
    if (chunkBuildScheduler_) chunkBuildScheduler_->stopWorker();
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    if (chunkBuildScheduler_ != nullptr) {
        chunkBuildScheduler_->waitAllBatchesFinish();
        chunkBuildScheduler_ = nullptr;
    }

    queuedIndex_.clear();
    chunkBuildDatas_.clear();
    chunkPackedData_.clear();
    chunkPackedDataBuffers_.clear();
    chunks_.clear();
    staticSceneCache_.reset();
    scenePublications_.reset();
    sizeX_ = 0;
    sizeY_ = 0;
    sizeZ_ = 0;
    bottomSectionCoord_ = 0;
    chunkStorageSectionPos_ = glm::ivec3(0);
    primaryChunkCount_ = 0;
    externalHandles_.reset(0);
}

void Chunks::releaseScene() {
    if (chunkBuildScheduler_) chunkBuildScheduler_->stopWorker();
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    if (chunkBuildScheduler_ != nullptr) {
        chunkBuildScheduler_->waitAllBatchesFinish();
        chunkBuildScheduler_ = nullptr;
    }
    queuedIndex_.clear();
    chunkBuildDatas_.clear();
    chunkPackedData_.clear();
    chunkPackedDataBuffers_.clear();
    chunks_.clear();
    staticSceneCache_.reset();
    scenePublications_.reset();
    primaryChunkCount_ = 0;
    externalHandles_.reset(0);
}

std::recursive_mutex &Chunks::mutex() {
    return mutex_;
}

std::vector<std::shared_ptr<Chunk1>> &Chunks::chunks() {
    return chunks_;
}

const std::shared_ptr<const mcvr::StaticChunkScene> &Chunks::staticScene() {
    if (!scenePublications_) scenePublications_ = std::make_shared<mcvr::ChunkScenePublications>();
    return staticSceneCache_.get(scenePublications_, [&](const auto &active) {
        auto next = std::make_shared<mcvr::StaticChunkPage>();
        size_t geometryCount = 0;
        for (auto slot : active) geometryCount += chunks_.at(slot)->geometryCount;
        next->reserve(active.size(), geometryCount);
        for (auto slot : active) {
            const auto &chunk = chunks_.at(slot);
            if (slot >= primaryChunkCount_ || !chunk->blas || chunk->hasCustomTransform)
                throw std::logic_error("Invalid primary chunk scene publication");
            next->add(slot, {chunk->x, chunk->y, chunk->z}, chunk->blas, chunk->sceneMetadata());
        }
        return next;
    });
}

std::shared_ptr<ChunkBuildScheduler> Chunks::chunkBuildScheduler() {
    std::unique_lock<std::recursive_mutex> lock(mutex_, std::defer_lock);
    {
        mcvr::profile::Scope wait("chunk-scheduler-get-lock-wait");
        lock.lock();
    }
    return chunkBuildScheduler_;
}


std::shared_ptr<vk::DeviceLocalBuffer> Chunks::chunkPackedData() {
    auto context = Renderer::instance().framework()->safeAcquireCurrentContext();
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    if (context->frameIndex >= chunkPackedDataBuffers_.size()) { return nullptr; }
    return chunkPackedDataBuffers_[context->frameIndex];
}

glm::ivec4 Chunks::chunkGridInfo() {
    std::unique_lock<std::recursive_mutex> lock(mutex_, std::defer_lock);
    {
        mcvr::profile::Scope wait("chunk-grid-info-lock-wait");
        lock.lock();
    }
    return glm::ivec4(sizeX_, sizeY_, sizeZ_, bottomSectionCoord_);
}

void Chunks::setChunkStorageSectionPos(glm::ivec3 sectionPos) {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    chunkStorageSectionPos_ = sectionPos;
}

glm::ivec4 Chunks::chunkStorageSectionPos() {
    std::unique_lock<std::recursive_mutex> lock(mutex_);
    return glm::ivec4(chunkStorageSectionPos_, 0);
}
