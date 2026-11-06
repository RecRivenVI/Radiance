#pragma once

#include "core/all_extern.hpp"

#include <span>
#include <unordered_set>

namespace vk {
class Instance;
class Window;

struct DeviceCapabilities {
    explicit DeviceCapabilities(VkPhysicalDevice device);

    const char *missingRendererRequirement(uint32_t apiVersion) const;
    static std::span<const char *const> requiredExtensions();

    std::unordered_set<std::string> extensions;
    VkPhysicalDeviceFeatures2 supportedFeatures2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
    VkPhysicalDeviceVulkan11Features supportedVulkan11{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES};
    VkPhysicalDeviceVulkan12Features supportedVulkan12{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
    VkPhysicalDeviceVulkan13Features supportedVulkan13{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};
    VkPhysicalDeviceAccelerationStructureFeaturesKHR supportedAccelerationStructureFeatures{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR};
    VkPhysicalDeviceRayTracingPipelineFeaturesKHR supportedRayTracingFeatures{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR};
    VkPhysicalDeviceExtendedDynamicState2FeaturesEXT supportedExtendedDynamicState2{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_2_FEATURES_EXT};
    VkPhysicalDeviceExtendedDynamicState3FeaturesEXT supportedExtendedDynamicState3{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_3_FEATURES_EXT};
    VkPhysicalDeviceVertexInputDynamicStateFeaturesEXT supportedVertexInputDynamicState{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_INPUT_DYNAMIC_STATE_FEATURES_EXT};
    VkPhysicalDeviceCoherentMemoryFeaturesAMD supportedCoherentMemoryFeatures{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COHERENT_MEMORY_FEATURES_AMD};
    VkPhysicalDeviceComputeShaderDerivativesFeaturesKHR supportedComputeDerivatives{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COMPUTE_SHADER_DERIVATIVES_FEATURES_KHR};
    VkPhysicalDeviceFaultFeaturesEXT supportedFault{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FAULT_FEATURES_EXT};
    VkPhysicalDevicePipelineExecutablePropertiesFeaturesKHR supportedExecutable{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_EXECUTABLE_PROPERTIES_FEATURES_KHR};
    VkPhysicalDeviceDiagnosticsConfigFeaturesNV supportedCapture{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DIAGNOSTICS_CONFIG_FEATURES_NV};
    VkPhysicalDeviceRayTracingInvocationReorderFeaturesNV supportedReorder{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_INVOCATION_REORDER_FEATURES_NV};
    VkPhysicalDeviceOpacityMicromapFeaturesEXT supportedOpacityMicromap{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_OPACITY_MICROMAP_FEATURES_EXT};
#ifdef VK_EXT_ray_tracing_invocation_reorder
    VkPhysicalDeviceRayTracingInvocationReorderFeaturesEXT supportedReorderExt{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_INVOCATION_REORDER_FEATURES_EXT};
#endif
};

class PhysicalDevice : public SharedObject<PhysicalDevice> {
  public:
    PhysicalDevice(std::shared_ptr<Instance> instance, std::shared_ptr<Window> window);
    ~PhysicalDevice();

    VkPhysicalDevice &vkPhysicalDevice();
    uint32_t mainQueueIndex();
    uint32_t secondaryQueueIndex();
    bool hasSecondaryQueue() const {
        return mainQueueIndex_ != secondaryQueueIndex_ || mainQueueCount_ >= 2;
    }

    const DeviceCapabilities &capabilities() const {
        return *capabilities_;
    }

    VkPhysicalDeviceProperties properties();
    VkPhysicalDeviceRayTracingPipelinePropertiesKHR rayTracingProperties();
    VkPhysicalDeviceAccelerationStructurePropertiesKHR accelerationStructProperties();

  private:
    void findPhysicalDevice();
    bool findQueueFamilies(VkPhysicalDevice device);

    std::unique_ptr<DeviceCapabilities> capabilities_;
    std::shared_ptr<Instance> instance_;
    std::shared_ptr<Window> window_;

    VkPhysicalDevice physicalDevice_ = VK_NULL_HANDLE;
    uint32_t mainQueueIndex_ = -1;
    uint32_t secondaryQueueIndex_ = -1;
    uint32_t mainQueueCount_ = 0;

    VkPhysicalDeviceProperties properties_;
    VkPhysicalDeviceRayTracingPipelinePropertiesKHR rayTracingProperties_;
    VkPhysicalDeviceAccelerationStructurePropertiesKHR accelerationStructProperties_;
};
} // namespace vk
