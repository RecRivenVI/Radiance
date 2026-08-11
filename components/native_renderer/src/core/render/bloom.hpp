#pragma once
#include "core/vulkan/all_core_vulkan.hpp"
#include <array>
#include <vector>
class Framework;
class Bloom {
  public:
    std::shared_ptr<vk::DeviceLocalImage> record(Framework &,
                                                 const std::shared_ptr<vk::CommandBuffer> &,
                                                 uint32_t slot,
                                                 const std::shared_ptr<vk::DeviceLocalImage> &,
                                                 const std::shared_ptr<vk::DeviceLocalBuffer> &,
                                                 glm::vec4 exposure);

  private:
    struct Level {
        std::shared_ptr<vk::DeviceLocalImage> down, up;
        std::shared_ptr<vk::DescriptorTable> downTable, upTable;
    };
    std::vector<std::array<Level, 5>> frames_;
    std::shared_ptr<vk::Sampler> sampler_;
    std::shared_ptr<vk::ComputePipeline> pipeline_;
};
