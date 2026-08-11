#pragma once

#include <deque>
#include <functional>
#include <mutex>
#include <unordered_map>

#include "core/all_extern.hpp"

namespace vk {
class PhysicalDevice;
class Device;
class Instance;

class VMA : public SharedObject<VMA> {
  public:
    VMA(std::shared_ptr<Instance> instance,
        std::shared_ptr<PhysicalDevice> physicalDevice,
        std::shared_ptr<Device> device);
    ~VMA();

    VmaAllocator &allocator();
    VmaPool smallResourcePool(const VkImageCreateInfo &image, const VmaAllocationCreateInfo &allocation);
    VmaPool smallResourcePool(const VkBufferCreateInfo &buffer,
                              const VmaAllocationCreateInfo &allocation,
                              VkDeviceSize alignment = 0);

  private:
    VmaAllocator allocator_ = VK_NULL_HANDLE;
    VmaVulkanFunctions vulkanFunctions_{};
    VkDevice device_ = VK_NULL_HANDLE;
    std::mutex poolMutex_;
    std::unordered_map<uint32_t, VmaPool> smallPools_;
    VmaPool smallResourcePool(uint32_t memoryType, const VkMemoryRequirements2 &requirements);
};
}; // namespace vk
