#include "core/vulkan/physical_device.hpp"

#include "core/logging.hpp"
#include "core/failure_state.hpp"

#include "core/vulkan/instance.hpp"
#include "core/vulkan/window.hpp"

#include <iostream>
#include <vector>

auto physicalDeviceCout() {
    return mcvr::log::info("PhysicalDevice");
}

auto physicalDeviceCerr() {
    return mcvr::log::error("PhysicalDevice");
}

std::span<const char *const> vk::DeviceCapabilities::requiredExtensions() {
    static constexpr const char *names[] = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME,
        VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
        VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
        VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME,
        VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME,
        VK_EXT_EXTENDED_DYNAMIC_STATE_3_EXTENSION_NAME,
    };
    return names;
}

vk::DeviceCapabilities::DeviceCapabilities(VkPhysicalDevice device) {
    uint32_t count = 0;
    auto result = vkEnumerateDeviceExtensionProperties(device, nullptr, &count, nullptr);
    if (result != VK_SUCCESS)
        mcvr::failure::raise(mcvr::failure::Kind::initialization, result,
                             "vkEnumerateDeviceExtensionProperties(count)");
    std::vector<VkExtensionProperties> properties(count);
    result = vkEnumerateDeviceExtensionProperties(device, nullptr, &count, properties.data());
    if (result != VK_SUCCESS)
        mcvr::failure::raise(mcvr::failure::Kind::initialization, result, "vkEnumerateDeviceExtensionProperties(list)");
    for (const auto &property : std::span(properties).first(count)) extensions.emplace(property.extensionName);

    auto prepend = [&](auto &features) {
        features.pNext = supportedFeatures2.pNext;
        supportedFeatures2.pNext = &features;
    };
    auto extensionFeatures = [&](const char *extension, auto &features) {
        if (extensions.contains(extension)) prepend(features);
    };
    prepend(supportedVulkan11);
    prepend(supportedVulkan12);
    prepend(supportedVulkan13);
    extensionFeatures(VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME, supportedAccelerationStructureFeatures);
    extensionFeatures(VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME, supportedRayTracingFeatures);
    extensionFeatures(VK_EXT_EXTENDED_DYNAMIC_STATE_2_EXTENSION_NAME, supportedExtendedDynamicState2);
    extensionFeatures(VK_EXT_EXTENDED_DYNAMIC_STATE_3_EXTENSION_NAME, supportedExtendedDynamicState3);
    extensionFeatures(VK_EXT_VERTEX_INPUT_DYNAMIC_STATE_EXTENSION_NAME, supportedVertexInputDynamicState);
    extensionFeatures(VK_AMD_DEVICE_COHERENT_MEMORY_EXTENSION_NAME, supportedCoherentMemoryFeatures);
    extensionFeatures(VK_KHR_COMPUTE_SHADER_DERIVATIVES_EXTENSION_NAME, supportedComputeDerivatives);
    extensionFeatures(VK_EXT_DEVICE_FAULT_EXTENSION_NAME, supportedFault);
    extensionFeatures(VK_KHR_PIPELINE_EXECUTABLE_PROPERTIES_EXTENSION_NAME, supportedExecutable);
    extensionFeatures(VK_NV_DEVICE_DIAGNOSTICS_CONFIG_EXTENSION_NAME, supportedCapture);
    extensionFeatures(VK_NV_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME, supportedReorder);
    extensionFeatures(VK_EXT_OPACITY_MICROMAP_EXTENSION_NAME, supportedOpacityMicromap);
#ifdef VK_EXT_ray_tracing_invocation_reorder
    extensionFeatures(VK_EXT_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME, supportedReorderExt);
#endif
    vkGetPhysicalDeviceFeatures2(device, &supportedFeatures2);
}

const char *vk::DeviceCapabilities::missingRendererRequirement(uint32_t apiVersion) const {
    if (apiVersion < VK_API_VERSION_1_3) return "Vulkan 1.3";
    for (const auto *name : requiredExtensions())
        if (!extensions.contains(name)) return name;
    if (!supportedRayTracingFeatures.rayTracingPipeline) return "rayTracingPipeline";
    if (!supportedAccelerationStructureFeatures.accelerationStructure ||
        !supportedAccelerationStructureFeatures.descriptorBindingAccelerationStructureUpdateAfterBind)
        return "accelerationStructure and update-after-bind";
    if (!supportedVulkan13.synchronization2) return "synchronization2";
    if (!supportedVulkan12.bufferDeviceAddress) return "bufferDeviceAddress";
    if (!supportedVulkan12.descriptorIndexing || !supportedVulkan12.runtimeDescriptorArray ||
        !supportedVulkan12.descriptorBindingPartiallyBound ||
        !supportedVulkan12.descriptorBindingUpdateUnusedWhilePending ||
        !supportedVulkan12.descriptorBindingSampledImageUpdateAfterBind ||
        !supportedVulkan12.descriptorBindingUniformBufferUpdateAfterBind ||
        !supportedVulkan12.descriptorBindingStorageImageUpdateAfterBind ||
        !supportedVulkan12.descriptorBindingStorageBufferUpdateAfterBind ||
        !supportedVulkan12.shaderSampledImageArrayNonUniformIndexing)
        return "descriptor indexing, non-uniform sampled images and update-after-bind";
    if (!supportedFeatures2.features.shaderInt64 || !supportedFeatures2.features.shaderFloat64)
        return "shaderInt64 and shaderFloat64";
    if (!supportedExtendedDynamicState3.extendedDynamicState3PolygonMode ||
        !supportedExtendedDynamicState3.extendedDynamicState3ColorBlendEnable ||
        !supportedExtendedDynamicState3.extendedDynamicState3ColorBlendEquation ||
        !supportedExtendedDynamicState3.extendedDynamicState3ColorWriteMask)
        return "dynamic polygon, blending and write-mask state";
    return nullptr;
}

