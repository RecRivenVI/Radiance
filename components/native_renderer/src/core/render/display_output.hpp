#pragma once
#include "core/vulkan/all_core_vulkan.hpp"
#include <array>
#include <vector>
class Framework;
class DisplayOutput {
  public:
    std::shared_ptr<vk::DeviceLocalImage> encode(Framework &,
                                                 const std::shared_ptr<vk::CommandBuffer> &,
                                                 uint32_t frame,
                                                 uint32_t role,
                                                 const std::shared_ptr<vk::DeviceLocalImage> &);

  private:
    struct Frame {
        std::shared_ptr<vk::DeviceLocalImage> image;
        std::shared_ptr<vk::Framebuffer> framebuffer;
        std::shared_ptr<vk::DescriptorTable> table;
    };
    std::vector<std::array<Frame, 2>> frames_;
    std::shared_ptr<vk::Sampler> sampler_;
    std::shared_ptr<vk::RenderPass> pass_;
    std::shared_ptr<vk::GraphicsPipeline> pipeline_;
};
