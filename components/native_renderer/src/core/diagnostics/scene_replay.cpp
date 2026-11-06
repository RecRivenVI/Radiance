#include "core/diagnostics/scene_replay.hpp"
#include "core/diagnostics/scene_replay_contract.hpp"
#include "core/diagnostics/scene_snapshot.hpp"
#include "core/diagnostics/scene_replay_camera.hpp"
#include "core/diagnostics/sharc_audit.hpp"
#include "core/render/buffers.hpp"
#include "core/render/chunks.hpp"
#include "core/render/entities.hpp"
#include "core/render/pipeline.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"
#include "core/render/streamline_runtime.hpp"
#include "core/render/textures.hpp"
#include "core/render/world.hpp"
#include "core/render/scene_scope.hpp"
#include "core/render/static_chunk_scene.hpp"
#include "core/render/material_palette.hpp"
#include "common/render_abi.hpp"
#include "core/render/modules/world/ray_tracing/submodules/world_prepare.hpp"
#include "core/render/modules/world/shader_pack/shader_pack.hpp"
#include "core/logging.hpp"
#include <nlohmann/json.hpp>
#include <algorithm>
#include <fstream>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <limits>
#include <set>
#include <span>
#include <glm/gtc/packing.hpp>
#include "core/render/medium_contact_publication.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "mz.h"
#include "mz_strm.h"
#include "mz_zip.h"
#include "mz_zip_rw.h"
#ifdef _WIN32
#    define GLFW_EXPOSE_NATIVE_WIN32
#    include <GLFW/glfw3native.h>
#    include <psapi.h>
#endif

namespace mcvr::replay {
using Json = nlohmann::json;
namespace {
    std::mutex registryMutex;
    std::map<uint64_t, std::weak_ptr<vk::DeviceLocalBuffer>> registry;
    std::map<const vk::BLAS *, VkBuildAccelerationStructureFlagsKHR> blasFlags;
    std::atomic<bool> registrationFinished{false};
    std::vector<ChunkPackedData> capturedChunkLights;
    bool captureConfigured() noexcept {
        static const bool enabled = [] {
            const auto p = std::getenv("RADIANCE_SCENE_CAPTURE");
            return p && *p;
        }();
        return enabled;
    }
    const std::filesystem::path &captureRoot() {
        static const std::filesystem::path root = [] {
            const auto value = std::getenv("RADIANCE_SCENE_CAPTURE");
            return value ? std::filesystem::path(value) : std::filesystem::path{};
        }();
        return root;
    }
    void check(bool condition, const char *message) {
        if (!condition) throw std::runtime_error(message);
    }
    void write(const std::filesystem::path &file, const void *bytes, size_t count) {
        std::ofstream out(file, std::ios::binary);
        out.exceptions(std::ios::badbit | std::ios::failbit);
        out.write(static_cast<const char *>(bytes), count);
    }
    template <class T>
    Json raw(const T &value) {
        const auto p = reinterpret_cast<const uint8_t *>(&value);
        return std::vector<uint8_t>(p, p + sizeof(T));
    }
    template <class T>
    void decodeInto(const Json &value, T &result) {
        const auto bytes = value.get<std::vector<uint8_t>>();
        check(bytes.size() == sizeof(T), "Snapshot ABI differs from this replay build");
        std::memcpy(&result, bytes.data(), sizeof(T));
    }
    template <class T>
    T unraw(const Json &value) {
        // The Java render thread has a 1 MiB stack. Large frozen tables are
        // decoded into heap-owned storage, never returned through this helper.
        static_assert(sizeof(T) <= 4096);
        T result{};
        decodeInto(value, result);
        return result;
    }
    template <class T>
    const T &uniform(const std::shared_ptr<vk::HostVisibleBuffer> &buffer) {
        check(buffer && buffer->size() == sizeof(T), "Missing snapshot uniform buffer");
        return *static_cast<const T *>(buffer->mappedPtr());
    }
    std::vector<VkBufferImageCopy> regions(const std::shared_ptr<vk::DeviceLocalImage> &image, uint64_t &bytes) {
        check(image->depth() == 1 && image->layer() == 1, "Replay v1 requires 2D single-layer textures");
        std::vector<VkBufferImageCopy> result;
        const auto texel = vk::formatToByte(image->vkFormat());
        check(texel > 0, "Unsupported texture format in scene snapshot");
        bytes = 0;
        for (uint32_t mip = 0; mip < image->mipLevels(); ++mip) {
            bytes = (bytes + 3) & ~uint64_t(3);
            VkBufferImageCopy r{};
            r.bufferOffset = bytes;
            r.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, mip, 0, 1};
            r.imageExtent = {std::max(1u, image->width() >> mip), std::max(1u, image->height() >> mip), 1};
            bytes += uint64_t(r.imageExtent.width) * r.imageExtent.height * texel;
            result.push_back(r);
        }
        return result;
    }
    void transition(const std::shared_ptr<vk::CommandBuffer> &cmd,
                    const std::shared_ptr<vk::DeviceLocalImage> &image,
                    VkImageLayout from,
                    VkImageLayout to) {
        cmd->barriersBufferImage({}, {{.srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                       .srcAccessMask = from == VK_IMAGE_LAYOUT_UNDEFINED ?
                                                            0ull :
                                                            VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                       .dstStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                       .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                       .oldLayout = from,
                                       .newLayout = to,
                                       .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                       .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                       .image = image,
                                       .subresourceRange = image->fullSubresourceRange()}});
    }
    // Large readbacks must not borrow the upload-buffer policy: sequential-write
    // mapped allocations may reside in host-visible VRAM on resizable-BAR devices.
    // This local diagnostic requires non-device-local host memory and fails closed
    // if the device exposes no such memory type. Ordinary buffers are unchanged.
    struct SystemReadback {
        std::shared_ptr<vk::VMA> allocator;
        VkBuffer buffer = VK_NULL_HANDLE;
        VmaAllocation allocation = VK_NULL_HANDLE;
        VmaAllocationInfo info{};
        uint64_t byteSize;
        SystemReadback(const std::shared_ptr<Framework> &fw, uint64_t bytes) : allocator(fw->vma()), byteSize(bytes) {
            VkPhysicalDeviceMemoryProperties properties{};
            vkGetPhysicalDeviceMemoryProperties(fw->physicalDevice()->vkPhysicalDevice(), &properties);
            uint32_t types = 0;
            for (uint32_t i = 0; i < properties.memoryTypeCount; ++i) {
                const auto flags = properties.memoryTypes[i].propertyFlags;
                if ((flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) && !(flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))
                    types |= 1u << i;
            }
            check(types != 0, "Capture needs non-device-local host memory; no safe readback heap");
            VkBufferCreateInfo create{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
            create.size = bytes;
            create.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            VmaAllocationCreateInfo alloc{};
            alloc.usage = VMA_MEMORY_USAGE_AUTO_PREFER_HOST;
            alloc.flags = VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT;
            alloc.requiredFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
            alloc.preferredFlags = VK_MEMORY_PROPERTY_HOST_CACHED_BIT;
            alloc.memoryTypeBits = types;
            check(vmaCreateBuffer(allocator->allocator(), &create, &alloc, &buffer, &allocation, &info) == VK_SUCCESS,
                  "Cannot allocate system-memory scene readback");
        }
        ~SystemReadback() {
            if (buffer) vmaDestroyBuffer(allocator->allocator(), buffer, allocation);
        }
        VkBuffer vkBuffer() const {
            return buffer;
        }
        uint64_t size() const {
            return byteSize;
        }
        void *mappedPtr() const {
            return info.pMappedData;
        }
        void downloadFromBuffer() {
            check(vmaInvalidateAllocation(allocator->allocator(), allocation, 0, byteSize) == VK_SUCCESS,
                  "Cannot invalidate scene readback memory");
        }
    };
    struct Capture {
        std::filesystem::path root;
        Json manifest;
        std::shared_ptr<vk::Device> device;
        VkEvent done = VK_NULL_HANDLE;
        bool sealed = false;
        struct Readback {
            std::string name;
            std::shared_ptr<SystemReadback> buffer;
        };
        std::vector<Readback> readbacks;
        std::vector<std::shared_ptr<void>> owners;
        std::vector<std::shared_ptr<const void>> sceneOwners;
        uint64_t bytes = 0;
        uint64_t limit = captureByteLimit;
        VkCommandBuffer commands = VK_NULL_HANDLE;
        ~Capture() noexcept {
            // Retained until the actual frame fence retires. The event also prevents a
            // failed/unsubmitted frame or lost device from publishing a valid snapshot.
            try {
                check(sealed && vkGetEventStatus(device->vkDevice(), done) == VK_EVENT_SET,
                      "Scene capture did not complete on the GPU");
                SnapshotZip zip(root / "blobs.zip.partial");
                for (const auto &item : readbacks) {
                    item.buffer->downloadFromBuffer();
                    zip.add(item.name,
                            {static_cast<const uint8_t *>(item.buffer->mappedPtr()), size_t(item.buffer->size())});
                }
                zip.close();
                std::filesystem::rename(root / "blobs.zip.partial", root / "blobs.zip");
                manifest["readback_bytes"] = bytes;
                const auto data = manifest.dump(2);
                write(root / "scene.json.partial", data.data(), data.size());
                std::filesystem::rename(root / "scene.json.partial", root / "scene.json");
                mcvr::log::info("SceneReplay")
                    << "GPU snapshot completed: " << root.string() << " bytes=" << bytes << std::endl;
            } catch (const std::exception &e) {
                try {
                    if (!std::filesystem::exists(root / "capture-error.txt"))
                        std::ofstream(root / "capture-error.txt") << e.what();
                } catch (...) {}
            }
            if (done) vkDestroyEvent(device->vkDevice(), done, nullptr);
        }
        std::shared_ptr<SystemReadback>
        readback(const std::string &name, uint64_t size, const std::shared_ptr<Framework> &fw) {
            addBytes(bytes, size, limit);
            auto result = std::make_shared<SystemReadback>(fw, size);
            readbacks.push_back({name, result});
            return result;
        }
    };
    std::weak_ptr<Capture> currentCapture;
    struct ReplayOpacity {
        struct Geometry {
            VkMicromapUsageEXT usage{};
            VkAccelerationStructureTrianglesOpacityMicromapEXT info{
                VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_TRIANGLES_OPACITY_MICROMAP_EXT};
        };
        std::shared_ptr<vk::Device> device;
        std::shared_ptr<vk::DeviceLocalBuffer> storage;
        VkMicromapEXT handle = VK_NULL_HANDLE;
        std::map<std::pair<size_t, size_t>, Geometry> geometries;

