#include "entities.hpp"
#include "computed_surface.hpp"
#include "renderer.hpp"
#include "render_framework.hpp"
#include "textures.hpp"
#include "buffers.hpp"
#include "core/diagnostics/computed_surface_audit.hpp"
#include <atomic>
#include <cmath>

namespace {
std::atomic_uint64_t nextHandle{1};
}

uint64_t Entities::surfaceProgram(uint64_t generation, std::span<const uint32_t> spirv) {
    if (!worldMeshFrameOpen_ || generation != activeResourceGeneration_)
        throw std::invalid_argument("Surface program generation is not active");
    auto value = makeSurfaceProgram(*Renderer::instance().framework(), spirv);
    auto handle = nextHandle.fetch_add(1);
    surfaces_.emplace(handle, std::move(value));
    return handle;
}
uint64_t Entities::surfaceData(uint64_t generation, const void *data, size_t bytes) {
    if (!worldMeshFrameOpen_ || generation != activeResourceGeneration_)
        throw std::invalid_argument("Surface input generation is not active");
    auto value = makeSurfaceData(*Renderer::instance().framework(), data, bytes);
    auto handle = nextHandle.fetch_add(1);
    surfaces_.emplace(handle, std::move(value));
    return handle;
}
void Entities::releaseSurface(uint64_t generation, uint64_t handle) {
    if (generation == surfaceGeneration_) surfaces_.erase(handle);
}
bool Entities::queueSurface(uint64_t world,
                            uint64_t frame,
                            uint64_t generation,
                            uint64_t stage,
                            int stageKind,
                            uint64_t program,
                            uint64_t source,
                            uint64_t contacts,
                            const void *parameters,
                            size_t bytes,
                            int noiseId,
                            int fieldId,
                            uint32_t work,
                            uint32_t vertices,
                            uint32_t indices,
                            int identity,
                            double x,
                            double y,
                            double z) {
    if (!worldMeshFrameOpen_ || !acceptsWorldMeshGeneration(world, frame, generation) || worldMeshStages_.empty() ||
        worldMeshStages_.back().token != stage)
        return false;
    auto get = [&](uint64_t id) {
        auto it = surfaces_.find(id);
        if (it == surfaces_.end()) throw std::invalid_argument("Unknown computed surface resource");
        return it->second;
    };
    auto f = Renderer::instance().framework();
    const uint64_t limit = f->physicalDevice()->properties().limits.maxStorageBufferRange;
    if (!parameters || !bytes || bytes > 4096 || bytes % 16 || work == 0 || (vertices == 0) != (indices == 0) ||
        uint64_t(vertices) * sizeof(vk::VertexFormat::MaterialVertex) > limit || uint64_t(indices) * 4 > limit ||
        !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(z))
        throw std::invalid_argument("Computed surface dispatch range");
    auto dispatch = ComputedSurfaceDispatch::create();
    dispatch->program = get(program);
    dispatch->source = get(source);
    dispatch->contacts = get(contacts);
    if (!dispatch->program->pipeline || !dispatch->source->data || !dispatch->contacts->data)
        throw std::invalid_argument("Computed surface resource kind");
    auto textures = Renderer::instance().textures();
    dispatch->noise = textures->texture(noiseId);
    dispatch->sampler = textures->sampler(noiseId);
    dispatch->field = textures->texture(fieldId);
    if (!dispatch->noise || !dispatch->sampler || !dispatch->field ||
        dispatch->field->vkFormat() != VK_FORMAT_R32_SFLOAT)
        throw std::invalid_argument("Computed surface texture ABI");
    dispatch->parameters = makeSurfaceData(*f, parameters, bytes)->data;
    if (mcvr::audit::computedSurfacesEnabled()) {
        const auto *start = static_cast<const std::byte *>(parameters);
        dispatch->auditParameters.assign(start, start + bytes);
    }
    dispatch->vertices = vertices;
    dispatch->indices = indices;
    dispatch->work = work;
    auto uploads = Renderer::instance().buffers();
    for (const auto &input : {dispatch->source, dispatch->contacts}) {
        // A rolled-back frame may discard its pending queue. Retry that payload in a later
        // frame, but never requeue staging already consumed by the actual upload recorder.
        if (input->data->vkStagingBuffer() != VK_NULL_HANDLE && input->queuedFrame != frame) {
            uploads->queueImportantWorldUpload(input->data);
            input->queuedFrame = frame;
        }
    }
    uploads->queueImportantWorldUpload(dispatch->parameters);
    if (!vertices) {
        dispatch->bind(*f, nullptr, nullptr, nullptr, 0, 0);
        surfaceFields_.push_back(dispatch);
    } else {
        auto data = EntityBuildData::create(
            identity, x, y, z, mcvr::abi::QUERY_WORLD, 0, -1, World::Coordinates::WORLD, 1,
            std::vector<World::GeometryTypes>{World::GeometryTypes::WORLD_TRANSPARENT},
            std::vector<std::string>{"source_surface"}, std::vector<std::string>{"source_surface"},
            std::vector<std::vector<vk::VertexFormat::PBRVertex>>(1), std::vector<std::vector<uint32_t>>(1),
            std::vector<uint32_t>{0}, world, frame, generation, stage, stageKind,
            std::vector<std::string>{"radiance:computed_surface"}, std::vector<std::string>{"default"});
        data->geometryMaterialFlags[0] = mcvr::faces::withAlphaMode(0, mcvr::abi::ALPHA_COVERAGE);
        data->geometryDrawColours = {glm::vec4(1.0f)};
        data->geometryEyeColours = {glm::vec4(1.0f)};
        data->rawGeometry.resize(1);
        auto &raw = data->rawGeometry[0];
        raw.computed = dispatch;
        raw.deferred = true;
        raw.parameters.vertexCount = vertices;
        raw.parameters.indexCount = indices;
        data->topologyPrepared = true;
        entityBuildDataBatch_->addData(data);
    }
    return true;
}
