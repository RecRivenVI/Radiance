#include "core/render/instancing.hpp"
#include "core/render/instancing_contract.hpp"

#include "core/render/buffers.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <glm/gtc/matrix_transform.hpp>
#include <glm/gtc/quaternion.hpp>
#include <glm/gtc/type_ptr.hpp>
#include <stdexcept>
#include <utility>

namespace {
struct CanonicalVertex {
    glm::vec3 position;
    glm::vec3 normal;
    glm::vec2 uv;
    glm::vec4 color;
    int32_t overlay;
    int32_t light;
};
static_assert(sizeof(CanonicalVertex) == 56);

constexpr uint32_t CRUMBLING = mcvr::abi::APPEARANCE_CRUMBLING;
constexpr uint32_t EMBEDDED = mcvr::abi::APPEARANCE_EMBEDDED;
constexpr uint32_t MATERIAL_POLYGON_OFFSET = mcvr::abi::SURFACE_POLYGON_OFFSET_BIT;
constexpr uint32_t MATERIAL_TRANSPARENCY_SHIFT = mcvr::abi::SURFACE_TRANSPARENCY_SHIFT;

std::pair<glm::mat4, InstancingAppearance> decode(const InstancingInstance &instance,
                                                  double renderTicks,
                                                  glm::ivec3 renderOrigin,
                                                  const mcvr::scene::SpacePose *space) {
    const auto &p = instance.parameters;
    glm::mat4 transform = glm::make_mat4(p.pose);
    glm::mat3 localNormal = glm::make_mat3(p.normal);
    InstancingAppearance appearance{};
    appearance.colorMultiply = glm::make_vec4(p.colorMultiply);
    appearance.colorReplace = glm::make_vec4(p.colorReplace);
    appearance.uv = glm::make_vec4(p.uv);
    appearance.shadow = glm::make_vec4(p.shadow);
    appearance.overlay = {p.overlay[0], p.overlay[1]};
    appearance.flags = p.flags;
    appearance.textureOverride = p.texture;
    appearance.fluidProgress = p.fluidProgress;
    if (p.motion & 1u) {
        const auto pivot = glm::make_vec3(p.pivot);
        const auto rotation = glm::rotate(
            glm::mat4(1),
            glm::radians(mcvr::instancing::rotationDegrees(p.initialDegrees, p.degreesPerSecond, renderTicks)),
            glm::normalize(glm::make_vec3(p.axis)));
        transform = glm::translate(glm::mat4(1), pivot) * rotation * glm::translate(glm::mat4(1), -pivot) * transform;
        localNormal = glm::mat3(rotation) * localNormal;
    }
    if (p.motion & 2u) {
        const auto phase =
            mcvr::instancing::scrollPhase(glm::make_vec2(p.uvSpeed), renderTicks, glm::make_vec2(p.uvPhase));
        const auto offset = glm::make_vec2(p.uvBase) + phase * glm::make_vec2(p.uvScale);
        appearance.uv.x = offset.x;
        appearance.uv.y = offset.y;
    }
    appearance.crumblingTransform = transform;
    appearance.crumblingNormal0 = glm::vec4(localNormal[0], 0.0f);
    appearance.crumblingNormal1 = glm::vec4(localNormal[1], 0.0f);
    appearance.crumblingNormal2 = glm::vec4(localNormal[2], 0.0f);
    transform = instance.embeddingPose * transform;
    auto objectPose = transform;
    auto objectNormal = instance.embeddingNormal * localNormal;
    if (space) {
        // Only the linear part is needed for normal correction; keep world translation in double.
        objectPose = glm::mat4(glm::mat3(space->matrix)) * transform;
        objectNormal = space->normal * objectNormal;
    }
    const auto correction = mcvr::instancing::normalCorrection(objectPose, objectNormal);
    appearance.normalCorrection0 = glm::vec4(correction[0], 0.0f);
    appearance.normalCorrection1 = glm::vec4(correction[1], 0.0f);
    appearance.normalCorrection2 = glm::vec4(correction[2], 0.0f);
    appearance.flags |= mcvr::abi::APPEARANCE_NORMAL_CORRECTION;
    if (instance.embedded) appearance.flags |= EMBEDDED;
    return {transform, appearance};
}
} // namespace

Instancing::Instancing(std::shared_ptr<Framework> framework) : framework_(std::move(framework)) {}

uint64_t Instancing::createEngine() {
    // Handles remain distinct across world recreation; a stale GAME handle cannot
    // accidentally address the first engine in a newly created native world.
    static std::atomic_uint64_t nextEngine{1};
    const uint64_t id = nextEngine++;
    auto child = std::make_shared<Instancing>(framework_);
    child->engineId_ = id;
    engines_.emplace(id, std::move(child));
    return id;
}