void vk::PhysicalDevice::findPhysicalDevice() {
    uint32_t deviceCount = 0;
    const auto countResult = vkEnumeratePhysicalDevices(instance_->vkInstance(), &deviceCount, nullptr);
    if (countResult != VK_SUCCESS || deviceCount == 0) {
        physicalDeviceCerr() << "failed to get number of physical devices" << std::endl;
        mcvr::failure::raise(mcvr::failure::Kind::initialization,
                             countResult == VK_SUCCESS ? VK_ERROR_INITIALIZATION_FAILED : countResult,
                             "vkEnumeratePhysicalDevices(count)");
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    if (const auto result = vkEnumeratePhysicalDevices(instance_->vkInstance(), &deviceCount, devices.data());
        result != VK_SUCCESS) {
        physicalDeviceCerr() << "failed to retrieve physical devices" << std::endl;
        mcvr::failure::raise(mcvr::failure::Kind::initialization, result, "vkEnumeratePhysicalDevices(list)");
    }

    for (const auto device : devices) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(device, &properties);
        if (properties.deviceType != VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) continue;
        auto capabilities = std::make_unique<DeviceCapabilities>(device);
        if (const char *missing = capabilities->missingRendererRequirement(properties.apiVersion)) {
            physicalDeviceCerr() << properties.deviceName << " missing renderer requirement: " << missing << std::endl;
            continue;
        }
        if (!findQueueFamilies(device)) {
            physicalDeviceCerr() << properties.deviceName << " missing a graphics/compute/present queue" << std::endl;
            continue;
        }
        physicalDevice_ = device;
        capabilities_ = std::move(capabilities);
        return;
    }

    // if no supported physical device is found
    physicalDeviceCerr() << "No suitable physical device found!" << std::endl;
    mcvr::failure::raise(mcvr::failure::Kind::initialization, VK_ERROR_FEATURE_NOT_PRESENT, "selectPhysicalDevice");
}

vk::PhysicalDevice::PhysicalDevice(std::shared_ptr<Instance> instance, std::shared_ptr<Window> window)
    : instance_(instance), window_(window) {
    findPhysicalDevice();

    VkPhysicalDeviceAccelerationStructurePropertiesKHR accelStructProperties{};
    accelStructProperties.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR;

    VkPhysicalDeviceRayTracingPipelinePropertiesKHR rayTracingProperties{};
    rayTracingProperties.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR;
    rayTracingProperties.pNext = &accelStructProperties;

    VkPhysicalDeviceProperties2 deviceProps2{};
    deviceProps2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
    deviceProps2.pNext = &rayTracingProperties;
    vkGetPhysicalDeviceProperties2(physicalDevice_, &deviceProps2);

    properties_ = deviceProps2.properties;
    rayTracingProperties_ = rayTracingProperties;
    accelerationStructProperties_ = accelStructProperties;
}

vk::PhysicalDevice::~PhysicalDevice() {
#ifdef DEBUG
    physicalDeviceCout() << "physical device deconstructed" << std::endl;
#endif
}

VkPhysicalDevice &vk::PhysicalDevice::vkPhysicalDevice() {
    return physicalDevice_;
}

uint32_t vk::PhysicalDevice::mainQueueIndex() {
    return mainQueueIndex_;
}

uint32_t vk::PhysicalDevice::secondaryQueueIndex() {
    return secondaryQueueIndex_;
}

bool vk::PhysicalDevice::findQueueFamilies(VkPhysicalDevice device) {
    mainQueueIndex_ = secondaryQueueIndex_ = UINT32_MAX;
    mainQueueCount_ = 0;
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, nullptr);
    std::vector<VkQueueFamilyProperties> families(count);
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, families.data());

    constexpr auto mainFlags = VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT | VK_QUEUE_TRANSFER_BIT;
    for (uint32_t i = 0; i < count; ++i) {
        if (families[i].queueCount == 0 || (families[i].queueFlags & mainFlags) != mainFlags) continue;
        VkBool32 presentSupport = VK_FALSE;
        const auto result = vkGetPhysicalDeviceSurfaceSupportKHR(device, i, window_->vkSurface(), &presentSupport);
        if (result != VK_SUCCESS)
            mcvr::failure::raise(mcvr::failure::Kind::initialization, result, "vkGetPhysicalDeviceSurfaceSupportKHR");
        if (!presentSupport) continue;
        mainQueueIndex_ = secondaryQueueIndex_ = i;
        mainQueueCount_ = families[i].queueCount;
        break;
    }
    if (mainQueueIndex_ == UINT32_MAX) return false;

    // Prefer another queue in the same family for exclusive chunk resources.
    if (mainQueueCount_ < 2) {
        constexpr auto chunkFlags = VK_QUEUE_COMPUTE_BIT | VK_QUEUE_TRANSFER_BIT;
        for (uint32_t i = 0; i < count; ++i) {
            if (i != mainQueueIndex_ && families[i].queueCount > 0 &&
                (families[i].queueFlags & chunkFlags) == chunkFlags) {
                secondaryQueueIndex_ = i;
                break;
            }
        }
    }
    return true;
}

VkPhysicalDeviceProperties vk::PhysicalDevice::properties() {
    return properties_;
}

VkPhysicalDeviceRayTracingPipelinePropertiesKHR vk::PhysicalDevice::rayTracingProperties() {
    return rayTracingProperties_;
}

VkPhysicalDeviceAccelerationStructurePropertiesKHR vk::PhysicalDevice::accelerationStructProperties() {
    return accelerationStructProperties_;
}
