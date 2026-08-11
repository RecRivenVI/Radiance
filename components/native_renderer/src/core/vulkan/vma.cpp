#define VMA_IMPLEMENTATION

#include "core/logging.hpp"
#include "core/failure_state.hpp"
#include "core/vulkan/vma.hpp"

#include "core/vulkan/device.hpp"
#include "core/vulkan/instance.hpp"
#include "core/vulkan/physical_device.hpp"

#include <iostream>

auto vmaTableCout() {
    return mcvr::log::info("VMA");
}

auto vmaTableCerr() {
    return mcvr::log::error("VMA");
}

vk::VMA::VMA(std::shared_ptr<Instance> instance,
             std::shared_ptr<PhysicalDevice> physicalDevice,
             std::shared_ptr<Device> device) {
    VmaAllocatorCreateInfo allocatorCreateInfo{};
    allocatorCreateInfo.physicalDevice = physicalDevice->vkPhysicalDevice();
    allocatorCreateInfo.device = device->vkDevice();
    device_ = allocatorCreateInfo.device;
    allocatorCreateInfo.instance = instance->vkInstance();
    allocatorCreateInfo.vulkanApiVersion = VK_API_VERSION_1_4;
    allocatorCreateInfo.flags = VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT;
    if (device->hasMemoryBudget()) allocatorCreateInfo.flags |= VMA_ALLOCATOR_CREATE_EXT_MEMORY_BUDGET_BIT;
    vmaTableCout() << "Driver memory budget=" << device->hasMemoryBudget() << "; small device resource pool=4 MiB"
                   << std::endl;

    if (const auto result = vmaImportVulkanFunctionsFromVolk(&allocatorCreateInfo, &vulkanFunctions_);
        result != VK_SUCCESS) {
        vmaTableCerr() << "failed to create vulkan function from volk" << std::endl;
        mcvr::failure::raise(mcvr::failure::Kind::initialization, result, "vmaImportVulkanFunctionsFromVolk");
    } else {
#ifdef DEBUG
        vmaTableCout() << "created vulkan function from volk" << std::endl;
#endif
    }
    allocatorCreateInfo.pVulkanFunctions = &vulkanFunctions_;

    if (const auto result = vmaCreateAllocator(&allocatorCreateInfo, &allocator_); result != VK_SUCCESS) {
        vmaTableCerr() << "failed to create VMA" << std::endl;
        mcvr::failure::raise(mcvr::failure::Kind::initialization, result, "vmaCreateAllocator");
    } else {
#ifdef DEBUG
        vmaTableCout() << "created VMA" << std::endl;
#endif
    }
}

vk::VMA::~VMA() {
#ifdef DEBUG
    vmaTableCout() << "VMA deconstructed" << std::endl;
#endif
    if (allocator_ != VK_NULL_HANDLE) {
        for (const auto &[type, pool] : smallPools_) vmaDestroyPool(allocator_, pool);
        vmaDestroyAllocator(allocator_);
    }
}

VmaAllocator &vk::VMA::allocator() {
    return allocator_;
}

namespace {
constexpr VkDeviceSize smallResourceLimit = 256 * 1024;
bool poolEligible(const VmaAllocationCreateInfo &allocation) {
    return !allocation.pool &&
           !(allocation.flags & (VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT | VMA_ALLOCATION_CREATE_CAN_ALIAS_BIT));
}
} // namespace