std::shared_ptr<Instancing> Instancing::engine(uint64_t id) {
    auto found = engines_.find(id);
    if (id == 0 || found == engines_.end()) throw std::runtime_error("Stale instance-scene engine handle");
    return found->second;
}

void Instancing::deleteEngine(uint64_t id) {
    auto found = engines_.find(id);
    if (found == engines_.end()) return;
    found->second->close();
    engines_.erase(found);
}

int Instancing::createModel(int meshCount) {
    if (meshCount < 0) throw std::runtime_error("Negative instance-scene mesh count");
    int id = nextModelId_++;
    auto model = std::make_shared<InstancingModel>();
    model->id = id;
    model->expectedMeshes = meshCount;
    model->vertices.resize(meshCount);
    model->indices.resize(meshCount);
    model->geometryTypes.resize(meshCount);
    model->groupNames.resize(meshCount);
    model->materialKeys.resize(meshCount);
    model->materialFlags.resize(meshCount);
    models_[id] = model;
    return id;
}

void Instancing::uploadModelMesh(int modelId,
                                 int meshIndex,
                                 const void *vertices,
                                 int vertexCount,
                                 const uint32_t *indices,
                                 int indexCount,
                                 int textureId,
                                 int alphaMode,
                                 int materialFlags,
                                 const char *materialKey) {
    auto model = models_.at(modelId);
    if (meshIndex < 0 || meshIndex >= model->expectedMeshes || vertices == nullptr || indices == nullptr ||
        vertexCount <= 0 || indexCount <= 0 || indexCount % 3 != 0)
        throw std::runtime_error("Invalid instance-scene mesh upload");
    const auto *source = static_cast<const CanonicalVertex *>(vertices);
    auto &out = model->vertices[meshIndex];
    out.resize(vertexCount);
    for (int i = 0; i < vertexCount; ++i) {
        auto &v = out[i];
        v.pos = source[i].position;
        if ((materialFlags & MATERIAL_POLYGON_OFFSET) != 0) v.pos += source[i].normal * 1.0e-4f;
        v.useNorm = 1;
        v.norm = source[i].normal;
        v.useColorLayer = 1;
        v.colorLayer = source[i].color;
        v.useTexture = textureId != 0;
        v.textureID = textureId;
        v.textureUV = source[i].uv;
        v.useOverlay = (materialFlags & 1) != 0;
        v.overlayUV = {source[i].overlay & 0xffff, (source[i].overlay >> 16) & 0xffff};
        v.useLight = (materialFlags & 2) != 0;
        v.lightUV = {source[i].light & 0xffff, (source[i].light >> 16) & 0xffff};
        v.coordinate = World::WORLD;
        v.alphaMode = alphaMode;
    }
    model->indices[meshIndex].assign(indices, indices + indexCount);
    // Keep Shared instance geometry non-opaque so per-geometry culling and material transparency
    // are evaluated by the hit shader instead of being lost in the instance-wide TLAS flags.
    model->geometryTypes[meshIndex] = World::WORLD_TRANSPARENT;
    const uint32_t transparency = (static_cast<uint32_t>(materialFlags) >> MATERIAL_TRANSPARENCY_SHIFT) & 0x7u;
    // Every built-in pack maps the existing "lightning" group to the shared
    // transparent-only any/closest-hit pair; there is no group literally named
    // "transparent_only" in the SBT config.
    model->groupNames[meshIndex] = transparency == 0 ? "default" : "lightning";
    model->materialKeys[meshIndex] = materialKey == nullptr ? "" : materialKey;
    model->materialFlags[meshIndex] = static_cast<uint32_t>(materialFlags);
}

void Instancing::finishModel(int modelId) {
    auto model = models_.at(modelId);
    if (model->expectedMeshes == 0) return;
    for (int i = 0; i < model->expectedMeshes; ++i) {
        if (model->vertices[i].empty() || model->indices[i].empty())
            throw std::runtime_error("Incomplete instance model");
    }
    std::vector<uint32_t> overlays(model->expectedMeshes, 0);
    std::vector<std::string> shaderKeys(model->expectedMeshes, "instance_surface");
    model->groupIds.clear();
    for (const auto &name : model->groupNames) model->groupIds.push_back(mcvr::hitgroups::intern(name));
    auto data = EntityBuildData::create(0, 0, 0, 0, 1, 0, -1, World::WORLD,
                                        static_cast<uint32_t>(model->expectedMeshes), std::move(model->geometryTypes),
                                        std::move(model->groupNames), std::vector<std::string>(model->materialKeys),
                                        std::move(model->vertices), std::move(model->indices), std::move(overlays), 0,
                                        0, 0, 0, 0, std::move(shaderKeys), std::move(model->materialKeys));
    data->geometryMaterialFlags = model->materialFlags;
    model->buildBatch = EntityBuildDataBatch::create();
    model->buildBatch->addData(data);
    model->buildBatch->build();
    // The build annotates exact thin-sheet pairs. Publish that provenance with
    // the model, rather than the pre-build source-only flags used for capture.
    model->materialFlags = data->geometryMaterialFlags;
    Renderer::instance().buffers()->queueImportantWorldUpload(model->buildBatch->indexBuffer);
    Renderer::instance().buffers()->queueImportantWorldUpload(model->buildBatch->positionBuffer);
    Renderer::instance().buffers()->queueImportantWorldUpload(model->buildBatch->materialBuffer);
    pendingBlasBuilders_.push_back(model->buildBatch->blasBatchBuilder);
    auto batch = EntityBatch::create(model->buildBatch);
    model->geometry = batch->entities.front();
}

