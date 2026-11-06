#pragma once

#include "core/vulkan/as.hpp"
#include "core/render/rigid_scene_columns.hpp"
#include "common/shared.hpp"
#include <filesystem>
#include <span>

struct WorldPrepareContext;
struct ChunkPackedData;
namespace mcvr {
struct StaticChunkScene;
}
namespace vk {
class DeviceLocalImage;
class HostVisibleBuffer;
} // namespace vk
namespace mcvr::replay {
// Local diagnostic only. Set before device/geometry creation; a capture.request file
// inside this directory requests exactly one frame. No capture occurs by default.
#ifdef MCVR_BUILD_SCENE_REPLAY
bool captureEnabled() noexcept;
bool active() noexcept;
void captureChunkLights(std::span<const ChunkPackedData> table);
std::shared_ptr<vk::DeviceLocalBuffer> replayChunkLights();
std::shared_ptr<vk::HostVisibleBuffer> replayTextureMapping();
void registerBuffer(uint64_t address, const std::shared_ptr<vk::DeviceLocalBuffer> &buffer);
void registerBlasFlags(const vk::BLAS *blas, VkBuildAccelerationStructureFlagsKHR flags);
void capture(WorldPrepareContext &context,
             const vk::TLASBuilder::TLASInstanceBuilder &instances,
             const std::vector<uint32_t> &offsets,
             const std::vector<uint64_t> &indices,
             const std::vector<uint64_t> &positions,
             const std::vector<uint64_t> &materials,
             const std::vector<vk::VertexFormat::InstanceAppearance> &appearances,
             std::span<const std::byte> contacts,
             const std::vector<glm::vec4> &water,
             const std::shared_ptr<const mcvr::StaticChunkScene> &staticScene,
             size_t staticPrefix,
             std::span<const glm::uvec2> sampleKeys,
             const mcvr::RigidSceneColumns::Publication &rigid);
bool render(WorldPrepareContext &context);
void captureOutput(const std::shared_ptr<vk::CommandBuffer> &commands,
                   const std::shared_ptr<vk::DeviceLocalImage> &image);
int run(const std::filesystem::path &snapshot, const std::filesystem::path &plan, const std::filesystem::path &output);
#else
inline bool captureEnabled() noexcept {
    return false;
}
inline bool active() noexcept {
    return false;
}
inline void captureChunkLights(std::span<const ChunkPackedData>) {}
inline std::shared_ptr<vk::DeviceLocalBuffer> replayChunkLights() {
    return {};
}
inline std::shared_ptr<vk::HostVisibleBuffer> replayTextureMapping() {
    return {};
}
inline void registerBuffer(uint64_t, const std::shared_ptr<vk::DeviceLocalBuffer> &) {}
inline bool render(WorldPrepareContext &) {
    return false;
}
inline void captureOutput(const std::shared_ptr<vk::CommandBuffer> &, const std::shared_ptr<vk::DeviceLocalImage> &) {}
#endif
} // namespace mcvr::replay