VmaPool vk::VMA::smallResourcePool(uint32_t memoryType, const VkMemoryRequirements2 &requirements) {
    const auto *dedicated = static_cast<const VkMemoryDedicatedRequirements *>(requirements.pNext);
    if (requirements.memoryRequirements.size > smallResourceLimit || dedicated->requiresDedicatedAllocation ||
        dedicated->prefersDedicatedAllocation)
        return VK_NULL_HANDLE;
    const VkPhysicalDeviceMemoryProperties *properties;
    vmaGetMemoryProperties(allocator_, &properties);
    const auto flags = properties->memoryTypes[memoryType].propertyFlags;
    if (!(flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) || (flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT))
        return VK_NULL_HANDLE;
    std::lock_guard lock(poolMutex_);
    if (auto found = smallPools_.find(memoryType); found != smallPools_.end()) return found->second;
    VmaPoolCreateInfo info{};
    info.memoryTypeIndex = memoryType;
    // Long-lived tiny textures/UI buffers must not pin mostly-empty 256 MiB
    // world blocks after unload. VMA still enforces buffer/image granularity.
    info.blockSize = 4 * 1024 * 1024;
    VmaPool pool = VK_NULL_HANDLE;
    const auto result = vmaCreatePool(allocator_, &info, &pool);
    if (result != VK_SUCCESS)
        mcvr::failure::raise(mcvr::failure::Kind::runtime, result, "vmaCreatePool(small resources)");
    try {
        smallPools_.emplace(memoryType, pool);
    } catch (...) {
        vmaDestroyPool(allocator_, pool);
        throw;
    }
    vmaSetPoolName(allocator_, pool, "Radiance small device resources");
    return pool;
}

VmaPool vk::VMA::smallResourcePool(const VkImageCreateInfo &image, const VmaAllocationCreateInfo &allocation) {
    if (!poolEligible(allocation) || image.flags & (VK_IMAGE_CREATE_DISJOINT_BIT | VK_IMAGE_CREATE_SPARSE_BINDING_BIT |
                                                    VK_IMAGE_CREATE_SPARSE_RESIDENCY_BIT))
        return VK_NULL_HANDLE;
    VkMemoryDedicatedRequirements dedicated{VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS};
    VkMemoryRequirements2 requirements{VK_STRUCTURE_TYPE_MEMORY_REQUIREMENTS_2, &dedicated};
    VkDeviceImageMemoryRequirements info{VK_STRUCTURE_TYPE_DEVICE_IMAGE_MEMORY_REQUIREMENTS};
    info.pCreateInfo = &image;
    vkGetDeviceImageMemoryRequirements(device_, &info, &requirements);
    if (requirements.memoryRequirements.size > smallResourceLimit) return VK_NULL_HANDLE;
    uint32_t memoryType;
    const auto result = vmaFindMemoryTypeIndexForImageInfo(allocator_, &image, &allocation, &memoryType);
    if (result != VK_SUCCESS) mcvr::failure::raise(mcvr::failure::Kind::runtime, result, "small image memory type");
    return smallResourcePool(memoryType, requirements);
}

VmaPool vk::VMA::smallResourcePool(const VkBufferCreateInfo &buffer,
                                   const VmaAllocationCreateInfo &allocation,
                                   VkDeviceSize alignment) {
    // Preserve the large-block AS layout for build/traversal locality. These
    // allocations are not the small persistent resources pinning world blocks.
    if (!poolEligible(allocation) || buffer.size > smallResourceLimit || alignment > smallResourceLimit ||
        buffer.flags || (buffer.usage & VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR))
        return VK_NULL_HANDLE;
    VkMemoryDedicatedRequirements dedicated{VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS};
    VkMemoryRequirements2 requirements{VK_STRUCTURE_TYPE_MEMORY_REQUIREMENTS_2, &dedicated};
    VkDeviceBufferMemoryRequirements info{VK_STRUCTURE_TYPE_DEVICE_BUFFER_MEMORY_REQUIREMENTS};
    info.pCreateInfo = &buffer;
    vkGetDeviceBufferMemoryRequirements(device_, &info, &requirements);
    uint32_t memoryType;
    const auto result = vmaFindMemoryTypeIndexForBufferInfo(allocator_, &buffer, &allocation, &memoryType);
    if (result != VK_SUCCESS) mcvr::failure::raise(mcvr::failure::Kind::runtime, result, "small buffer memory type");
    return smallResourcePool(memoryType, requirements);
}