void Instancing::deleteModel(int modelId) {
    auto found = models_.find(modelId);
    if (found == models_.end()) throw std::runtime_error("Unknown instance model");
    for (const auto &[id, instance] : instances_)
        if (instance.model == modelId) throw std::runtime_error("Instance model deleted while an instance uses it");
    // Frames already recorded may still trace its BLAS; a pending build keeps its own builder.
    framework_->frameResourceRetainer().retain(found->second);
    models_.erase(found);
}

void Instancing::updateInstance(uint64_t id,
                                int model,
                                int version,
                                int bias,
                                bool visible,
                                const void *data,
                                int dataSize,
                                const float *embeddingPose,
                                const float *embeddingNormal,
                                bool embedded) {
    if (!models_.contains(model) || data == nullptr || !mcvr::instancing::validInstanceBytes(version, dataSize) ||
        embeddingPose == nullptr || embeddingNormal == nullptr)
        throw std::runtime_error("Invalid instance-scene instance update");
    mcvr::scene::InstanceParameters parameters;
    std::memcpy(&parameters, data, sizeof(parameters));
    mcvr::scene::validateInstanceParameters(parameters);
    auto &instance = instances_[id];
    instance.id = id;
    instance.model = model;
    instance.bias = bias;
    instance.visible = visible;
    instance.embedded = embedded;
    instance.parameters = parameters;
    instance.embeddingPose = glm::make_mat4(embeddingPose);
    instance.embeddingNormal = glm::make_mat3(embeddingNormal);
}
void Instancing::deleteInstance(uint64_t id) {
    instances_.erase(id);
    crumbling_.erase(id);
}
void Instancing::beginFrame(glm::ivec3 origin, double ticks) {
    renderOrigin_ = origin;
    renderTicks_ = ticks;
    crumbling_.clear();
}
void Instancing::renderCrumbling(
    const uint64_t *ids, const int64_t *positions, const int32_t *progress, const int32_t *textures, int count) {
    for (int i = 0; i < count; ++i)
        if (instances_.contains(ids[i])) crumbling_[ids[i]] = {positions[i], progress[i], textures[i]};
}
std::vector<std::shared_ptr<vk::BLASBatchBuilder>> Instancing::drainPendingBlasBuilders() {
    auto builders = std::exchange(pendingBlasBuilders_, {});
    for (const auto &[id, child] : engines_) {
        auto pending = child->drainPendingBlasBuilders();
        builders.insert(builders.end(), pending.begin(), pending.end());
    }
    return builders;
}
std::vector<PreparedInstance> Instancing::preparedInstances() const {
    std::vector<PreparedInstance> out;
    for (const auto &[id, child] : engines_) {
        auto prepared = child->preparedInstances();
        out.insert(out.end(), prepared.begin(), prepared.end());
    }
    for (const auto &[id, instance] : instances_) {
        auto model = models_.at(instance.model);
        if (!instance.visible || model->geometry == nullptr) continue;
        const auto *space = instance.space ? &Renderer::instance().world()->spaces().at(instance.space) : nullptr;
        auto [transform, appearance] = decode(instance, renderTicks_, renderOrigin_, space);
        if (auto it = crumbling_.find(id); it != crumbling_.end()) {
            appearance.flags |= CRUMBLING;
            appearance.textureOverride = it->second.texture;
        }
        out.push_back({model, id, instance.bias, transform, renderOrigin_, appearance,
                       (appearance.flags & CRUMBLING) != 0, engineId_, instance.space});
    }
    std::stable_sort(out.begin(), out.end(), [](const auto &a, const auto &b) { return a.bias < b.bias; });
    return out;
}
size_t Instancing::residentModelCount() const {
    size_t count = models_.size();
    for (const auto &[id, child] : engines_) count += child->residentModelCount();
    return count;
}
void Instancing::close() {
    for (auto &[id, child] : engines_) child->close();
    engines_.clear();
    instances_.clear();
    models_.clear();
    pendingBlasBuilders_.clear();
    crumbling_.clear();
}