        ~ReplayOpacity() {
            if (handle) vkDestroyMicromapEXT(device->vkDevice(), handle, nullptr);
        }
    };
    struct Replay {
        Json manifest;
        std::filesystem::path root;
        std::unique_ptr<SnapshotReader> archive;
        std::unique_ptr<vk::Data::TextureMapping> mapping;
        std::shared_ptr<vk::HostVisibleBuffer> mappingBuffer;
        vk::Data::WorldUBO capturedWorld{};
        vk::Data::SkyUBO capturedSky{};
        std::vector<std::shared_ptr<vk::DeviceLocalBuffer>> buffers;
        std::vector<AddressRange> ranges;
        std::unique_ptr<ReplayOpacity> opacity;
        std::vector<std::shared_ptr<vk::BLAS>> blases;
        std::shared_ptr<vk::TLAS> frozenTlas;
        mcvr::hitgroups::Layout hitLayout;
        struct Instance {
            VkTransformMatrixKHR transform;
            uint32_t custom, mask, sbt, flags;
            size_t blas;
        };
        std::vector<Instance> instances;
        std::vector<uint32_t> offsets;
        std::vector<glm::uvec2> sampleKeys;
        std::vector<uint64_t> indices, positions, materials;
        std::vector<glm::mat4> previous;
        std::vector<glm::mat4> capturedTransforms;
        std::unique_ptr<ReplayCamera> camera;
        glm::dvec3 previousCameraOffset{0.0};
        std::vector<vk::VertexFormat::InstanceAppearance> appearances;
        std::vector<std::string> hitGroups;
        std::vector<glm::vec4> water;
        std::shared_ptr<vk::DeviceLocalBuffer> contacts;
        std::shared_ptr<vk::DeviceLocalBuffer> chunkLights;
        uint64_t sampleFrame = 0;
        uint32_t seedOffset = 0;
        vk::Data::WorldUBO previousUniform{};
        bool wantImage = false;
        std::shared_ptr<SystemReadback> imageReadback;
        VkFormat imageFormat = VK_FORMAT_UNDEFINED;
        bool initialized = false;
        // Setup ownership survives exceptions until run() synchronizes/handles device loss.
        std::shared_ptr<vk::CommandBuffer> setupCommand;
        std::shared_ptr<vk::Fence> setupFence;
        std::vector<std::shared_ptr<void>> setupOwners;
        std::vector<std::shared_ptr<void>> opacityBuildOwners;
    };
    std::unique_ptr<Replay> replay;
    uint64_t replayBudget() {
        const uint64_t limit = replay->manifest.value("readback_budget", captureByteLimit);
        check(limit > 0 && limit <= maximumCaptureBytes, "Invalid snapshot memory budget");
        return limit;
    }
    std::vector<uint8_t> readBlob(const Json &record) {
        auto name = record.at("file").get<std::string>();
        check(std::filesystem::path(name).filename() == std::filesystem::path(name), "Invalid snapshot blob path");
        const uint64_t size = record.at("bytes");
        check(size > 0 && size <= maximumCaptureBytes, "Invalid snapshot blob size");
        return replay->archive->read(name, size);
    }
} // namespace

bool captureEnabled() noexcept {
    // getenv does not allocate. Avoid registry/metadata work in ordinary clients.
    return captureConfigured() && !registrationFinished.load(std::memory_order_relaxed);
}
bool active() noexcept {
    return bool(replay);
}
void registerBlasFlags(const vk::BLAS *blas, VkBuildAccelerationStructureFlagsKHR flags) {
    std::lock_guard lock(registryMutex);
    blasFlags[blas] = flags;
}
void registerBuffer(uint64_t address, const std::shared_ptr<vk::DeviceLocalBuffer> &buffer) {
    if (registrationFinished.load(std::memory_order_relaxed)) return;
    std::lock_guard lock(registryMutex);
    if (registrationFinished.load(std::memory_order_relaxed)) return;
    registry[address] = buffer;
    // A live large scene can permanently exceed 8192 entries. Do not walk the
    // whole map for every address lookup in that case.
    static RegistryPruneBudget prune;
    if (prune.due(registry.size())) std::erase_if(registry, [](const auto &p) { return p.second.expired(); });
}

void captureChunkLights(std::span<const ChunkPackedData> table) {
    if (captureEnabled() && !SceneRecordingScope::active() &&
        std::filesystem::exists(captureRoot() / "capture.request"))
        capturedChunkLights.assign(table.begin(), table.end());
}

std::shared_ptr<vk::DeviceLocalBuffer> replayChunkLights() {
    return replay ? replay->chunkLights : nullptr;
}

std::shared_ptr<vk::HostVisibleBuffer> replayTextureMapping() {
    return replay ? replay->mappingBuffer : nullptr;
}

void capture(WorldPrepareContext &context,
             const vk::TLASBuilder::TLASInstanceBuilder &instances,
             const std::vector<uint32_t> &offsets,
             const std::vector<uint64_t> &dynamicIndices,
             const std::vector<uint64_t> &dynamicPositions,
             const std::vector<uint64_t> &dynamicMaterials,
             const std::vector<vk::VertexFormat::InstanceAppearance> &dynamicAppearances,
             std::span<const std::byte> contacts,
             const std::vector<glm::vec4> &water,
             const std::shared_ptr<const mcvr::StaticChunkScene> &staticScene,
             size_t staticPrefix,
             std::span<const glm::uvec2> sampleKeys,
             const mcvr::RigidSceneColumns::Publication &rigid) {
    if (!captureEnabled()) return;
    if (SceneRecordingScope::active()) return;
    static bool consumed = false;
    if (consumed || !std::filesystem::exists(captureRoot() / "capture.request")) return;
    // A request arriving after resetFrame must wait for the matching uploaded light table.
    if (Renderer::options.collectChunkEmission && capturedChunkLights.empty()) return;
    consumed = true;
    registrationFinished.store(true, std::memory_order_relaxed);
    auto fw = Renderer::instance().framework();
    auto cmd = context.frameworkContext.lock()->worldCommandBuffer;
    auto cap = std::make_shared<Capture>();
    cap->root = captureRoot();
    cap->device = fw->device();
    cap->commands = cmd->vkCommandBuffer();
    currentCapture = cap;
    fw->frameResourceRetainer().retain(cap);
    try {
        cap->limit = captureBudget(std::getenv("RADIANCE_SCENE_CAPTURE_MAX_MIB"));
        std::filesystem::rename(cap->root / "capture.request", cap->root / "capture.accepted");
        check(!Renderer::options.dlssFrameGeneration, "Replay v1 comparison requires FG disabled in capture");
        const auto post = Renderer::instance().world()->entities()->entityPostBatch();
        check(!post || post->entities.empty(), "Replay v1 cannot omit post-raster world geometry");
        auto flatten = [&](const auto &values, auto member, size_t column) {
            using T = typename std::decay_t<decltype(values)>::value_type;
            auto table =
                staticScene ? staticScene->table<T>(values, staticPrefix, member) : mcvr::sceneTable<T>(values);
            table.leading = rigid[column];
            std::vector<T> result(table.size() / sizeof(T));
            table.write(std::as_writable_bytes(std::span(result)));
            return result;
        };
        const auto indices = flatten(dynamicIndices, &mcvr::StaticChunkPage::indices, 0);
        const auto positions = flatten(dynamicPositions, &mcvr::StaticChunkPage::positions, 1);
        const auto materials = flatten(dynamicMaterials, &mcvr::StaticChunkPage::materials, 2);
        const auto appearances = flatten(dynamicAppearances, &mcvr::StaticChunkPage::appearances, 5);
        auto &m = cap->manifest;
        m = {{"version", 2},
             {"scope", "static PT TLAS; fixed camera; independent temporal warmup; no GUI"},
             {"pipeline", Json::parse(fw->pipeline()->worldPipelineBlueprint()->replayDescription())},
             {"runtime_root", Renderer::folderPath.string()},
             {"options", raw(Renderer::options)},
             {"width", fw->swapchain()->vkExtent().width},
             {"height", fw->swapchain()->vkExtent().height},
             {"offsets", offsets},
             {"indices", indices},
             {"positions", positions},
             {"materials", materials},
             {"hit_groups", Json::array()}};
        for (auto id : context.hitGroups) m["hit_groups"].push_back(mcvr::hitgroups::Registry::global().name(id));
        if (!sampleKeys.empty()) {
            auto &keys = m["sample_keys"] = Json::array();
            for (const auto &key : sampleKeys) keys.push_back({key.x, key.y});
        }
        m["medium_contacts"] =
            std::vector<uint8_t>(reinterpret_cast<const uint8_t *>(contacts.data()),
                                 reinterpret_cast<const uint8_t *>(contacts.data()) + contacts.size());
        for (auto item : water) m["water_exclusions"].push_back(raw(item));
        cap->sceneOwners = instances.owners;
        const auto b = Renderer::instance().buffers();
        const auto world = uniform<vk::Data::WorldUBO>(b->worldUniformBuffer());
        check(world.lodGeometry == 0, "Snapshot v2 has no LoD coverage table; disable Voxy for this capture");
        m["world"] = raw(world);
        std::ifstream identity(cap->root / "capture-identity.json");
        check(bool(identity), "Capture needs capture-identity.json from the replay preparation command");
        identity >> m["source"];
        m["format"] = "radiance-native-scene-v2: local ABI, ZIP/Deflate blobs";
        m["sky"] = raw(uniform<vk::Data::SkyUBO>(b->skyUniformBuffer()));
        m["mapping"] = raw(uniform<vk::Data::TextureMapping>(b->textureMappingBuffer()));
        m["readback_budget"] = cap->limit;
        m["settings"] = {{"bounces", Renderer::options.rayBounces},
                         {"fg", Renderer::options.dlssFrameGeneration},
                         {"denoiser", Renderer::options.denoiserMode},
                         {"dlssMode", Renderer::options.dlssMode},
                         {"upscalerQuality", Renderer::options.upscalerQuality},
                         {"maxFps", Renderer::options.maxFps},
                         {"inactiveFps", Renderer::options.inactivityFpsLimit}};
        m["readback_heap"] = "host-visible non-device-local";
        // ToneMapping owns its exposure/history resources. The legacy Buffers
        // exposure slot is not an input to this graph and is normally absent.
        for (auto &a : appearances) m["appearances"].push_back(raw(a));
        std::map<const vk::BLAS *, size_t> blasIds;
        std::set<uint64_t> needed(indices.begin(), indices.end());
        needed.insert(positions.begin(), positions.end());
        needed.insert(materials.begin(), materials.end());
        if (Renderer::options.collectChunkEmission) {
            auto &table = m["chunk_lights"] = Json::array();
            for (const auto &chunk : capturedChunkLights) {
                table.push_back(
                    {chunk.x, chunk.y, chunk.z, chunk.geometryCount, chunk.lightCount, chunk.lightBufferAddress});
                if (chunk.lightBufferAddress) needed.insert(chunk.lightBufferAddress);
            }
            std::vector<ChunkPackedData>().swap(capturedChunkLights);
        }
        for (size_t instance = 0; instance < instances.instances.size(); ++instance) {
            const auto &item = instances.instances[instance];
            if (item.accelerationStructureReference == 0) continue;
            const auto &transform = item.transform;
            const uint32_t custom = item.instanceCustomIndex, mask = item.mask,
                           sbt = item.instanceShaderBindingTableRecordOffset, flags = item.flags;
            const auto *blas = instances.instanceModels.at(instance);
            auto [it, fresh] = blasIds.emplace(blas, blasIds.size());
            if (fresh) {
                check(!blas->captureGeometries.empty(), "BLAS predates capture enablement");
                Json geometries = Json::array();
                for (size_t i = 0; i < blas->captureGeometries.size(); ++i) {
                    const auto &g = blas->captureGeometries[i];
                    const auto &t = g.geometry.triangles;
                    check(g.geometryType == VK_GEOMETRY_TYPE_TRIANGLES_KHR && !g.pNext && !t.pNext &&
                              t.transformData.deviceAddress == 0,
                          "Unsupported BLAS geometry in snapshot");
                    needed.insert(t.vertexData.deviceAddress);
                    needed.insert(t.indexData.deviceAddress);
                    geometries.push_back({{"flags", g.flags},
                                          {"vertices", t.vertexData.deviceAddress},
                                          {"indices", t.indexData.deviceAddress},
                                          {"stride", t.vertexStride},
                                          {"max_vertex", t.maxVertex},
                                          {"format", t.vertexFormat},
                                          {"index_type", t.indexType},
                                          {"primitives", blas->capturePrimitiveCounts.at(i)}});
                }
                m["blases"].push_back(geometries);
                {
                    std::lock_guard lock(registryMutex);
                    m["blas_flags"].push_back(blasFlags.at(blas));
                }
            }
            m["instances"].push_back({{"transform", raw(transform)},
                                      {"custom", custom},
                                      {"mask", mask},
                                      {"sbt", sbt},
                                      {"flags", flags},
                                      {"blas", it->second}});
        }
        for (size_t offset = 0; offset + sizeof(medium::Filter) <= contacts.size(); offset += sizeof(medium::Filter)) {
            medium::Filter filter;
            std::memcpy(&filter, contacts.data() + offset, sizeof(filter));
            if (filter.primitives) needed.insert(filter.primitives);
            if (filter.neighbors) needed.insert(filter.neighbors);
        }
        for (const auto &a : appearances) {
            const uint64_t address = uint64_t(a.surfaceLayerAddressLo) | (uint64_t(a.surfaceLayerAddressHi) << 32);
            if (address) needed.insert(address);
        }
        // Resolve only live owners of addresses actually submitted in this frame.
        std::map<uint64_t, std::shared_ptr<vk::DeviceLocalBuffer>> sources;
        {
            std::lock_guard lock(registryMutex);
            for (auto address : needed) {
                if (!address) continue;
                auto [base, owner] = resolveOwner(registry, address);
                sources.emplace(base, std::move(owner));
            }
            registry.clear();
            blasFlags.clear();
        }
        auto textures = Renderer::instance().textures();
        std::lock_guard textureLock(textures->mtx_);
        uint64_t planned = 0;
        for (const auto &[address, source] : sources) addBytes(planned, source->size(), cap->limit);
        for (const auto &[id, source] : textures->textures_) {
            if (!source || textures->isFramebufferTexture(id) || source->imageLayout() == VK_IMAGE_LAYOUT_UNDEFINED ||
                source->fullSubresourceRange().aspectMask != VK_IMAGE_ASPECT_COLOR_BIT)
                continue;
            uint64_t bytes;
            regions(source, bytes);
            addBytes(planned, bytes, cap->limit);
        }
        // Reserve at least RGBA32F-sized output before allocating any readback.
        addBytes(planned, uint64_t(fw->swapchain()->vkExtent().width) * fw->swapchain()->vkExtent().height * 16,
                 cap->limit);
        check(std::filesystem::space(cap->root).available >= planned, "Insufficient snapshot disk space");
#ifdef _WIN32
        MEMORYSTATUSEX memory{sizeof(MEMORYSTATUSEX)};
        check(GlobalMemoryStatusEx(&memory) && planned <= memory.ullAvailPhys / 4,
              "Capture exceeds one quarter of available physical system memory");
#endif
        m["preflight_bytes_reserved"] = planned;
        mcvr::log::info("SceneReplay") << "Readback preflight: system_memory=true reserved_bytes=" << planned
                                       << " buffers=" << sources.size() << " instances=" << instances.instances.size()
                                       << std::endl;
        cmd->barriersMemory({{VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT, VK_ACCESS_2_MEMORY_WRITE_BIT,
                              VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_READ_BIT}});
        for (auto &[address, source] : sources) {
            const auto name = "buffer-" + std::to_string(m["buffers"].size()) + ".bin";
            auto dest = cap->readback(name, source->size(), fw);
            VkBufferCopy region{0, 0, source->size()};
            vkCmdCopyBuffer(cmd->vkCommandBuffer(), source->vkBuffer(), dest->vkBuffer(), 1, &region);
            cap->owners.push_back(source);
            m["buffers"].push_back({{"address", address}, {"bytes", source->size()}, {"file", name}});
        }
        m["fallbacks"] = textures->releasedTextureFallbacks_;
        for (auto &[id, source] : textures->textures_) {
            if (!source || textures->isFramebufferTexture(id) || source->imageLayout() == VK_IMAGE_LAYOUT_UNDEFINED ||
                source->fullSubresourceRange().aspectMask != VK_IMAGE_ASPECT_COLOR_BIT) {
                m["excluded_texture_ids"].push_back(id);
                continue;
            }
            uint64_t bytes;
            auto copies = regions(source, bytes);
            const auto name = "texture-" + std::to_string(id) + ".bin";
            auto dest = cap->readback(name, bytes, fw);
            const auto layout = source->imageLayout();
            transition(cmd, source, layout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            vkCmdCopyImageToBuffer(cmd->vkCommandBuffer(), source->vkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                   dest->vkBuffer(), static_cast<uint32_t>(copies.size()), copies.data());
            transition(cmd, source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, layout);
            auto sampler = textures->sampler(id);
            check(sampler != nullptr, "Snapshot texture has no sampler");
            cap->owners.push_back(source);
            cap->owners.push_back(sampler);
            m["textures"].push_back({{"id", id},
                                     {"width", source->width()},
                                     {"height", source->height()},
                                     {"format", source->vkFormat()},
                                     {"mips", source->mipLevels()},
                                     {"bytes", bytes},
                                     {"file", name},
                                     {"filter", sampler->vkSamplingMode()},
                                     {"mipmap", sampler->vkMipmapMode()},
                                     {"address", sampler->vkAddressMode()}});
        }
        VkEventCreateInfo event{VK_STRUCTURE_TYPE_EVENT_CREATE_INFO};
        check(vkCreateEvent(cap->device->vkDevice(), &event, nullptr, &cap->done) == VK_SUCCESS,
              "Capture event creation failed");
        cmd->barriersMemory({{VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                              VK_PIPELINE_STAGE_2_HOST_BIT, VK_ACCESS_2_HOST_READ_BIT}});
        vkCmdSetEvent(cmd->vkCommandBuffer(), cap->done, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        cap->sealed = true;
        mcvr::log::info("SceneReplay") << "Recorded snapshot readbacks; awaiting actual GPU completion. instances="
                                       << instances.instances.size() << " unique_blas=" << blasIds.size() << std::endl;
    } catch (const std::exception &e) {
        std::ofstream(cap->root / "capture-error.txt") << e.what();
        mcvr::log::error("SceneReplay") << "Snapshot rejected: " << e.what() << std::endl;
    }
}

void captureOutput(const std::shared_ptr<vk::CommandBuffer> &cmd, const std::shared_ptr<vk::DeviceLocalImage> &image) {
    if (SceneRecordingScope::active()) return;
    if (replay && replay->wantImage) {
        replay->wantImage = false;
        auto fw = Renderer::instance().framework();
        uint64_t bytes;
        auto copies = regions(image, bytes);
        if (!replay->imageReadback || replay->imageReadback->size() != bytes)
            replay->imageReadback = std::make_shared<SystemReadback>(fw, bytes);
        replay->imageFormat = image->vkFormat();
        const auto layout = image->imageLayout();
        transition(cmd, image, layout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        vkCmdCopyImageToBuffer(cmd->vkCommandBuffer(), image->vkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                               replay->imageReadback->vkBuffer(), uint32_t(copies.size()), copies.data());
        transition(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, layout);
        cmd->barriersMemory({{VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                              VK_PIPELINE_STAGE_2_HOST_BIT, VK_ACCESS_2_HOST_READ_BIT}});
        return;
    }
    if (!captureConfigured()) return;
    auto cap = currentCapture.lock();
    if (!cap || !cap->sealed || cap->commands != cmd->vkCommandBuffer() || cap->manifest.contains("reference_image"))
        return;
    try {
        auto fw = Renderer::instance().framework();
        uint64_t bytes;
        auto copies = regions(image, bytes);
        auto dest = cap->readback("reference-image.bin", bytes, fw);
        const auto layout = image->imageLayout();
        transition(cmd, image, layout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        vkCmdCopyImageToBuffer(cmd->vkCommandBuffer(), image->vkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                               dest->vkBuffer(), static_cast<uint32_t>(copies.size()), copies.data());
        transition(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, layout);
        cmd->barriersMemory({{VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
                              VK_PIPELINE_STAGE_2_HOST_BIT, VK_ACCESS_2_HOST_READ_BIT}});
        cap->owners.push_back(image);
        cap->manifest["reference_image"] = {{"file", "reference-image.bin"},
                                            {"width", image->width()},
                                            {"height", image->height()},
                                            {"format", image->vkFormat()},
                                            {"bytes", bytes}};
    } catch (const std::exception &e) {
        cap->sealed = false;
        try {
            std::ofstream(cap->root / "capture-error.txt") << e.what();
        } catch (...) {}
        // Already recorded copies and their owners still retire with this frame.
    }
}

namespace {
    void prepareReplayOpacity(const std::shared_ptr<Framework> &fw, const std::shared_ptr<vk::CommandBuffer> &cmd) {
        const char *input = std::getenv("RADIANCE_REPLAY_OPACITY");
        if (!input) return;
        auto device = fw->device();
        check(device->opacityMicromapEnabled(), "Replay opacity micromap unsupported");
        if (std::string_view(input) == "plain") return;
        auto vma = fw->vma();
        const std::filesystem::path directory(input);
        Json manifest;
        std::ifstream(directory / "opacity.json") >> manifest;
        check(manifest.at("version") == 1 && manifest.at("sceneIdentity") == replay->manifest.at("source"),
              "Replay opacity micromap belongs to a different scene");
        const auto patterns = manifest.at("patterns").get<std::vector<uint32_t>>();
        check(manifest.at("level") == 2 && !patterns.empty() && patterns.size() <= UINT16_MAX,
              "Replay opacity micromap patterns are outside the supported range");
        std::ifstream indicesFile(directory / "references.bin", std::ios::binary | std::ios::ate);
        const std::streamoff bytes = indicesFile.tellg();
        check(bytes > 0 && bytes <= 64 * 1024 * 1024 && bytes % sizeof(int32_t) == 0,
              "Invalid replay opacity index stream size");
        std::vector<int32_t> indices(size_t(bytes) / sizeof(int32_t));
        indicesFile.seekg(0);
        indicesFile.read(reinterpret_cast<char *>(indices.data()), bytes);
        check(bool(indicesFile), "Incomplete replay opacity index stream");
        const auto &blases = replay->manifest.at("blases");
        const auto &bindings = manifest.at("geometries");
        check(bindings.is_array() && !bindings.empty(), "Missing replay opacity bindings");
        for (const auto &row : bindings) {
            auto [b, g, offset, count, regular, sourceAddress] = row.get<std::array<uint64_t, 6>>();
            const auto &geometry = blases.at(b).at(g);
            check(geometry.at("primitives") == count && geometry.at("indices") == sourceAddress &&
                      offset <= indices.size() && count <= indices.size() - offset,
                  "Replay opacity binding does not match its geometry");
            uint64_t matched = 0;
            for (int32_t index : std::span(indices).subspan(offset, count)) {
                check(index == VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_TRANSPARENT_EXT ||
                          index == VK_OPACITY_MICROMAP_SPECIAL_INDEX_FULLY_UNKNOWN_OPAQUE_EXT ||
                          (index >= 0 && size_t(index) < patterns.size()),
                      "Invalid replay opacity index");
                matched += index >= 0;
            }
            check(matched == regular, "Replay opacity usage count disagrees with indices");
        }

        auto &opacity = replay->opacity = std::make_unique<ReplayOpacity>();
        opacity->device = device;
        VkPhysicalDeviceAccelerationStructurePropertiesKHR asProperties{
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR};
        VkPhysicalDeviceOpacityMicromapPropertiesEXT properties{
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_OPACITY_MICROMAP_PROPERTIES_EXT, &asProperties};
        VkPhysicalDeviceProperties2 physical{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, &properties};
        vkGetPhysicalDeviceProperties2(fw->physicalDevice()->vkPhysicalDevice(), &physical);
        check(properties.maxOpacity4StateSubdivisionLevel >= 2, "Replay opacity subdivision unsupported");

        auto stage = [&](const void *data, size_t size, VkBufferUsageFlags usage, VkDeviceSize alignment) {
            auto buffer = vk::HostVisibleBuffer::create(vma, device, size,
                                                        usage | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, alignment);
            std::memcpy(buffer->mappedPtr(), data, size);
            buffer->flush();
            replay->opacityBuildOwners.push_back(buffer);
            return buffer->bufferAddress();
        };
        constexpr auto inputUsage = VK_BUFFER_USAGE_MICROMAP_BUILD_INPUT_READ_ONLY_BIT_EXT;
        constexpr auto indexUsage = VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
        constexpr auto format = VK_OPACITY_MICROMAP_FORMAT_4_STATE_EXT;
        const VkMicromapUsageEXT total{uint32_t(patterns.size()), 2, format};
        std::vector<VkMicromapTriangleEXT> triangles(patterns.size());
        for (uint32_t i = 0; i < triangles.size(); ++i) triangles[i] = {i * uint32_t(sizeof(uint32_t)), 2, format};
        VkMicromapBuildInfoEXT build{VK_STRUCTURE_TYPE_MICROMAP_BUILD_INFO_EXT};
        build.type = VK_MICROMAP_TYPE_OPACITY_MICROMAP_EXT;
        build.mode = VK_BUILD_MICROMAP_MODE_BUILD_EXT;
        build.usageCountsCount = 1;
        build.pUsageCounts = &total;
        build.data.deviceAddress = stage(patterns.data(), patterns.size() * sizeof(uint32_t), inputUsage, 256);
        build.triangleArray.deviceAddress =
            stage(triangles.data(), triangles.size() * sizeof(VkMicromapTriangleEXT), inputUsage, 256);
        build.triangleArrayStride = sizeof(VkMicromapTriangleEXT);
        VkMicromapBuildSizesInfoEXT sizes{VK_STRUCTURE_TYPE_MICROMAP_BUILD_SIZES_INFO_EXT};
        vkGetMicromapBuildSizesEXT(device->vkDevice(), VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR, &build, &sizes);
        check(sizes.micromapSize != 0, "Replay opacity micromap size is zero");
        opacity->storage =
            vk::DeviceLocalBuffer::create(vma, device, sizes.micromapSize, VK_BUFFER_USAGE_MICROMAP_STORAGE_BIT_EXT);
        VkMicromapCreateInfoEXT create{VK_STRUCTURE_TYPE_MICROMAP_CREATE_INFO_EXT};
        create.buffer = opacity->storage->vkBuffer();
        create.size = sizes.micromapSize;
        create.type = VK_MICROMAP_TYPE_OPACITY_MICROMAP_EXT;
        if (auto result = vkCreateMicromapEXT(device->vkDevice(), &create, nullptr, &opacity->handle);
            result != VK_SUCCESS) {
            device->recordFailure(result, "vkCreateMicromapEXT(scene replay)");
            throw std::runtime_error("Replay opacity micromap creation failed");
        }
        build.dstMicromap = opacity->handle;
        if (sizes.buildScratchSize) {
            auto scratch = vk::DeviceLocalBuffer::create(
                vma, device, false, sizes.buildScratchSize,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, 0,
                VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, asProperties.minAccelerationStructureScratchOffsetAlignment);
            build.scratchData.deviceAddress = scratch->bufferAddress();
            replay->opacityBuildOwners.push_back(scratch);
        }
        vkCmdBuildMicromapsEXT(cmd->vkCommandBuffer(), 1, &build);
        cmd->barriersMemory(
            {{VK_PIPELINE_STAGE_2_MICROMAP_BUILD_BIT_EXT, VK_ACCESS_2_MICROMAP_WRITE_BIT_EXT,
              VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_ACCESS_2_MICROMAP_READ_BIT_EXT}});
        auto address = stage(indices.data(), size_t(bytes), indexUsage, alignof(int32_t));
        for (const auto &row : bindings) {
            auto [b, g, offset, count, regular, sourceAddress] = row.get<std::array<uint64_t, 6>>();
            auto [it, inserted] = opacity->geometries.try_emplace({b, g});
            check(inserted, "Duplicate replay opacity binding");
            auto &binding = it->second;
            binding.usage = {uint32_t(regular), 2, format};
            binding.info.indexType = VK_INDEX_TYPE_UINT32;
            binding.info.indexBuffer.deviceAddress = address + offset * sizeof(int32_t);
            binding.info.indexStride = sizeof(int32_t);
            binding.info.usageCountsCount = regular ? 1u : 0u;
            binding.info.pUsageCounts = regular ? &binding.usage : nullptr;
            binding.info.micromap = opacity->handle;
        }
        mcvr::log::info("SceneReplay") << "Opacity micromap geometries=" << bindings.size()
                                       << " mapBytes=" << sizes.micromapSize << " indexBytes=" << bytes << std::endl;
    }

    void prepareReplayGeometry(const std::shared_ptr<Framework> &fw) {
        auto device = fw->device();
        auto vma = fw->vma();
        auto pool = vk::CommandPool::create(fw->physicalDevice(), device);
        auto cmd = replay->setupCommand = vk::CommandBuffer::create(device, pool);
        auto fence = replay->setupFence = vk::Fence::create(device, false);
        cmd->begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        const auto &m = replay->manifest;
        constexpr uint64_t uploadBatchLimit = 64ull * 1024 * 1024;
        uint64_t batchBytes = 0;
        size_t batchBlases = 0, stagingBegin = 0;
        auto flush = [&] {
            if (!batchBytes && !batchBlases) return;
            cmd->barriersMemory(
                {{VK_PIPELINE_STAGE_2_TRANSFER_BIT | VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
                  VK_ACCESS_2_TRANSFER_WRITE_BIT | VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
                  VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                      VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                  VK_ACCESS_2_MEMORY_READ_BIT}});
            cmd->end();
            check(cmd->submitMainQueueIndividual(device, fence) == VK_SUCCESS, "Replay setup submission failed");
            const auto result = vkWaitForFences(device->vkDevice(), 1, &fence->vkFence(), VK_TRUE, 10'000'000'000ull);
            if (result != VK_SUCCESS) {
                device->recordFailure(result, "vkWaitForFences(scene replay setup)");
                throw std::runtime_error("Replay setup completion failed: " + std::to_string(result));
            }
            // Reuse only after this batch's real fence, not a frame-count guess.
            for (; stagingBegin < replay->buffers.size(); ++stagingBegin)
                replay->buffers[stagingBegin]->releaseStaging();
            replay->setupOwners.clear();
            check(vkResetFences(device->vkDevice(), 1, &fence->vkFence()) == VK_SUCCESS,
                  "Replay setup fence reset failed");
            cmd->reset()->begin(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            batchBytes = 0;
            batchBlases = 0;
        };
        uint64_t total = 0;
        std::map<uint64_t, uint32_t> layerRanges;
        for (const auto &appearance : replay->appearances) {
            const uint64_t address =
                uint64_t(appearance.surfaceLayerAddressLo) | (uint64_t(appearance.surfaceLayerAddressHi) << 32);
            if (address) layerRanges[address] = std::max(layerRanges[address], appearance.surfaceLayerPrimitiveCount);
        }
        for (const auto &b : m.at("buffers")) {
            auto bytes = readBlob(b);
            addBytes(total, bytes.size(), replayBudget());
            if (batchBytes && bytes.size() > uploadBatchLimit - std::min(batchBytes, uploadBatchLimit)) flush();
            auto gpu = vk::DeviceLocalBuffer::create(
                vma, device, true, bytes.size(),
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR);
            const uint64_t oldBase = b.at("address"), newBase = gpu->bufferAddress();
            std::set<size_t> patched;
            for (auto layer = layerRanges.lower_bound(oldBase);
                 layer != layerRanges.end() && layer->first - oldBase < bytes.size(); ++layer) {
                const auto [address, count] = *layer;
                for (uint32_t n = 0; n < count; ++n) {
                    size_t offset = size_t(address - oldBase) + size_t(n) * sizeof(vk::VertexFormat::SurfaceLayerRange);
                    check(offset + 16 <= bytes.size(), "Surface layer range outside snapshot buffer");
                    if (!patched.insert(offset).second) continue;
                    vk::VertexFormat::SurfaceLayerRange range;
                    std::memcpy(&range, bytes.data() + offset, sizeof(range));
                    if (!range.count) continue;
                    uint64_t target = uint64_t(range.addressLo) | (uint64_t(range.addressHi) << 32);
                    check(target >= oldBase && target - oldBase <= bytes.size() &&
                              uint64_t(range.count) * 256 <= bytes.size() - (target - oldBase),
                          "Surface layer records outside snapshot buffer");
                    target = newBase + (target - oldBase);
                    range.addressLo = uint32_t(target);
                    range.addressHi = uint32_t(target >> 32);
                    std::memcpy(bytes.data() + offset, &range, sizeof(range));
                }
            }
            gpu->uploadToStagingBuffer(bytes.data());
            gpu->uploadToBuffer(cmd);
            replay->ranges.push_back({b.at("address"), bytes.size(), gpu->bufferAddress()});
            replay->buffers.push_back(gpu);
            batchBytes += bytes.size();
            if (batchBytes >= uploadBatchLimit) flush();
        }
        std::ranges::sort(replay->ranges, {}, &AddressRange::oldBase);
        if (Renderer::options.collectChunkEmission) {
            std::vector<ChunkPackedData> table;
            const auto &rows = m.at("chunk_lights");
            check(rows.is_array() && !rows.empty() && rows.size() <= UINT32_MAX / sizeof(ChunkPackedData),
                  "Invalid captured chunk light table");
            table.reserve(rows.size());
            for (const auto &row : rows) {
                check(row.is_array() && row.size() == 6, "Invalid captured chunk light row");
                ChunkPackedData entry{row.at(0).get<int32_t>(),  row.at(1).get<int32_t>(),  row.at(2).get<int32_t>(),
                                      row.at(3).get<uint32_t>(), row.at(4).get<uint32_t>(), row.at(5).get<uint64_t>()};
                if (entry.lightCount) {
                    constexpr size_t lightStride = sizeof(glm::vec4) * 7;
                    entry.lightBufferAddress =
                        relocate(replay->ranges, entry.lightBufferAddress, uint64_t(entry.lightCount) * lightStride);
                }
                table.push_back(entry);
            }
            const auto bytes = table.size() * sizeof(ChunkPackedData);
            replay->chunkLights =
                vk::DeviceLocalBuffer::create(vma, device, true, bytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            replay->chunkLights->uploadToStagingBuffer(table.data());
            replay->chunkLights->uploadToBuffer(cmd);
            replay->buffers.push_back(replay->chunkLights);
            batchBytes += bytes;
        }
        cmd->barriersMemory(
            {{VK_PIPELINE_STAGE_2_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT,
              VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                  VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
              VK_ACCESS_2_MEMORY_READ_BIT}});
        flush();
        prepareReplayOpacity(fw, cmd);
        size_t blasIndex = 0;
        for (const auto &geometries : m.at("blases")) {
            auto builder = vk::BLASBuilder::create();
            auto geo = builder->beginGeometries();
            for (const auto &g : geometries) {
                uint32_t count = g.at("primitives");
                if (!count) {
                    geo->definePlaceholderGeometry();
                    continue;
                }
                VkAccelerationStructureGeometryKHR result{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
                result.geometryType = VK_GEOMETRY_TYPE_TRIANGLES_KHR;
                result.flags = g.at("flags");
                auto &t = result.geometry.triangles;
                t.sType = VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR;
                t.vertexFormat = static_cast<VkFormat>(g.at("format").get<int>());
                t.indexType = static_cast<VkIndexType>(g.at("index_type").get<int>());
                t.vertexStride = g.at("stride");
                t.maxVertex = g.at("max_vertex");
                check(t.vertexFormat == VK_FORMAT_R32G32B32_SFLOAT && t.indexType == VK_INDEX_TYPE_UINT32,
                      "Unsupported replay vertex/index format");
                t.vertexData.deviceAddress =
                    relocate(replay->ranges, g.at("vertices"), uint64_t(t.maxVertex) * t.vertexStride + 12);
                t.indexData.deviceAddress = relocate(replay->ranges, g.at("indices"), uint64_t(count) * 3 * 4);
                if (replay->opacity) {
                    const auto binding = replay->opacity->geometries.find({blasIndex, geo->geometries.size()});
                    if (binding != replay->opacity->geometries.end()) t.pNext = &binding->second.info;
                }
                geo->geometries.push_back(result);
                geo->primitiveCounts.push_back(count);
            }
            // BLAS quality is fixed per process, so build-flag A/B runs compare separate replay processes.
            uint32_t buildFlags = m.at("blas_flags").at(blasIndex++).get<uint32_t>();
            if (const char *forced = std::getenv("RADIANCE_REPLAY_FAST_BUILD_FLAGS");
                forced && (buildFlags & VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_BUILD_BIT_KHR) != 0u)
                buildFlags = static_cast<uint32_t>(std::stoul(forced));
            auto blas = geo->endGeometries()
                            ->defineBuildProperty(buildFlags)
                            ->querySizeInfo(device)
                            ->allocateBuffers(fw->physicalDevice(), device, vma)
                            ->buildAndSubmit(device, cmd);
            replay->blases.push_back(blas);
            replay->setupOwners.push_back(builder);
            if (++batchBlases >= 64) flush();
        }
        flush();
        // Indirection buffers are build inputs, no longer needed by the finished BLASes.
        replay->opacityBuildOwners.clear();
        if (replay->opacity) replay->opacity->geometries.clear();

        // Static file parsing/relocation is setup work, not renderer frame work.
        for (const auto &i : m.at("instances")) {
            const auto transform = unraw<VkTransformMatrixKHR>(i.at("transform"));
            replay->instances.push_back(
                {transform, i.at("custom"), i.at("mask"), i.at("sbt"), i.at("flags"), i.at("blas")});
            glm::mat4 matrix(1.0f);
            for (int row = 0; row < 3; ++row)
                for (int col = 0; col < 4; ++col) matrix[col][row] = transform.matrix[row][col];
            // Shader history is indexed by instanceCustomIndex, not TLAS array order.
            if (replay->capturedTransforms.size() <= replay->instances.back().custom)
                replay->capturedTransforms.resize(size_t(replay->instances.back().custom) + 1, glm::mat4(1));
            replay->capturedTransforms[replay->instances.back().custom] = matrix;
        }
        auto remap = [&](const char *key) {
            auto values = m.at(key).get<std::vector<uint64_t>>();
            for (auto &v : values)
                if (v) v = relocate(replay->ranges, v);
            return values;
        };
        replay->offsets = m.at("offsets").get<std::vector<uint32_t>>();
        if (auto keys = m.find("sample_keys"); keys != m.end()) {
            check(keys->size() == replay->offsets.size(), "Snapshot sample keys disagree with instance slots");
            for (const auto &key : *keys)
                replay->sampleKeys.emplace_back(key.at(0).get<uint32_t>(), key.at(1).get<uint32_t>());
        }
        replay->indices = remap("indices");
        replay->positions = remap("positions");
        replay->materials = remap("materials");
        for (auto &a : replay->appearances) {
            uint64_t address = uint64_t(a.surfaceLayerAddressLo) | (uint64_t(a.surfaceLayerAddressHi) << 32);
            if (address) {
                address = relocate(replay->ranges, address, uint64_t(a.surfaceLayerPrimitiveCount) * 16);
                a.surfaceLayerAddressLo = uint32_t(address);
                a.surfaceLayerAddressHi = uint32_t(address >> 32);
            }
        }
        replay->hitGroups = m.at("hit_groups").get<std::vector<std::string>>();
        // The capture records this optional domain only when it contains a boat
        // exclusion. An ordinary empty world domain has no entries to restore.
        if (auto exclusions = m.find("water_exclusions"); exclusions != m.end())
            for (const auto &w : *exclusions) replay->water.push_back(unraw<glm::vec4>(w));
        auto contactBytes = m.at("medium_contacts").get<std::vector<uint8_t>>();
        for (size_t offset = 0; offset + sizeof(medium::Filter) <= contactBytes.size();
             offset += sizeof(medium::Filter)) {
            medium::Filter f;
            std::memcpy(&f, contactBytes.data() + offset, sizeof(f));
            if (f.primitives) f.primitives = relocate(replay->ranges, f.primitives);
            if (f.neighbors) f.neighbors = relocate(replay->ranges, f.neighbors, uint64_t(f.neighborCount) * 32);
            std::memcpy(contactBytes.data() + offset, &f, sizeof(f));
        }
        check(contactBytes.size() >= replay->indices.size() * sizeof(medium::Filter),
              "Missing per-geometry contact metadata");
        replay->contacts =
            vk::DeviceLocalBuffer::create(vma, device, true, contactBytes.size(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        replay->contacts->uploadToStagingBuffer(contactBytes.data());
        replay->contacts->uploadToBuffer(cmd);
        replay->setupOwners.push_back(replay->contacts);
        batchBytes += contactBytes.size();
        flush();
        replay->previous = replay->capturedTransforms;
        replay->setupCommand.reset();
        replay->setupFence.reset();
        replay->initialized = true;
        mcvr::log::info("SceneReplay") << "Rebuilt snapshot BLAS once in bounded setup batches: "
                                       << replay->blases.size() << std::endl;
    }
} // namespace

// Standalone replay calls this seam instead of the Minecraft scene producer.
// It still uses the ordinary WorldPrepare metadata, SBT and downstream modules.
bool render(WorldPrepareContext &context) {
    if (!replay) return false;
    auto fw = Renderer::instance().framework();
    auto device = fw->device();
    auto vma = fw->vma();
    auto cmd = context.frameworkContext.lock()->worldCommandBuffer;
    auto &m = replay->manifest;
    check(replay->initialized, "Replay geometry was not prepared before rendering");
    cmd->barriersMemory(
        {{VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
          VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR}});
    if (!replay->frozenTlas) {
        context.tlasBuilder = vk::TLASBuilder::create();
        auto &instances = context.tlasBuilder->beginInstanceBuilder();
        // Captured transforms are camera relative, so this measures the GPU cost of far non-chunk instances.
        const char *radiusText = std::getenv("RADIANCE_REPLAY_NON_CHUNK_RADIUS");
        const float nonChunkRadius = radiusText ? std::stof(radiusText) : std::numeric_limits<float>::infinity();
        size_t droppedInstances = 0;
        for (const auto &i : replay->instances) {
            const bool chunk = (m.at("blas_flags").at(i.blas).get<uint32_t>() &
                                VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_BUILD_BIT_KHR) != 0u;
            const glm::vec3 offset(i.transform.matrix[0][3], i.transform.matrix[1][3], i.transform.matrix[2][3]);
            if (!chunk && glm::length(offset) > nonChunkRadius) {
                ++droppedInstances;
                continue;
            }
            instances.defineInstance(i.transform, i.custom, i.mask, i.sbt, i.flags, replay->blases.at(i.blas));
        }
        if (radiusText)
            std::printf("Replay dropped %zu of %zu instances\n", droppedInstances, replay->instances.size());
        replay->frozenTlas = instances.endInstanceBuilder(device, vma)
                                 ->defineBuildProperty(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                                 ->querySizeInfo(device)
                                 ->allocateBuffers(fw->physicalDevice(), device, vma)
                                 ->buildAndSubmit(device, cmd);
    }
    context.tlas = replay->frozenTlas;
    cmd->barriersMemory(
        {{VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
          VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR, VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR}});
    if (!replay->hitLayout) {
        auto ids = std::make_shared<std::vector<mcvr::hitgroups::Id>>();
        for (const auto &name : replay->hitGroups) ids->push_back(mcvr::hitgroups::intern(name));
        replay->hitLayout = ids;
    }
    if (context.hitGroupLayout != replay->hitLayout) {
        context.hitGroups = *replay->hitLayout;
        context.hitGroupLayout = replay->hitLayout;
    }
    context.uploadBuffer(replay->offsets, replay->indices, replay->positions, replay->materials, replay->indices,
                         replay->positions, replay->previous, replay->appearances, replay->water, nullptr, 0,
                         replay->sampleKeys, {});
    context.mediumContactBuffer = replay->contacts;
    return true;
}

namespace {
    void validateSnapshot() {
        const auto &m = replay->manifest;
        std::map<uint64_t, std::vector<uint8_t>> cpu;
        uint64_t total = 0;
        for (const auto &b : m.at("buffers")) {
            const uint64_t address = b.at("address"), size = b.at("bytes");
            addBytes(total, size, replayBudget());
            auto next = cpu.lower_bound(address);
            check(address != 0 && size <= std::numeric_limits<uint64_t>::max() - address &&
                      (next == cpu.end() || size <= next->first - address) &&
                      (next == cpu.begin() || std::prev(next)->second.size() <= address - std::prev(next)->first),
                  "Overlapping or invalid captured buffer range");
            cpu.emplace_hint(next, address, readBlob(b));
        }
        auto spanAt = [&](uint64_t address, uint64_t size) -> std::span<const uint8_t> {
            if (auto next = cpu.upper_bound(address); next != cpu.begin()) {
                const auto &[base, data] = *std::prev(next);
                if (address - base <= data.size() && size <= data.size() - (address - base))
                    return {data.data() + address - base, size};
            }
            throw std::runtime_error("Snapshot shader/AS input exceeds a captured buffer");
        };
        std::set<uint32_t> textures;
        for (const auto &t : m.at("textures")) {
            textures.insert(t.at("id").get<uint32_t>());
            addBytes(total, t.at("bytes"), replayBudget());
        }
        const auto fallbacks = m.at("fallbacks").get<std::map<uint32_t, uint32_t>>();
        auto needTexture = [&](uint32_t id) {
            const auto original = id;
            for (size_t n = 0; !textures.contains(id) && n <= fallbacks.size(); ++n) {
                auto it = fallbacks.find(id);
                if (it == fallbacks.end()) break;
                id = it->second;
            }
            if (!textures.contains(id))
                throw std::runtime_error("Scene uses an uncaptured texture " + std::to_string(original));
        };
        const auto &mapping = *replay->mapping;
        std::set<uint32_t> checkedMaterials;
        auto needMaterialTexture = [&](uint32_t id) {
            check(id < 4096, "Snapshot texture index exceeds descriptor capacity");
            if (!checkedMaterials.insert(id).second) return;
            needTexture(id);
            const auto &entry = mapping.entries[id];
            if (entry.normal >= 0) needTexture(entry.normal);
            if (entry.specular >= 0) needTexture(entry.specular);
            if (entry.flag >= 0) needTexture(entry.flag);
        };
        for (const auto &program : mapping.programs) {
            const auto type = program.settings.x;
            if (type == 1) {
                if (program.textures.x >= 0) needTexture(program.textures.x);
                if (program.textures.y >= 0) needTexture(program.textures.y);
            }
            if (type == 2) {
                for (auto id : {program.textures.x, program.textures.z, program.textures.w})
                    if (id >= 0) needTexture(id);
                if (program.settings.z) needTexture(program.settings.z);
            }
            if ((type == 3 || type == 4) && program.textures.y >= 0) needTexture(program.textures.y);
            check(type <= 4, "Snapshot requires an unknown world material program");
        }
        auto needVertex = [&](const vk::VertexFormat::MaterialVertex &v) {
            if (v.packedData & (1u << 1)) needMaterialTexture(v.textureID);
            if (v.packedData & (1u << 3)) needTexture(v.glintTexture);
            if (v.emissiveOverlayTextureID) needTexture(v.emissiveOverlayTextureID & mcvr::abi::EYE_TEXTURE_MASK);
        };
        const auto offsets = m.at("offsets").get<std::vector<uint32_t>>();
        const auto indices = m.at("indices").get<std::vector<uint64_t>>();
        const auto positions = m.at("positions").get<std::vector<uint64_t>>();
        const auto materials = m.at("materials").get<std::vector<uint64_t>>();
        check(indices.size() == positions.size() && indices.size() == materials.size() &&
                  indices.size() == replay->appearances.size(),
              "Snapshot geometry metadata arrays disagree");
        for (const auto &i : m.at("instances")) {
            const auto custom = i.at("custom").get<size_t>();
            const auto &geometries = m.at("blases").at(i.at("blas").get<size_t>());
            check(custom < offsets.size() && offsets[custom] <= indices.size() &&
                      geometries.size() <= indices.size() - offsets[custom],
                  "Invalid snapshot instance geometry range");
            for (size_t g = 0; g < geometries.size(); ++g) {
                const auto &geometry = geometries[g];
                const uint64_t count = geometry.at("primitives");
                if (!count) continue;
                const auto slot = offsets[custom] + g;
                const uint64_t maxVertex = geometry.at("max_vertex");
                check(indices[slot] == geometry.at("indices") && positions[slot] == geometry.at("vertices"),
                      "Snapshot shader geometry and AS input disagree");
                auto indexData = spanAt(indices[slot], count * 12);
                const bool dictionary = (materials[slot] & mcvr::materialPalette::addressTag) != 0;
                const auto materialAddress = materials[slot] & ~mcvr::materialPalette::addressTag;
                std::span<const uint8_t> materialIds;
                std::span<const uint8_t> materialData;
                uint32_t paletteSize = 0;
                if (dictionary) {
                    std::array<uint32_t, 4> header;
                    std::memcpy(header.data(), spanAt(materialAddress, sizeof(header)).data(), sizeof(header));
                    check(header[1] > maxVertex && uint64_t(header[0]) * 4 >= 16 + uint64_t(header[1]) * 4,
                          "Snapshot material dictionary header is inconsistent");
                    paletteSize = header[2];
                    materialIds = spanAt(materialAddress + 16, (maxVertex + 1) * 4);
                    materialData = spanAt(materialAddress + uint64_t(header[0]) * 4,
                                          uint64_t(paletteSize) * sizeof(vk::VertexFormat::MaterialVertex));
                } else {
                    materialData = spanAt(materialAddress, (maxVertex + 1) * sizeof(vk::VertexFormat::MaterialVertex));
                }
                spanAt(positions[slot], maxVertex * geometry.at("stride").get<uint64_t>() + 12);
                for (size_t p = 0; p < indexData.size(); p += 4) {
                    uint32_t vertex;
                    std::memcpy(&vertex, indexData.data() + p, 4);
                    check(vertex <= maxVertex, "Snapshot index exceeds BLAS maxVertex");
                    if (dictionary) {
                        std::memcpy(&vertex, materialIds.data() + size_t(vertex) * 4, 4);
                        check(vertex < paletteSize, "Snapshot material palette index exceeds its table");
                    }
                    vk::VertexFormat::MaterialVertex v;
                    std::memcpy(&v, materialData.data() + size_t(vertex) * sizeof(v), sizeof(v));
                    needVertex(v);
                }
                const auto &appearance = replay->appearances.at(slot);
                if (appearance.textureOverride) needMaterialTexture(appearance.textureOverride);
                const auto layerAddress =
                    uint64_t(appearance.surfaceLayerAddressLo) | (uint64_t(appearance.surfaceLayerAddressHi) << 32);
                if (layerAddress) {
                    auto ranges = spanAt(layerAddress, uint64_t(appearance.surfaceLayerPrimitiveCount) * 16);
                    for (size_t offset = 0; offset < ranges.size(); offset += 16) {
                        vk::VertexFormat::SurfaceLayerRange range;
                        std::memcpy(&range, ranges.data() + offset, sizeof(range));
                        if (!range.count) continue;
                        auto records = spanAt(uint64_t(range.addressLo) | (uint64_t(range.addressHi) << 32),
                                              uint64_t(range.count) * 256);
                        for (size_t at = 0; at < records.size(); at += 256) {
                            vk::VertexFormat::SurfaceLayerTriangle record;
                            std::memcpy(&record, records.data() + at, sizeof(record));
                            for (const auto &v : record.vertices) needVertex(v);
                        }
                    }
                }
            }
        }
        auto world = unraw<vk::Data::WorldUBO>(m.at("world"));
        auto sky = unraw<vk::Data::SkyUBO>(m.at("sky"));
        for (auto id : {world.overlayTextureID, world.lightMapTextureID, world.endSkyTextureID,
                        world.endPortalTextureID, sky.sunTextureID, sky.moonTextureID})
            if (id) needTexture(id);
        for (int a = 0; a < 3; ++a)
            if (world.cameraEffectTextureIDs[a] >= 0) needTexture(world.cameraEffectTextureIDs[a]);
        mcvr::log::info("SceneReplay")
            << "Validated every referenced triangle/index/material buffer and texture; instances="
            << m.at("instances").size() << " geometries=" << indices.size() << " captured_bytes=" << total << std::endl;
    }
    void buildBlueprint(const Json &description, const std::shared_ptr<Pipeline> &pipeline) {
        auto names = description.at("modules").get<std::vector<std::string>>();
        auto inputs = description.at("inputs").get<std::vector<std::vector<int>>>();
        auto outputs = description.at("outputs").get<std::vector<std::vector<int>>>();
        auto attributes = description.at("attributes").get<std::vector<std::vector<std::string>>>();
        auto formats = description.at("formats").get<std::vector<int>>();
        std::vector<char *> namePointers;
        std::vector<int *> inputPointers, outputPointers;
        std::vector<int> counts;
        std::vector<std::vector<char *>> attrPointers;
        std::vector<char **> kvPointers;
        for (size_t i = 0; i < names.size(); ++i) {
            check(Pipeline::worldModuleInOutImageNums.contains(names[i]), "Snapshot requires an unavailable module");
            auto arity = Pipeline::worldModuleInOutImageNums.at(names[i]);
            check(inputs.at(i).size() == arity.first && outputs.at(i).size() == arity.second,
                  "Snapshot module interface changed");
            check(attributes.at(i).size() % 2 == 0, "Invalid snapshot module attributes");
            namePointers.push_back(names[i].data());
            inputPointers.push_back(inputs[i].data());
            outputPointers.push_back(outputs[i].data());
            counts.push_back(static_cast<int>(attributes[i].size() / 2));
            auto &pointers = attrPointers.emplace_back();
            for (auto &a : attributes[i]) pointers.push_back(a.data());
        }
        for (auto &a : attrPointers) kvPointers.push_back(a.data());
        WorldPipelineBuildParams params{static_cast<int>(names.size()),
                                        0,
                                        namePointers.data(),
                                        formats.data(),
                                        inputPointers.data(),
                                        outputPointers.data(),
                                        counts.data(),
                                        kvPointers.data()};
        pipeline->buildWorldPipelineBlueprint(&params);
    }
    void loadTextures(const std::shared_ptr<Framework> &fw) {
        auto cmd = fw->safeAcquireCurrentContext()->uploadCommandBuffer;
        auto textures = Renderer::instance().textures();
        uint64_t total = 0;
        for (const auto &record : replay->manifest.at("textures")) {
            const auto id = record.at("id").get<uint32_t>();
            check(id < 4096, "Invalid snapshot texture slot");
            auto bytes = readBlob(record);
            addBytes(total, bytes.size(), replayBudget());
            auto image = vk::DeviceLocalImage::create(
                fw->device(), fw->vma(), false, record.at("mips").get<uint32_t>(), record.at("width").get<uint32_t>(),
                record.at("height").get<uint32_t>(), 1u, static_cast<VkFormat>(record.at("format").get<int>()),
                VK_IMAGE_USAGE_SAMPLED_BIT, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT,
                VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            uint64_t expected;
            auto copies = regions(image, expected);
            check(expected == bytes.size(), "Snapshot texture dimensions/format disagree with bytes");
            auto staging =
                vk::HostVisibleBuffer::create(fw->vma(), fw->device(), bytes.size(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            staging->uploadToBuffer(bytes.data());
            fw->frameResourceRetainer().retain(staging);
            transition(cmd, image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            vkCmdCopyBufferToImage(cmd->vkCommandBuffer(), staging->vkBuffer(), image->vkImage(),
                                   VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, static_cast<uint32_t>(copies.size()),
                                   copies.data());
            transition(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            image->imageLayout() = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            textures->textures_[id] = image;
            textures->samplers[id] =
                vk::Sampler::create(fw->device(), static_cast<VkFilter>(record.at("filter").get<int>()),
                                    static_cast<VkSamplerMipmapMode>(record.at("mipmap").get<int>()),
                                    static_cast<VkSamplerAddressMode>(record.at("address").get<int>()));
        }
        textures->releasedTextureFallbacks_ = replay->manifest.at("fallbacks").get<std::map<uint32_t, uint32_t>>();
        textures->bindAllTextures();
    }
    void frameUniforms() {
        auto b = Renderer::instance().buffers();
        const auto &captured = replay->capturedWorld;
        auto world = captured;
        glm::mat4 conversion(1.0f);
        conversion[1][1] = -1;
        conversion[2][2] = .5f;
        conversion[3][2] = .5f;
        world.cameraProjMat = glm::inverse(conversion) * world.cameraProjMat;
        world.cameraViewMat = replay->camera->view();
        world.cameraEffectedViewMat = replay->camera->effectedView();
        Renderer::instance().world()->setCameraPos(replay->camera->position());
        b->setAndUploadWorldUniformBuffer(world);
        // The ordinary setter allocates frame-owned buffers but derives random seed,
        // chunk state and jitter from the game. A replay restores frozen inputs and
        // advances one deterministic sequence, identically after every A/B reset.
        world.seed = captured.seed + replay->seedOffset + uint32_t(replay->sampleFrame) * 747796405u;
        world.samplingSeed = captured.samplingSeed + replay->seedOffset;
        world.samplingFrameIndex = captured.samplingFrameIndex + uint32_t(replay->sampleFrame);
        world.chunkGridInfo = captured.chunkGridInfo;
        world.chunkStorageSectionPos = captured.chunkStorageSectionPos;
        auto radical = [](uint32_t n, uint32_t base) {
            float value = 0, weight = 1;
            while (n) {
                weight /= base;
                value += float(n % base) * weight;
                n /= base;
            }
            return value;
        };
        world.cameraJitter =
            replay->sampleFrame ?
                glm::vec2(radical(uint32_t(replay->sampleFrame), 2), radical(uint32_t(replay->sampleFrame), 3)) - .5f :
                captured.cameraJitter;
        b->worldUniformBuffer()->uploadToBuffer(&world);
        b->lastWorldUniformBuffer()->uploadToBuffer(replay->sampleFrame ? &replay->previousUniform : &world);
        replay->previousUniform = world;
        auto sky = replay->capturedSky;
        b->setAndUploadSkyUniformBuffer(sky);
    }

} // namespace

namespace {
    std::ofstream *gpuOutput = nullptr;
    std::ofstream *hostOutput = nullptr;
    std::ofstream *counterOutput = nullptr;

    Json windowResources(const std::shared_ptr<Framework> &framework) {
        VkPhysicalDeviceMemoryProperties properties{};
        vkGetPhysicalDeviceMemoryProperties(framework->physicalDevice()->vkPhysicalDevice(), &properties);
        VmaBudget budgets[VK_MAX_MEMORY_HEAPS]{};
        vmaGetHeapBudgets(framework->vma()->allocator(), budgets);
        Json heaps = Json::array();
        for (uint32_t index = 0; index < properties.memoryHeapCount; ++index) {
            const auto &budget = budgets[index];
            heaps.push_back(
                {{"device_local", bool(properties.memoryHeaps[index].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT)},
                 {"vma_allocation_bytes", budget.statistics.allocationBytes},
                 {"vma_block_bytes", budget.statistics.blockBytes},
                 {"estimated_heap_usage_bytes", budget.usage}});
        }
        Json resources{{"heaps", std::move(heaps)}};
#ifdef _WIN32
        const auto process = GetCurrentProcess();
        PROCESS_MEMORY_COUNTERS_EX counters{};
        counters.cb = sizeof(counters);
        if (K32GetProcessMemoryInfo(process, reinterpret_cast<PROCESS_MEMORY_COUNTERS *>(&counters), counters.cb)) {
            resources["working_set_bytes"] = counters.WorkingSetSize;
            resources["private_bytes"] = counters.PrivateUsage;
        }
        FILETIME created{}, exited{}, kernel{}, user{};
        if (GetProcessTimes(process, &created, &exited, &kernel, &user)) {
            const auto ticks = [](const FILETIME &time) {
                return (uint64_t(time.dwHighDateTime) << 32u) | time.dwLowDateTime;
            };
            resources["process_cpu_100ns"] = ticks(kernel) + ticks(user);
        }
#endif
        return resources;
    }

    void observeProfile(uint64_t frame, uint32_t domain, const char *label, uint64_t ns, uint64_t) {
        if (gpuOutput && (domain == 2 || domain == 3 || domain == 4))
            *gpuOutput << frame << ',' << domain << ',' << label << ',' << double(ns) / 1e6 << '\n';
        if (counterOutput && domain == 5) {
            const std::string_view stage(label);
            if (stage == "sharc.sampled.pixels" || stage.starts_with("shadow.sampled."))
                *counterOutput << frame << ',' << stage << ',' << ns << '\n';
        }
        if (hostOutput && domain == 0) {
            const std::string_view stage(label);
            if (stage == "swapchain-acquire" || stage == "frame-fence-wait" || stage == "queue-present" ||
                stage == "queue-submit" || stage == "frame-limiter")
                *hostOutput << frame << ',' << stage << ',' << double(ns) / 1e6 << '\n';
        }
    }
} // namespace
int run(const std::filesystem::path &snapshot,
        const std::filesystem::path &planPath,
        const std::filesystem::path &output) {
    check(!Renderer::is_initialized(), "Replay requires a standalone process");
    check(!std::filesystem::exists(output), "Replay output exists; use a new result directory");
    std::filesystem::create_directories(output);
    replay = std::make_unique<Replay>();
    replay->root = std::filesystem::absolute(snapshot);
    std::ifstream source(replay->root / "scene.json");
    source >> replay->manifest;
    const auto &m = replay->manifest;
    check(m.at("version") == 2, "Unsupported snapshot format/ABI");
    replay->mapping = std::make_unique<vk::Data::TextureMapping>();
    decodeInto(m.at("mapping"), *replay->mapping);
    replay->capturedWorld = unraw<vk::Data::WorldUBO>(m.at("world"));
    replay->capturedSky = unraw<vk::Data::SkyUBO>(m.at("sky"));
    replay->appearances.reserve(m.at("appearances").size());
    for (const auto &record : m.at("appearances"))
        replay->appearances.push_back(unraw<vk::VertexFormat::InstanceAppearance>(record));
    replay->archive = std::make_unique<SnapshotReader>(replay->root / "blobs.zip");
    validateSnapshot();
    std::ifstream planFile(planPath);
    Json plan;
    planFile >> plan;
    check(plan.is_array() && !plan.empty(), "Replay plan must contain windows");
    Renderer::folderPath = replay->root / "runtime";
    check(std::filesystem::is_directory(Renderer::folderPath / "shaders"), "Snapshot runtime/shaders is missing");
    Renderer::options = unraw<Options>(m.at("options"));
    Renderer::options.needRecreate = false;
    Renderer::options.presentationChanged = false;
    check(!Renderer::options.dlssFrameGeneration, "Unsupported FG snapshot");
    check(!Renderer::options.collectChunkEmission || m.contains("chunk_lights"),
          "Snapshot omits required chunk lights");
    p_glfwInit = glfwInit;
    p_glfwTerminate = glfwTerminate;
    p_glfwGetWindowSize = glfwGetWindowSize;
    p_glfwGetWindowAttrib = glfwGetWindowAttrib;
    p_glfwCreateWindowSurface = glfwCreateWindowSurface;
    p_glfwGetRequiredInstanceExtensions = glfwGetRequiredInstanceExtensions;
    p_glfwSetWindowTitle = glfwSetWindowTitle;
    p_glfwSetFramebufferSizeCallback = [](GLFWwindow *w, GLFWframebuffersizefun f) {
        glfwSetFramebufferSizeCallback(w, f);
    };
    p_glfwGetFramebufferSize = glfwGetFramebufferSize;
    p_glfwWaitEvents = glfwWaitEvents;
    p_glfwWaitEventsTimeout = glfwWaitEventsTimeout;
    p_glfwWindowShouldClose = glfwWindowShouldClose;
#ifdef _WIN32
    p_glfwGetWin32Window = glfwGetWin32Window;
#endif
    check(glfwInit() == GLFW_TRUE, "GLFW init failed");
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
    glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
    glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
    auto *window = glfwCreateWindow(m.at("width"), m.at("height"), "Radiance scene replay", nullptr, nullptr);
    check(window != nullptr, "Cannot create replay window");
    auto close = [&] {
        gpuOutput = nullptr;
        hostOutput = nullptr;
        counterOutput = nullptr;
        if (Renderer::is_initialized()) {
            auto fw = Renderer::instance().framework();
            if (fw && !fw->isDeviceLost()) fw->waitDeviceIdle();
            replay.reset();
            Renderer::instance().close();
        }
        glfwDestroyWindow(window);
        glfwTerminate();
    };
    try {
        Renderer::init(window);
        auto fw = Renderer::instance().framework();
        // The snapshot's material table is immutable across frames and variants.
        replay->mappingBuffer = vk::HostVisibleBuffer::create(fw->vma(), fw->device(), sizeof(vk::Data::TextureMapping),
                                                              VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        replay->mappingBuffer->uploadToBuffer(replay->mapping.get());
        check(fw->swapchain()->vkExtent().width == m.at("width") &&
                  fw->swapchain()->vkExtent().height == m.at("height"),
              "Replay framebuffer differs from capture resolution");
        Pipeline::collectWorldModules();
        std::ofstream frames(output / "frames.csv"), gpu(output / "gpu.csv"), host(output / "host.csv"), counters;
        gpuOutput = &gpu;
        hostOutput = &host;
        if (mcvr::diagnostics::SharcAudit::enabled()) {
            counters.open(output / "shadow-counts.csv");
            check(bool(counters), "Cannot write replay shadow counters");
            counters << "frame,stage,count\n";
            counterOutput = &counters;
        }
        frames << "frame,window,local_frame,warmup,cpu_frame_ms,gpu_sequence,sl_frame_id,wall_epoch_ms\n";
        gpu << "frame,domain,stage,gpu_ms\n";
        host << "frame,stage,host_ms\n";
        static const McvrProfileSink sink{MCVR_PROFILE_ABI, sizeof(McvrProfileSink), observeProfile};
        check(mcvr::profile::install(&sink), "Replay profile sink already installed");
        Json windows = Json::array();
        uint64_t frame = 0;
        bool geometryReady = false, texturesLoaded = false;
        const auto &capturedWorld = replay->capturedWorld;
        for (const auto &item : plan) {
            const std::string name = item.at("name");
            check(std::filesystem::path(name).filename() == name, "Window name must be a filename");
            check(name.find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-") ==
                      std::string::npos,
                  "Replay window name must contain only letters, digits, underscore or dash");
            const auto pack = std::filesystem::absolute(item.at("shader_pack").get<std::string>());
            check(std::filesystem::is_regular_file(pack), "Requested shader pack does not exist");
            auto requested = ShaderPackLoader::load(pack, pack, "en_us");
            if (!requested.success) throw std::runtime_error("Requested replay pack is invalid: " + requested.error);
            check(!requested.shaderPack.requiresEmission || Renderer::options.collectChunkEmission,
                  "Requested replay pack needs an uncaptured emission sampling table");
            const uint32_t warmup = item.value("warmup", 256u), count = item.value("frames", 480u);
            check(count > 0 && count <= 100000 && warmup <= 100000, "Unbounded replay window");
            const std::string route = item.value("route", std::string("fixed"));
            check(route == "fixed" || route == "turn", "Unknown camera route");
            const uint32_t imageSamples = item.value("image_samples", 1u);
            check(imageSamples >= 1 && imageSamples <= 64 && imageSamples <= count &&
                      (imageSamples == 1 || route == "fixed"),
                  "Accumulated replay images require 1 to 64 samples at a fixed camera");
            replay->seedOffset = item.value("seed_offset", 0u);
            check(fw->waitDeviceIdle() == VK_SUCCESS, "A/B boundary synchronization failed");
            auto blueprint = m.at("pipeline");
            bool replaced = false;
            for (auto &attrs : blueprint["attributes"])
                for (size_t i = 0; i + 1 < attrs.size(); i += 2)
                    if (attrs[i] == "render_pipeline.module.ray_tracing.attribute.shader_pack_path") {
                        attrs[i + 1] = pack.string();
                        replaced = true;
                    }
            if (!replaced) {
                auto &attrs = blueprint["attributes"][0];
                attrs.push_back("render_pipeline.module.ray_tracing.attribute.shader_pack_path");
                attrs.push_back(pack.string());
            }
            // Window-local settings let quality A/B windows share one frozen scene and one process.
            if (item.contains("attributes")) {
                for (const auto &[key, value] : item.at("attributes").items()) {
                    bool found = false;
                    for (auto &attrs : blueprint["attributes"])
                        for (size_t i = 0; i + 1 < attrs.size(); i += 2)
                            if (attrs[i] == key) {
                                attrs[i + 1] = value.get<std::string>();
                                found = true;
                            }
                    if (found) continue;
                    // Older snapshots predate some attributes; add them to the module the key names.
                    const auto separator = key.find(".attribute.");
                    check(separator != std::string::npos, "Replay attribute override has no module");
                    const std::string module = key.substr(0, separator) + ".name";
                    const auto &modules = blueprint.at("modules");
                    const auto owner = std::find(modules.begin(), modules.end(), module);
                    check(owner != modules.end(), "Replay attribute override names an absent module");
                    auto &attrs = blueprint["attributes"][static_cast<size_t>(owner - modules.begin())];
                    attrs.push_back(key);
                    attrs.push_back(value.get<std::string>());
                }
            }
            const auto compilationStart = std::chrono::steady_clock::now();
            buildBlueprint(blueprint, fw->pipeline());
            check(fw->recreate(false, false) == VK_SUCCESS, "Replay pipeline preparation failed");
            fw->pipeline()->worldPipeline()->onResourceReload();
            Renderer::instance().buffers()->invalidateWorldHistory();
            const auto pipelineReady = std::chrono::steady_clock::now();
            if (!geometryReady) {
                prepareReplayGeometry(fw);
                geometryReady = true;
            }
            const double setupMs =
                std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - compilationStart).count();
            replay->camera = std::make_unique<ReplayCamera>(
                glm::dvec3(capturedWorld.cameraPos), capturedWorld.cameraViewMat, capturedWorld.cameraEffectedViewMat);
            replay->previousCameraOffset = glm::dvec3(0);
            replay->sampleFrame = 0;
            double previousYaw = 0;
            auto previousEnd = std::chrono::steady_clock::now();
            const auto warmupStart = previousEnd;
            double warmupMs = 0;
            Json firstResources, lastResources;
            std::vector<float> accumulated;
            for (uint32_t local = 0; local <= warmup + count; ++local) {
                if (local == warmup) {
                    warmupMs = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - warmupStart)
                                   .count();
                    firstResources = windowResources(fw);
                    previousEnd = std::chrono::steady_clock::now();
                } else if (local == warmup + count) {
                    lastResources = windowResources(fw);
                }
                check(!glfwWindowShouldClose(window), "Replay window closed before completion");
                glfwPollEvents();
                const double yaw = route == "turn" && local >= warmup ?
                                       .18 * std::sin(double(local - warmup) * 6.283185307179586 / double(count)) :
                                       0;
                CameraInput input;
                input.lookX = (yaw - previousYaw) / .0025;
                replay->camera->update(input, 0);
                previousYaw = yaw;
                mcvr::profile::beginFrame(++frame, MCVR_PROFILE_TIMING);
                auto &sl = mcvr::StreamlineRuntime::get();
                sl.beginFrame(Renderer::options.reflexMode, fw->effectiveFrameRateLimit());
                uint32_t token = sl.frame() ? uint32_t(*sl.frame()) : 0;
                sl.marker(sl::PCLMarker::eSimulationStart);
                sl.marker(sl::PCLMarker::eSimulationEnd);
                sl.marker(sl::PCLMarker::eRenderSubmitStart);
                auto result = fw->acquireContext();
                check(result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR, "Replay acquire failed");
                auto sequence = fw->beginGpuProfile();
                const bool sampleImage = local > warmup + count - imageSamples;
                replay->wantImage = sampleImage;
                if (!texturesLoaded) {
                    loadTextures(fw);
                    texturesLoaded = true;
                } else
                    Renderer::instance().textures()->bindAllTextures();
                frameUniforms();
                Renderer::instance().world()->shouldRender() = true;
                fw->setMenuFrame(false);
                fw->pipeline()->acquirePipelineContext(fw->safeAcquireCurrentContext())->fuseWorld();
                check(fw->submitCommand() == VK_SUCCESS, "Replay submit failed");
                sl.marker(sl::PCLMarker::eRenderSubmitEnd);
                result = fw->present();
                check(result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR, "Replay present failed");
                if (sampleImage && imageSamples > 1) {
                    check(fw->waitDeviceIdle() == VK_SUCCESS, "Replay calibration readback wait failed");
                    check(replay->imageReadback && replay->imageFormat == VK_FORMAT_R16G16B16A16_SFLOAT,
                          "Replay calibration requires a floating-point display canvas");
                    replay->imageReadback->downloadFromBuffer();
                    const auto *pixels = static_cast<const uint16_t *>(replay->imageReadback->mappedPtr());
                    if (accumulated.empty()) accumulated.resize(replay->imageReadback->size() / sizeof(*pixels));
                    for (size_t i = 0; i < accumulated.size(); ++i)
                        accumulated[i] += glm::unpackHalf1x16(pixels[i]) / float(imageSamples);
                }
                auto end = std::chrono::steady_clock::now();
                frames << frame << ',' << name << ',' << local << ','
                       << (local < warmup || local == warmup + count || (imageSamples > 1 && sampleImage)) << ','
                       << std::chrono::duration<double, std::milli>(end - previousEnd).count() << ',' << sequence << ','
                       << token << ','
                       << std::chrono::duration_cast<std::chrono::milliseconds>(
                              std::chrono::system_clock::now().time_since_epoch())
                              .count()
                       << '\n';
                previousEnd = end;
                ++replay->sampleFrame;
            }
            check(fw->waitDeviceIdle() == VK_SUCCESS, "Window completion failed");
            // Collect the final frame slots before their query pools are rebuilt.
            const auto properties = fw->physicalDevice()->vkPhysicalDevice();
            VkPhysicalDeviceProperties physical{};
            vkGetPhysicalDeviceProperties(properties, &physical);
            VkQueueFamilyProperties family{};
            uint32_t familyCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(properties, &familyCount, nullptr);
            std::vector<VkQueueFamilyProperties> families(familyCount);
            vkGetPhysicalDeviceQueueFamilyProperties(properties, &familyCount, families.data());
            for (auto &ctx : fw->contexts())
                ctx->auditGpu.collect(fw->device()->vkDevice(),
                                      families.at(fw->physicalDevice()->mainQueueIndex()).timestampValidBits,
                                      physical.limits.timestampPeriod);
            check(bool(replay->imageReadback), "Replay did not execute its image frame");
            replay->imageReadback->downloadFromBuffer();
            write(output / (name + ".bin"), replay->imageReadback->mappedPtr(), replay->imageReadback->size());
            replay->imageReadback.reset();
            if (!accumulated.empty())
                write(output / (name + "-average.bin"), accumulated.data(), accumulated.size() * sizeof(float));
            windows.push_back(
                {{"name", name},
                 {"shader_pack", pack.string()},
                 {"route", route},
                 {"warmup_frames", warmup},
                 {"timed_frames", count},
                 {"image_samples", imageSamples},
                 {"seed_offset", replay->seedOffset},
                 {"prepare_ms", setupMs},
                 {"pipeline_prepare_ms",
                  std::chrono::duration<double, std::milli>(pipelineReady - compilationStart).count()},
                 {"scene_prepare_ms",
                  setupMs - std::chrono::duration<double, std::milli>(pipelineReady - compilationStart).count()},
                 {"warmup_ms", warmupMs},
                 {"resources_at_start", std::move(firstResources)},
                 {"resources_at_end", std::move(lastResources)},
                 {"end_frame", frame},
                 {"image_format", replay->imageFormat}});
        }
        const auto metadata = Json{{"snapshot", replay->root.string()},
                                   {"source", m.at("source")},
                                   {"windows", windows},
                                   {"width", m.at("width")},
                                   {"height", m.at("height")},
                                   {"sampling", "captured seed, frame-indexed sequence; reset for each window"}}
                                  .dump(2);
        write(output / "result.json", metadata.data(), metadata.size());
        close();
        return 0;
    } catch (...) {
        auto error = std::current_exception();
        try {
            bool lost = Renderer::is_initialized() && Renderer::instance().framework()->isDeviceLost();
            const auto failure = Json{{"deviceLost", lost}, {"automaticRetry", false}}.dump();
            write(output / "failure.json", failure.data(), failure.size());
        } catch (...) {}
        try {
            close();
        } catch (...) {}
        std::rethrow_exception(error);
    }
}

} // namespace mcvr::replay

#ifdef _WIN32
extern "C" __declspec(dllexport) int
MCVR_ReplayMain(const wchar_t *snapshot, const wchar_t *plan, const wchar_t *output) noexcept {
    try {
        return mcvr::replay::run(snapshot, plan, output);
    } catch (const std::exception &e) {
        try {
            mcvr::log::error("SceneReplay") << e.what() << std::endl;
        } catch (...) {}
        return 1;
    } catch (...) { return 2; }
}
#endif
