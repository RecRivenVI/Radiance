#include "core/logging.hpp"
#include "core/render/renderer.hpp"
#include "core/diagnostics/local_gpu_capture.hpp"

#include "core/render/buffers.hpp"
#include "core/render/framebuffers.hpp"
#include "core/render/frame_execution.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/textures.hpp"
#include "core/render/world.hpp"
#include "core/render/scene_scope.hpp"
#include "core/loading/loading_renderer.hpp"

std::filesystem::path Renderer::folderPath{};
Options Renderer::options{};

Renderer::Renderer(GLFWwindow *window)
    : framework_(Framework::create(window)),
      textures_(Textures::create(framework_)),
      framebuffers_(Framebuffers::create(framework_, textures_)),
      buffers_(Buffers::create(framework_)),
      world_(World::create(framework_)) {
    tessellationSupported_.store(framework_->device()->hasTessellation(), std::memory_order_release);
}

Renderer::~Renderer() {}

std::shared_ptr<Framework> Renderer::framework() {
    return framework_;
}

bool Renderer::tessellationSupported() const noexcept {
    return tessellationSupported_.load(std::memory_order_acquire);
}

std::shared_ptr<Textures> Renderer::textures(const FrameworkContext *overlay) {
    mcvr::FrameExecution::checkAccess("texture table", overlay);
    return textures_;
}

std::shared_ptr<Framebuffers> Renderer::framebuffers(const FrameworkContext *overlay) {
    mcvr::FrameExecution::checkAccess("framebuffer table", overlay);
    return framebuffers_;
}

bool Renderer::defaultDrawFramebufferBound() const {
    return framebuffers_ && uint32_t(producerFramebufferBindings() >> 32) == 0;
}

uint64_t Renderer::producerFramebufferBindings() const {
    if (!framebuffers_) throw std::runtime_error("Vulkan framebuffer resources are not initialized");
    return framebuffers_->producerBindings();
}

bool Renderer::producerFramebufferTexture(uint32_t id) const {
    if (!textures_) throw std::runtime_error("Texture manager is unavailable");
    return textures_->producerFramebufferTexture(id);
}

std::shared_ptr<Buffers> Renderer::buffers(const FrameworkContext *overlay) {
    mcvr::FrameExecution::checkAccess("buffer table", overlay);
    if (auto scene = SceneRecordingScope::active()) return scene->buffers;
    return buffers_;
}

std::shared_ptr<World> Renderer::world(const FrameworkContext *input) {
    mcvr::FrameExecution::checkAccess("world state", input);
    if (auto scene = SceneRecordingScope::active()) return scene->world;
    return world_;
}

std::shared_ptr<World> Renderer::inputWorld() {
    return world(framework_ ? framework_->worldInputSlot() : nullptr);
}

void Renderer::close() {
    tessellationSupported_.store(false, std::memory_order_release);
    if (framework_ != nullptr && framework_->isDeviceLost())
        mcvr::diagnostics::local_gpu_capture::beforeLostDeviceRelease();
    releaseLoadingRenderer();
    if (framework_ != nullptr && !framework_->isDeviceLost()) framework_->waitDeviceIdle();
    if (framework_ != nullptr && framework_->isDeviceLost())
        mcvr::diagnostics::local_gpu_capture::beforeLostDeviceRelease();

    if (world_ != nullptr) world_->close();
    if (framework_ != nullptr) framework_->close();

    framebuffers_ = nullptr;
    textures_ = nullptr;
    buffers_ = nullptr;
    world_ = nullptr;
    framework_ = nullptr;

#ifdef DEBUG
    mcvr::log::info("Renderer") << "Renderer closed" << std::endl;
#endif
}
