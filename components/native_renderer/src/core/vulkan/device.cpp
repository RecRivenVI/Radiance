#include "core/render/streamline_runtime.hpp"

#include "core/logging.hpp"
#include "core/diagnostics/as_lifetime_trace.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "core/failure_state.hpp"
#include "core/diagnostics/device_loss_trace.hpp"
#include "core/diagnostics/gpu_fault_capture.hpp"
#include <fstream>
#include <array>
#include "core/vulkan/device.hpp"
#include <cstdio>
#include "core/diagnostics/local_gpu_capture.hpp"

#include "core/render/modules/world/dlss/dlss_wrapper.hpp"
#include "core/render/modules/world/xess_upscaler/xess_wrapper.hpp"
#include "core/render/renderer.hpp"
#include "core/vulkan/instance.hpp"
#include "core/vulkan/pipeline_cache.hpp"
#include "core/vulkan/physical_device.hpp"

#include <cstring>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>
#include <unordered_set>
#include <utility>
#include <vector>

auto deviceCout() {
    return mcvr::log::info("Device");
}

auto deviceCerr() {
    return mcvr::log::error("Device");
}

vk::Device::Device(std::shared_ptr<Instance> instance,
                   std::shared_ptr<Window> window,
                   std::shared_ptr<PhysicalDevice> physicalDevice)
    : instance_(instance), window_(window), physicalDevice_(physicalDevice) {
    const auto &capabilities = physicalDevice_->capabilities();
    const auto &supportedExtensions = capabilities.extensions;
    const auto required = DeviceCapabilities::requiredExtensions();
    std::vector<const char *> enabledExtensions(required.begin(), required.end());
    for (const char *extension : {
             VK_KHR_SPIRV_1_4_EXTENSION_NAME,
             VK_KHR_SYNCHRONIZATION_2_EXTENSION_NAME,
             VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME,
             VK_EXT_EXTENDED_DYNAMIC_STATE_2_EXTENSION_NAME,
             VK_EXT_VERTEX_INPUT_DYNAMIC_STATE_EXTENSION_NAME,
         })
        if (supportedExtensions.contains(extension)) enabledExtensions.push_back(extension);

    std::vector<std::string> dlssRequiredExtensions;
    bool dlssRequirementQuerySuccess = false;
#ifdef MCVR_ENABLE_XESS
    std::vector<std::string> xessRequiredExtensions;
    bool xessRequirementQuerySuccess = false;
#endif

    std::vector<VkExtensionProperties> dlssSRExtensions;
    std::vector<std::string> dlssSRRequired;
    bool dlssSRQuery = NVSDK_NGX_SUCCEED(NgxContext::getDlssRRRequiredDeviceExtensions(
        instance_, physicalDevice_, dlssSRExtensions, NVSDK_NGX_Feature_SuperSampling));
    for (const auto &extension : dlssSRExtensions) {
        dlssSRRequired.emplace_back(extension.extensionName);
        if (std::strcmp(extension.extensionName, "VK_EXT_buffer_device_address") != 0)
            enabledExtensions.push_back(extension.extensionName);
    }
    std::vector<VkExtensionProperties> dlssFGExtensions;
    std::vector<std::string> dlssFGRequired;
    bool dlssFGQuery = NVSDK_NGX_SUCCEED(NgxContext::getDlssRRRequiredDeviceExtensions(
        instance_, physicalDevice_, dlssFGExtensions, NVSDK_NGX_Feature_FrameGeneration));
    for (const auto &extension : dlssFGExtensions) {
        dlssFGRequired.emplace_back(extension.extensionName);
        if (std::strcmp(extension.extensionName, "VK_EXT_buffer_device_address") != 0)
            enabledExtensions.push_back(extension.extensionName);
    }

    std::vector<VkExtensionProperties> dlssExtensions;
    NVSDK_NGX_Result dlssResult =
        NgxContext::getDlssRRRequiredDeviceExtensions(instance_, physicalDevice_, dlssExtensions);
    if (NVSDK_NGX_FAILED(dlssResult)) {
        deviceCerr() << "dlss device extensions unavailable; skipping." << std::endl;
    } else {
        dlssRequirementQuerySuccess = true;
#ifdef DEBUG
        deviceCout() << "dlss instance extensions:" << std::endl;
#endif
        for (const auto &dlssExtension : dlssExtensions) {
            dlssRequiredExtensions.emplace_back(dlssExtension.extensionName);
            if (std::strcmp(dlssExtension.extensionName, "VK_EXT_buffer_device_address") == 0)
                continue; // already enabled using PHYSICAL_DEVICE_VULKAN_1_2_FEATURES
#ifdef DEBUG
            deviceCout() << "\t" << dlssExtension.extensionName << std::endl;
#endif
            enabledExtensions.push_back(dlssExtension.extensionName);
        }
    }

#ifdef MCVR_ENABLE_XESS
    std::vector<const char *> xessExtensions;
    if (mcvr::XeSSWrapper::getRequiredDeviceExtensions(instance_->vkInstance(), physicalDevice_->vkPhysicalDevice(),
                                                       xessExtensions)) {
        xessRequirementQuerySuccess = true;
#    ifdef DEBUG
        deviceCout() << "xess device extensions:" << std::endl;
#    endif
        for (const char *extension : xessExtensions) {
            xessRequiredExtensions.emplace_back(extension);
#    ifdef DEBUG
            deviceCout() << "\t" << extension << std::endl;
#    endif
            enabledExtensions.push_back(extension);
        }
    } else {
        deviceCerr() << "xess device extensions unavailable; skipping." << std::endl;
    }
#endif

    if (supportedExtensions.find(VK_AMD_DEVICE_COHERENT_MEMORY_EXTENSION_NAME) != supportedExtensions.end()) {
        enabledExtensions.push_back(VK_AMD_DEVICE_COHERENT_MEMORY_EXTENSION_NAME);
    }

    auto areRequiredExtensionsSupported = [&](const std::vector<std::string> &requiredExtensions) {
        for (const auto &requiredExtension : requiredExtensions) {
            if (requiredExtension == "VK_EXT_buffer_device_address") {
                // Covered by Vulkan 1.2 buffer device address feature path
                continue;
            }
            if (supportedExtensions.find(requiredExtension) == supportedExtensions.end()) { return false; }
        }
        return true;
    };

    dlssDeviceExtensionsCompatible_ = instance_->isDlssInstanceExtensionsCompatible() && dlssRequirementQuerySuccess &&
                                      areRequiredExtensionsSupported(dlssRequiredExtensions);
    dlssSRCompatible_ = instance_->isDlssSRInstanceExtensionsCompatible() && dlssSRQuery &&
                        areRequiredExtensionsSupported(dlssSRRequired);
    dlssFGCompatible_ = instance_->isDlssFGInstanceExtensionsCompatible() && dlssFGQuery &&
                        areRequiredExtensionsSupported(dlssFGRequired);
    if (!dlssDeviceExtensionsCompatible_) {
        deviceCerr() << "dlss device extension requirements are not fully satisfied." << std::endl;
    }

#ifdef MCVR_ENABLE_XESS
    xessDeviceExtensionsCompatible_ = instance_->isXessInstanceExtensionsCompatible() && xessRequirementQuerySuccess &&
                                      areRequiredExtensionsSupported(xessRequiredExtensions);
    if (!xessDeviceExtensionsCompatible_) {
        deviceCerr() << "xess device extension requirements are not fully satisfied." << std::endl;
    }
#endif

    const auto executableRequested = std::getenv("MCVR_PIPELINE_EXECUTABLE_STATS");
    auto executableFeatures = capabilities.supportedExecutable;
    if (executableRequested && std::string_view(executableRequested) == "1" &&
        supportedExtensions.contains(VK_KHR_PIPELINE_EXECUTABLE_PROPERTIES_EXTENSION_NAME)) {
        pipelineExecutableStats_ = executableFeatures.pipelineExecutableInfo;
        if (pipelineExecutableStats_) enabledExtensions.push_back(VK_KHR_PIPELINE_EXECUTABLE_PROPERTIES_EXTENSION_NAME);
    }
    if (executableRequested)
        deviceCout() << "Pipeline executable statistics supported=" << pipelineExecutableStats_ << std::endl;
    auto reorderFeatures = capabilities.supportedReorder;
#ifdef VK_EXT_ray_tracing_invocation_reorder
    auto reorderExtFeatures = capabilities.supportedReorderExt;
#endif
    if (const char *mode = std::getenv("RADIANCE_SER_MODE"); mode && std::string_view(mode) != "0") {
        const std::string_view requested(mode);
        if (requested != "1" && requested != "2" && requested != "3" && requested != "4")
            throw std::invalid_argument("RADIANCE_SER_MODE must be 0 through 4");
        if ((requested == "1" || requested == "2") && reorderFeatures.rayTracingInvocationReorder &&
            supportedExtensions.contains(VK_NV_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME)) {
            VkPhysicalDeviceRayTracingInvocationReorderPropertiesNV properties{
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_INVOCATION_REORDER_PROPERTIES_NV};
            VkPhysicalDeviceProperties2 properties2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, &properties};
            vkGetPhysicalDeviceProperties2(physicalDevice_->vkPhysicalDevice(), &properties2);
            if (requested == "1" || properties.rayTracingInvocationReorderReorderingHint ==
                                        VK_RAY_TRACING_INVOCATION_REORDER_MODE_REORDER_NV) {
                enabledExtensions.push_back(VK_NV_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME);
                rayReorderMode_ = requested == "1" ? 1 : 2;
            }
        }
#ifdef VK_EXT_ray_tracing_invocation_reorder
        if ((requested == "3" || requested == "4") && reorderExtFeatures.rayTracingInvocationReorder &&
            supportedExtensions.contains(VK_EXT_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME)) {
            VkPhysicalDeviceRayTracingInvocationReorderPropertiesEXT properties{
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_INVOCATION_REORDER_PROPERTIES_EXT};
            VkPhysicalDeviceProperties2 properties2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, &properties};
            vkGetPhysicalDeviceProperties2(physicalDevice_->vkPhysicalDevice(), &properties2);
            if (requested == "3" || properties.rayTracingInvocationReorderReorderingHint ==
                                        VK_RAY_TRACING_INVOCATION_REORDER_MODE_REORDER_EXT) {
                enabledExtensions.push_back(VK_EXT_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME);
                rayReorderMode_ = requested == "3" ? 3 : 4;
            }
        }
#endif
        deviceCout() << "SER requested=" << mode << " enabled=" << rayReorderMode_ << std::endl;
    }
    opacityMicromapEnabled_ = std::getenv("RADIANCE_REPLAY_OPACITY") && capabilities.supportedOpacityMicromap.micromap;
    if (opacityMicromapEnabled_) enabledExtensions.push_back(VK_EXT_OPACITY_MICROMAP_EXTENSION_NAME);
    std::vector<const char *> filteredExtensions;
    if (supportedExtensions.contains(VK_EXT_HDR_METADATA_EXTENSION_NAME))
        enabledExtensions.push_back(VK_EXT_HDR_METADATA_EXTENSION_NAME);
    filteredExtensions.reserve(enabledExtensions.size());
    memoryBudget_ = supportedExtensions.contains(VK_EXT_MEMORY_BUDGET_EXTENSION_NAME);
    if (memoryBudget_) enabledExtensions.push_back(VK_EXT_MEMORY_BUDGET_EXTENSION_NAME);
    if (supportedExtensions.contains(VK_KHR_COMPUTE_SHADER_DERIVATIVES_EXTENSION_NAME))
        enabledExtensions.push_back(VK_KHR_COMPUTE_SHADER_DERIVATIVES_EXTENSION_NAME);
    const auto captureFlags = mcvr::diagnostics::local_gpu_capture::flags();
    if (captureFlags && supportedExtensions.contains(VK_NV_DEVICE_DIAGNOSTICS_CONFIG_EXTENSION_NAME))
        enabledExtensions.push_back(VK_NV_DEVICE_DIAGNOSTICS_CONFIG_EXTENSION_NAME);
    if (mcvr::diagnostics::device_loss::enabled()) {
        if (supportedExtensions.contains(VK_EXT_DEVICE_FAULT_EXTENSION_NAME))
            enabledExtensions.push_back(VK_EXT_DEVICE_FAULT_EXTENSION_NAME);
        if (supportedExtensions.contains(VK_NV_DEVICE_DIAGNOSTIC_CHECKPOINTS_EXTENSION_NAME)) {
            enabledExtensions.push_back(VK_NV_DEVICE_DIAGNOSTIC_CHECKPOINTS_EXTENSION_NAME);
            checkpoints_ = true;
        }
    }
    std::unordered_set<std::string> seenExtensions;
    for (const auto *ext : enabledExtensions) {
        if (supportedExtensions.find(ext) == supportedExtensions.end()) {
            deviceCerr() << "extension not supported, skipping: " << ext << std::endl;
            continue;
        }
        if (!seenExtensions.insert(ext).second) { continue; }
        filteredExtensions.push_back(ext);
    }

#ifdef DEBUG
    deviceCout() << "selected instance extensions:" << std::endl;
    for (int i = 0; i < filteredExtensions.size(); i++) { deviceCout() << "\t" << filteredExtensions[i] << std::endl; }
#endif

    std::unordered_set<std::string> selectedExtensions;
    selectedExtensions.reserve(filteredExtensions.size());
    for (const auto *ext : filteredExtensions) { selectedExtensions.insert(ext); }
    auto hasExtension = [&](const char *name) { return selectedExtensions.find(name) != selectedExtensions.end(); };

    // enabling features
    VkPhysicalDeviceCoherentMemoryFeaturesAMD coherentMemoryFeatures{};
    coherentMemoryFeatures.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COHERENT_MEMORY_FEATURES_AMD;
    coherentMemoryFeatures.deviceCoherentMemory =
        hasExtension(VK_AMD_DEVICE_COHERENT_MEMORY_EXTENSION_NAME) ?
            capabilities.supportedCoherentMemoryFeatures.deviceCoherentMemory :
            VK_FALSE;

    VkPhysicalDeviceVertexInputDynamicStateFeaturesEXT vertexInputDynamicState{};
    vertexInputDynamicState.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_INPUT_DYNAMIC_STATE_FEATURES_EXT;
    vertexInputDynamicState.pNext =
        hasExtension(VK_AMD_DEVICE_COHERENT_MEMORY_EXTENSION_NAME) ? &coherentMemoryFeatures : nullptr;
    vertexInputDynamicState.vertexInputDynamicState =
        hasExtension(VK_EXT_VERTEX_INPUT_DYNAMIC_STATE_EXTENSION_NAME) ?
            capabilities.supportedVertexInputDynamicState.vertexInputDynamicState :
            VK_FALSE;

    VkPhysicalDeviceExtendedDynamicState3FeaturesEXT extendedDynamicState3{};
    extendedDynamicState3.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_3_FEATURES_EXT;
    extendedDynamicState3.pNext = hasExtension(VK_EXT_VERTEX_INPUT_DYNAMIC_STATE_EXTENSION_NAME) ?
                                      static_cast<void *>(&vertexInputDynamicState) :
                                      vertexInputDynamicState.pNext;
    if (hasExtension(VK_EXT_EXTENDED_DYNAMIC_STATE_3_EXTENSION_NAME)) {
        extendedDynamicState3.extendedDynamicState3PolygonMode =
            capabilities.supportedExtendedDynamicState3.extendedDynamicState3PolygonMode;
        extendedDynamicState3.extendedDynamicState3ColorBlendEnable =
            capabilities.supportedExtendedDynamicState3.extendedDynamicState3ColorBlendEnable;
        extendedDynamicState3.extendedDynamicState3ColorBlendEquation =
            capabilities.supportedExtendedDynamicState3.extendedDynamicState3ColorBlendEquation;
        extendedDynamicState3.extendedDynamicState3ColorWriteMask =
            capabilities.supportedExtendedDynamicState3.extendedDynamicState3ColorWriteMask;
        extendedDynamicState3.extendedDynamicState3LogicOpEnable =
            capabilities.supportedExtendedDynamicState3.extendedDynamicState3LogicOpEnable;
    }

    VkPhysicalDeviceExtendedDynamicState2FeaturesEXT extendedDynamicState2{};
    extendedDynamicState2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_2_FEATURES_EXT;
    extendedDynamicState2.pNext = &extendedDynamicState3;
    if (hasExtension(VK_EXT_EXTENDED_DYNAMIC_STATE_2_EXTENSION_NAME)) {
        extendedDynamicState2.extendedDynamicState2 = capabilities.supportedExtendedDynamicState2.extendedDynamicState2;
        extendedDynamicState2.extendedDynamicState2LogicOp =
            capabilities.supportedExtendedDynamicState2.extendedDynamicState2LogicOp ? VK_TRUE : VK_FALSE;

        // Store the flag for runtime checks
        extendedDynamicState2LogicOp_ = extendedDynamicState2.extendedDynamicState2LogicOp &&
                                        extendedDynamicState3.extendedDynamicState3LogicOpEnable &&
                                        capabilities.supportedFeatures2.features.logicOp;

#ifdef DEBUG
        mcvr::log::debug("Device") << "extendedDynamicState2="
                                   << (extendedDynamicState2.extendedDynamicState2 ? "YES" : "NO") << std::endl;
        mcvr::log::debug("Device") << "extendedDynamicState2LogicOp="
                                   << (extendedDynamicState2.extendedDynamicState2LogicOp ? "YES" : "NO") << std::endl;
#endif

        extendedDynamicState2.extendedDynamicState2PatchControlPoints = VK_FALSE;
    } else {
        mcvr::log::warn("Device") << "VK_EXT_extended_dynamic_state2 is unavailable" << std::endl;
    }

    VkPhysicalDeviceVulkan13Features vulkan13Features{};
    vulkan13Features.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;
    vulkan13Features.pNext = hasExtension(VK_EXT_EXTENDED_DYNAMIC_STATE_2_EXTENSION_NAME) ?
                                 static_cast<void *>(&extendedDynamicState2) :
                                 static_cast<void *>(&extendedDynamicState3);
    vulkan13Features.shaderDemoteToHelperInvocation = capabilities.supportedVulkan13.shaderDemoteToHelperInvocation;
    vulkan13Features.synchronization2 = capabilities.supportedVulkan13.synchronization2;
    // Streamline 2.14.1 sl.common creates a private slot during Vulkan initialization.
    // The create-device proxy does not enable this member in our existing Vulkan13 structure.
    vulkan13Features.privateData = capabilities.supportedVulkan13.privateData;

    VkPhysicalDeviceVulkan11Features vulkan11Features{};
    vulkan11Features.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES;
    vulkan11Features.pNext = &vulkan13Features;
    vulkan11Features.storageBuffer16BitAccess = capabilities.supportedVulkan11.storageBuffer16BitAccess;

    VkPhysicalDeviceVulkan12Features vulkan12Features{};
    vulkan12Features.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
    vulkan12Features.pNext = &vulkan11Features;
    vulkan12Features.bufferDeviceAddress = capabilities.supportedVulkan12.bufferDeviceAddress;
    vulkan12Features.descriptorBindingUpdateUnusedWhilePending =
        capabilities.supportedVulkan12.descriptorBindingUpdateUnusedWhilePending;
    vulkan12Features.descriptorBindingPartiallyBound = capabilities.supportedVulkan12.descriptorBindingPartiallyBound;
    vulkan12Features.descriptorIndexing = capabilities.supportedVulkan12.descriptorIndexing;
    vulkan12Features.runtimeDescriptorArray = capabilities.supportedVulkan12.runtimeDescriptorArray;
    vulkan12Features.descriptorBindingVariableDescriptorCount =
        capabilities.supportedVulkan12.descriptorBindingVariableDescriptorCount;
    vulkan12Features.shaderSampledImageArrayNonUniformIndexing =
        capabilities.supportedVulkan12.shaderSampledImageArrayNonUniformIndexing;
    vulkan12Features.descriptorBindingUniformBufferUpdateAfterBind =
        capabilities.supportedVulkan12.descriptorBindingUniformBufferUpdateAfterBind;
    vulkan12Features.descriptorBindingSampledImageUpdateAfterBind =
        capabilities.supportedVulkan12.descriptorBindingSampledImageUpdateAfterBind;
    vulkan12Features.descriptorBindingStorageImageUpdateAfterBind =
        capabilities.supportedVulkan12.descriptorBindingStorageImageUpdateAfterBind;
    vulkan12Features.descriptorBindingStorageBufferUpdateAfterBind =
        capabilities.supportedVulkan12.descriptorBindingStorageBufferUpdateAfterBind;
    vulkan12Features.shaderFloat16 = capabilities.supportedVulkan12.shaderFloat16;
    vulkan12Features.shaderBufferInt64Atomics = capabilities.supportedVulkan12.shaderBufferInt64Atomics;
    vulkan12Features.shaderStorageBufferArrayNonUniformIndexing =
        capabilities.supportedVulkan12.shaderStorageBufferArrayNonUniformIndexing;
    vulkan12Features.shaderStorageImageArrayNonUniformIndexing =
        capabilities.supportedVulkan12.shaderStorageImageArrayNonUniformIndexing;
    vulkan12Features.shaderUniformBufferArrayNonUniformIndexing =
        capabilities.supportedVulkan12.shaderUniformBufferArrayNonUniformIndexing;

    VkPhysicalDeviceAccelerationStructureFeaturesKHR accelerationStructureFeatures = {};
    accelerationStructureFeatures.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR;
    accelerationStructureFeatures.pNext = &vulkan12Features;
    if (hasExtension(VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME)) {
        accelerationStructureFeatures.accelerationStructure =
            capabilities.supportedAccelerationStructureFeatures.accelerationStructure;
        accelerationStructureFeatures.descriptorBindingAccelerationStructureUpdateAfterBind =
            capabilities.supportedAccelerationStructureFeatures.descriptorBindingAccelerationStructureUpdateAfterBind;
    }

    VkPhysicalDeviceRayTracingPipelineFeaturesKHR rayTracingFeatures = {};
    rayTracingFeatures.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR;
    rayTracingFeatures.pNext = &accelerationStructureFeatures;
    if (hasExtension(VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME)) {
        rayTracingFeatures.rayTracingPipeline = capabilities.supportedRayTracingFeatures.rayTracingPipeline;
    }

    VkPhysicalDeviceFeatures features = {};
    features.independentBlend = capabilities.supportedFeatures2.features.independentBlend;
    features.tessellationShader = capabilities.supportedFeatures2.features.tessellationShader;
    features.multiDrawIndirect = capabilities.supportedFeatures2.features.multiDrawIndirect;
    features.drawIndirectFirstInstance = capabilities.supportedFeatures2.features.drawIndirectFirstInstance;
    multiDrawIndirect_ = features.multiDrawIndirect == VK_TRUE;
    drawIndirectFirstInstance_ = features.drawIndirectFirstInstance == VK_TRUE;
    tessellation_ = features.tessellationShader == VK_TRUE;
    features.shaderClipDistance = capabilities.supportedFeatures2.features.shaderClipDistance;
    features.shaderCullDistance = capabilities.supportedFeatures2.features.shaderCullDistance;
    features.logicOp = capabilities.supportedFeatures2.features.logicOp;
    features.fillModeNonSolid = capabilities.supportedFeatures2.features.fillModeNonSolid;
    features.depthBiasClamp = capabilities.supportedFeatures2.features.depthBiasClamp;
    features.shaderInt64 = capabilities.supportedFeatures2.features.shaderInt64;
    features.shaderFloat64 = capabilities.supportedFeatures2.features.shaderFloat64;
    features.shaderInt16 = capabilities.supportedFeatures2.features.shaderInt16;
    features.shaderStorageImageReadWithoutFormat =
        capabilities.supportedFeatures2.features.shaderStorageImageReadWithoutFormat;
    features.shaderStorageImageWriteWithoutFormat =
        capabilities.supportedFeatures2.features.shaderStorageImageWriteWithoutFormat;

    VkPhysicalDeviceFeatures2 features2 = {};
    features2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
    features2.pNext = &rayTracingFeatures;
    features2.features = features;
    // Current SDK kernels declare ComputeDerivativeGroupQuadsKHR. Extension availability
    // alone does not enable this feature on the SERVICE-created device.
    VkPhysicalDeviceComputeShaderDerivativesFeaturesKHR computeDerivatives{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COMPUTE_SHADER_DERIVATIVES_FEATURES_KHR};
    if (hasExtension(VK_KHR_COMPUTE_SHADER_DERIVATIVES_EXTENSION_NAME)) {
        computeDerivatives.computeDerivativeGroupQuads =
            capabilities.supportedComputeDerivatives.computeDerivativeGroupQuads;
        computeDerivatives.computeDerivativeGroupLinear =
            capabilities.supportedComputeDerivatives.computeDerivativeGroupLinear;
        computeDerivatives.pNext = features2.pNext;
        features2.pNext = &computeDerivatives;
    }
    VkPhysicalDeviceFaultFeaturesEXT faultFeatures{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FAULT_FEATURES_EXT};
    faultFeatures.deviceFault =
        hasExtension(VK_EXT_DEVICE_FAULT_EXTENSION_NAME) ? capabilities.supportedFault.deviceFault : VK_FALSE;
    faultDiagnostics_ = faultFeatures.deviceFault == VK_TRUE;
    if (faultDiagnostics_) {
        faultFeatures.pNext = features2.pNext;
        features2.pNext = &faultFeatures;
    }
    if (pipelineExecutableStats_) {
        executableFeatures.pNext = features2.pNext;
        features2.pNext = &executableFeatures;
    }
    if (rayReorderMode_ == 1 || rayReorderMode_ == 2) {
        reorderFeatures.pNext = features2.pNext;
        features2.pNext = &reorderFeatures;
    }
#ifdef VK_EXT_ray_tracing_invocation_reorder
    if (rayReorderMode_ == 3 || rayReorderMode_ == 4) {
        reorderExtFeatures.pNext = features2.pNext;
        features2.pNext = &reorderExtFeatures;
    }
#endif
    VkPhysicalDeviceOpacityMicromapFeaturesEXT opacityMicromap{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_OPACITY_MICROMAP_FEATURES_EXT};
    if (opacityMicromapEnabled_) {
        opacityMicromap.micromap = VK_TRUE;
        opacityMicromap.pNext = features2.pNext;
        features2.pNext = &opacityMicromap;
    }
    VkDeviceDiagnosticsConfigCreateInfoNV captureConfig{VK_STRUCTURE_TYPE_DEVICE_DIAGNOSTICS_CONFIG_CREATE_INFO_NV};
    VkPhysicalDeviceDiagnosticsConfigFeaturesNV captureFeatures{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DIAGNOSTICS_CONFIG_FEATURES_NV};
    if (captureFlags && hasExtension(VK_NV_DEVICE_DIAGNOSTICS_CONFIG_EXTENSION_NAME)) {
        captureFeatures.diagnosticsConfig = capabilities.supportedCapture.diagnosticsConfig;
        if (!captureFeatures.diagnosticsConfig)
            mcvr::failure::raise(mcvr::failure::Kind::initialization, VK_ERROR_FEATURE_NOT_PRESENT,
                                 "local GPU capture diagnosticsConfig feature");
        captureFeatures.pNext = features2.pNext;
        features2.pNext = &captureFeatures;
        // Deliberately exclude automatic call stacks and additional shader-error modes.
        captureConfig.flags = captureFlags & (VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_RESOURCE_TRACKING_BIT_NV |
                                              VK_DEVICE_DIAGNOSTICS_CONFIG_ENABLE_SHADER_DEBUG_INFO_BIT_NV);
        deviceCout() << "External local GPU capture configured before device creation, flags=" << captureConfig.flags
                     << " diagnosticsConfig=" << captureFeatures.diagnosticsConfig << std::endl;
    } else if (captureFlags) {
        mcvr::failure::raise(mcvr::failure::Kind::initialization, VK_ERROR_EXTENSION_NOT_PRESENT,
                             "local GPU capture VK_NV_device_diagnostics_config");
    }

#ifdef MCVR_ENABLE_XESS
    if (xessDeviceExtensionsCompatible_) {
        void *featureChain = &features2;
        if (!mcvr::XeSSWrapper::getRequiredDeviceFeatures(instance_->vkInstance(), physicalDevice_->vkPhysicalDevice(),
                                                          &featureChain)) {
            xessDeviceExtensionsCompatible_ = false;
            deviceCerr() << "xess device feature requirements are not fully satisfied." << std::endl;
        }
    }
#endif

    // create logical device
    VkDeviceCreateInfo deviceCreateInfo = {};
    captureConfig.pNext = &features2;
    const void *deviceCreateChain =
        captureConfig.flags ? static_cast<void *>(&captureConfig) : static_cast<void *>(&features2);
    if (physicalDevice_->mainQueueIndex() == physicalDevice_->secondaryQueueIndex()) {
        std::vector<float> queuePriorities{{1.0, 0.0}};
        VkDeviceQueueCreateInfo queueCreateInfo = {};
        queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queueCreateInfo.queueFamilyIndex = physicalDevice_->mainQueueIndex();
        queueCreateInfo.queueCount = physicalDevice_->hasSecondaryQueue() ? 2 : 1;
        queueCreateInfo.pQueuePriorities = queuePriorities.data();

        deviceCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        deviceCreateInfo.pQueueCreateInfos = &queueCreateInfo;
        deviceCreateInfo.queueCreateInfoCount = 1;
        deviceCreateInfo.enabledExtensionCount = static_cast<uint32_t>(filteredExtensions.size());
        deviceCreateInfo.ppEnabledExtensionNames = filteredExtensions.data();
        deviceCreateInfo.pNext = deviceCreateChain;
        deviceCreateInfo.pEnabledFeatures = nullptr;

        if (const auto result =
                vkCreateDevice(physicalDevice_->vkPhysicalDevice(), &deviceCreateInfo, nullptr, &device_);
            result != VK_SUCCESS) {
            deviceCerr() << "Failed to create logical device!" << std::endl;
            mcvr::failure::raise(mcvr::failure::Kind::initialization, result, "vkCreateDevice(Streamline)");
        }
    } else {
        std::vector<float> queuePriorities{{1.0, 0.0}};
        std::vector<VkDeviceQueueCreateInfo> queueCreateInfos(2);
        queueCreateInfos[0].sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queueCreateInfos[0].queueFamilyIndex = physicalDevice_->mainQueueIndex();
        queueCreateInfos[0].queueCount = 1;
        queueCreateInfos[0].pQueuePriorities = &queuePriorities[0];

        queueCreateInfos[1].sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queueCreateInfos[1].queueFamilyIndex = physicalDevice_->secondaryQueueIndex();
        queueCreateInfos[1].queueCount = 1;
        queueCreateInfos[1].pQueuePriorities = &queuePriorities[1];

        deviceCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        deviceCreateInfo.queueCreateInfoCount = queueCreateInfos.size();
        deviceCreateInfo.pQueueCreateInfos = queueCreateInfos.data();
        deviceCreateInfo.enabledExtensionCount = static_cast<uint32_t>(filteredExtensions.size());
        deviceCreateInfo.ppEnabledExtensionNames = filteredExtensions.data();
        deviceCreateInfo.pNext = deviceCreateChain;
        deviceCreateInfo.pEnabledFeatures = nullptr;

        if (const auto result =
                vkCreateDevice(physicalDevice_->vkPhysicalDevice(), &deviceCreateInfo, nullptr, &device_);
            result != VK_SUCCESS) {
            deviceCerr() << "Failed to create logical device!" << std::endl;
            mcvr::failure::raise(mcvr::failure::Kind::initialization, result, "vkCreateDevice");
        }
    }

    volkLoadDevice(device_);
    mcvr::StreamlineRuntime::get().hookDevice(device_, physicalDevice_->vkPhysicalDevice());

#ifdef DEBUG
    deviceCout() << "Logical device created successfully!" << std::endl;
#endif

    vkGetDeviceQueue(device_, physicalDevice_->mainQueueIndex(), 0, &mainQueue_);
    vkGetDeviceQueue(device_, physicalDevice_->secondaryQueueIndex(),
                     physicalDevice_->mainQueueIndex() == physicalDevice_->secondaryQueueIndex() &&
                             physicalDevice_->hasSecondaryQueue() ?
                         1 :
                         0,
                     &secondaryQueue_);
    deviceCout() << "Renderer device=" << physicalDevice_->properties().deviceName
                 << " extensions=" << filteredExtensions.size() << " NGX SR/RR/FG requirements=" << dlssSRCompatible_
                 << "/" << dlssDeviceExtensionsCompatible_ << "/" << dlssFGCompatible_
                 << " HDR metadata extension=" << hasExtension(VK_EXT_HDR_METADATA_EXTENSION_NAME)
                 << " dynamic logic op=" << extendedDynamicState2LogicOp_ << std::endl;
    if (mcvr::diagnostics::device_loss::enabled()) {
        deviceCout() << "GPU diagnostics instance=" << instance_->vkInstance() << " device=" << device_
                     << " mainQueue=" << mainQueue_ << " secondaryQueue=" << secondaryQueue_
                     << " deviceFault=" << faultDiagnostics_ << " checkpoints=" << checkpoints_
                     << " privateData=" << vulkan13Features.privateData << std::endl;
        nameObject(VK_OBJECT_TYPE_QUEUE, reinterpret_cast<uint64_t>(mainQueue_), "MCVR main queue");
        nameObject(VK_OBJECT_TYPE_QUEUE, reinterpret_cast<uint64_t>(secondaryQueue_), "MCVR chunk queue");
        // SERVICE can create this device before the Java/native console bridge is ready.
        // Persist the actual enabled features once, instead of relying on early stdout.
        try {
            std::ofstream capability("radiance-gpu-capabilities.log", std::ios::app);
            capability << "device-created instance=" << instance_->vkInstance() << " device=" << device_
                       << " mainQueue=" << mainQueue_ << " secondaryQueue=" << secondaryQueue_
                       << " aftermathFlags=" << captureConfig.flags
                       << " diagnosticsConfig=" << captureFeatures.diagnosticsConfig
                       << " deviceFault=" << faultDiagnostics_ << " checkpoints=" << checkpoints_
                       << " debugNames=" << (vkSetDebugUtilsObjectNameEXT != nullptr)
                       << " deviceName=" << physicalDevice_->properties().deviceName
                       << " enabledExtensions=" << filteredExtensions.size() << " ngxSR=" << dlssSRCompatible_
                       << " ngxRR=" << dlssDeviceExtensionsCompatible_ << " ngxFG=" << dlssFGCompatible_
                       << " hdrMetadata=" << hasExtension(VK_EXT_HDR_METADATA_EXTENSION_NAME)
                       << " memoryBudget=" << memoryBudget_
                       << " computeDerivativeQuads=" << computeDerivatives.computeDerivativeGroupQuads << '\n';
            capability.close();
            if (!capability) mcvr::diagnostics::device_loss::note("capability-output-failed");
        } catch (...) { mcvr::diagnostics::device_loss::note("capability-output-failed"); }
    }

    try {
        std::filesystem::path pipelineCacheRoot;
        if (!Renderer::folderPath.empty()) {
            auto dataRoot = Renderer::folderPath;
            // The SERVICE bootstrap extracts binaries under .radiance/runtime/<hash>.
            // Cache compiled pipelines beside persistent game data, not per binary build.
            if (dataRoot.parent_path().filename() == "runtime" &&
                dataRoot.parent_path().parent_path().filename() == ".radiance") {
                dataRoot = dataRoot.parent_path().parent_path().parent_path() / "radiance";
            }
            pipelineCacheRoot = dataRoot / "cache" / "vulkan" / "pipelines";
        }
        pipelineCache_ =
            std::make_unique<PipelineCache>(device_, physicalDevice_->properties(), std::move(pipelineCacheRoot));
    } catch (const std::exception &exception) {
        deviceCerr() << "Pipeline cache unavailable; continuing without it: " << exception.what() << std::endl;
    }
}

vk::Device::~Device() {
    mcvr::diagnostics::device_loss::dump(isDeviceLost() ? "device-lost-close" : "normal-device-close", lastFailure());
    if (isDeviceLost()) mcvr::diagnostics::local_gpu_capture::beforeLostDeviceRelease();
    mcvr::diagnostics::as_lifetime::dump("radiance-as-lifetime-close.log");
    if (device_ != VK_NULL_HANDLE) {
        if (isDeviceLost()) captureFaultDiagnostics();
        if (!isDeviceLost()) {
            const VkResult result = vkDeviceWaitIdle(device_);
            if (result != VK_SUCCESS) { recordFailure(result, "vkDeviceWaitIdle(Device::~Device)"); }
        }
        if (isDeviceLost()) mcvr::diagnostics::local_gpu_capture::beforeLostDeviceRelease();
        pipelineCache_.reset();
        mcvr::StreamlineRuntime::get().shutdown();
        vkDestroyDevice(device_, nullptr);
        device_ = VK_NULL_HANDLE;
    }

#ifdef DEBUG
    deviceCout() << "device deconstructed" << std::endl;
#endif
}

VkDevice &vk::Device::vkDevice() {
    return device_;
}

VkQueue &vk::Device::mainVkQueue() {
    return mainQueue_;
}

VkQueue &vk::Device::secondaryQueue() {
    return secondaryQueue_;
}

VkResult vk::Device::createGraphicsPipelines(uint32_t count,
                                             const VkGraphicsPipelineCreateInfo *createInfos,
                                             const VkAllocationCallbacks *allocator,
                                             VkPipeline *pipelines) {
    if (pipelineCache_) { return pipelineCache_->createGraphicsPipelines(count, createInfos, allocator, pipelines); }
    return vkCreateGraphicsPipelines(device_, VK_NULL_HANDLE, count, createInfos, allocator, pipelines);
}

VkResult vk::Device::createComputePipelines(uint32_t count,
                                            const VkComputePipelineCreateInfo *createInfos,
                                            const VkAllocationCallbacks *allocator,
                                            VkPipeline *pipelines) {
    if (pipelineCache_) { return pipelineCache_->createComputePipelines(count, createInfos, allocator, pipelines); }
    return vkCreateComputePipelines(device_, VK_NULL_HANDLE, count, createInfos, allocator, pipelines);
}

VkResult vk::Device::createRayTracingPipelines(VkDeferredOperationKHR deferredOperation,
                                               uint32_t count,
                                               const VkRayTracingPipelineCreateInfoKHR *createInfos,
                                               const VkAllocationCallbacks *allocator,
                                               VkPipeline *pipelines) {
    std::vector<VkRayTracingPipelineCreateInfoKHR> profiled;
    if (pipelineExecutableStats_) {
        profiled.assign(createInfos, createInfos + count);
        for (auto &info : profiled) info.flags |= VK_PIPELINE_CREATE_CAPTURE_STATISTICS_BIT_KHR;
        createInfos = profiled.data();
    }
    const auto result = pipelineCache_ ? pipelineCache_->createRayTracingPipelines(deferredOperation, count,
                                                                                   createInfos, allocator, pipelines) :
                                         vkCreateRayTracingPipelinesKHR(device_, deferredOperation, VK_NULL_HANDLE,
                                                                        count, createInfos, allocator, pipelines);
    if (result == VK_SUCCESS && pipelineExecutableStats_)
        for (uint32_t p = 0; p < count; ++p) {
            VkPipelineInfoKHR info{VK_STRUCTURE_TYPE_PIPELINE_INFO_KHR};
            info.pipeline = pipelines[p];
            uint32_t executables = 0;
            auto status = vkGetPipelineExecutablePropertiesKHR(device_, &info, &executables, nullptr);
            std::vector<VkPipelineExecutablePropertiesKHR> properties(
                executables, {VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_PROPERTIES_KHR});
            if (status == VK_SUCCESS)
                status = vkGetPipelineExecutablePropertiesKHR(device_, &info, &executables, properties.data());
            deviceCout() << "ExecutableStatistics pipeline=" << pipelines[p] << " status=" << status
                         << " count=" << executables << std::endl;
            if (status != VK_SUCCESS) continue;
            for (uint32_t e = 0; e < executables; ++e) {
                VkPipelineExecutableInfoKHR executable{VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_INFO_KHR};
                executable.pipeline = pipelines[p];
                executable.executableIndex = e;
                uint32_t statistics = 0;
                status = vkGetPipelineExecutableStatisticsKHR(device_, &executable, &statistics, nullptr);
                std::vector<VkPipelineExecutableStatisticKHR> values(
                    statistics, {VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_STATISTIC_KHR});
                if (status == VK_SUCCESS)
                    status = vkGetPipelineExecutableStatisticsKHR(device_, &executable, &statistics, values.data());
                auto log = deviceCout();
                log << "ExecutableStatistics pipeline=" << pipelines[p] << " executable=" << e
                    << " name=" << properties[e].name << " stages=" << properties[e].stages
                    << " subgroup=" << properties[e].subgroupSize << " status=" << status;
                if (status == VK_SUCCESS)
                    for (const auto &value : values) {
                        log << " | " << value.name << "=";
                        if (value.format == VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_UINT64_KHR)
                            log << value.value.u64;
                        else if (value.format == VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_INT64_KHR)
                            log << value.value.i64;
                        else if (value.format == VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_FLOAT64_KHR)
                            log << value.value.f64;
                        else
                            log << value.value.b32;
                    }
                log << std::endl;
            }
        }
    return result;
}

void vk::Device::nameObject(VkObjectType type, uint64_t handle, const char *label) const noexcept {
    if (!mcvr::diagnostics::device_loss::enabled() || !vkSetDebugUtilsObjectNameEXT || !handle) return;
    VkDebugUtilsObjectNameInfoEXT info{VK_STRUCTURE_TYPE_DEBUG_UTILS_OBJECT_NAME_INFO_EXT};
    info.objectType = type;
    info.objectHandle = handle;
    info.pObjectName = label;
    vkSetDebugUtilsObjectNameEXT(device_, &info);
}

void vk::Device::resource(VkObjectType type,
                          uint64_t handle,
                          uint64_t address,
                          uint64_t bytes,
                          const char *label,
                          bool created) const noexcept {
    if (!mcvr::diagnostics::device_loss::enabled() || !handle) return;
    mcvr::profile::AllocationScope scope(created ? "allocation.resource.register" : "allocation.resource.unregister",
                                         bytes);
    mcvr::diagnostics::device_loss::note(created ? "resource-create" : "resource-destroy-call", VK_SUCCESS,
                                         uint32_t(type), 0, handle, address, bytes);
    if (created) {
        char name[160];
        std::snprintf(name, sizeof(name), "%s [%016llx]", label, (unsigned long long)handle);
        nameObject(type, handle, name);
    }
}

void vk::Device::checkpoint(VkCommandBuffer commands, std::string_view label) const noexcept {
    if (!checkpoints_ || !vkCmdSetCheckpointNV || !commands) return;
    static std::atomic<uint32_t> sequence{1};
    const uint32_t id = sequence.fetch_add(1, std::memory_order_relaxed);
    const uint32_t fingerprint = mcvr::diagnostics::device_loss::passFingerprint(label);
    const uint64_t token = (uint64_t(fingerprint) << 32) | id;
    // NV's marker is an opaque value, never dereferenced by the driver. It contains no borrowed
    // string/object pointer and remains decodable after the originating CPU objects are retired.
    vkCmdSetCheckpointNV(commands, reinterpret_cast<const void *>(uintptr_t(token)));
    mcvr::diagnostics::device_loss::note("checkpoint-recorded-NOT-completed", VK_SUCCESS, fingerprint, id,
                                         reinterpret_cast<uint64_t>(commands));
}

void vk::Device::captureFaultDiagnostics() noexcept {
    if (!mcvr::diagnostics::device_loss::enabled() || !device_ || !isDeviceLost() || faultCaptured_.exchange(true))
        return;
    mcvr::diagnostics::as_lifetime::dump("radiance-as-lifetime-fault.log");
    try {
        std::ofstream log("radiance-gpu-fault.log", std::ios::app);
        if (!log) return;
        log << "BEGIN device=" << device_ << " faultEnabled=" << faultDiagnostics_ << " checkpoints=" << checkpoints_
            << '\n';
        if (faultDiagnostics_ && vkGetDeviceFaultInfoEXT) {
            auto data = mcvr::diagnostics::captureFault(device_, vkGetDeviceFaultInfoEXT);
            log << "deviceFault countResult=" << data.countResult << " detailResult=" << data.detailResult
                << " requestedAddresses=" << data.requested.addressInfoCount
                << " requestedVendors=" << data.requested.vendorInfoCount << " description=" << data.description.data()
                << '\n';
            for (size_t i = 0; i < std::min<size_t>(data.returned.addressInfoCount, data.addresses.size()); ++i) {
                const auto &a = data.addresses[i];
                log << "address type=" << a.addressType << " value=" << std::hex << a.reportedAddress
                    << " precision=" << a.addressPrecision << std::dec << '\n';
            }
            for (size_t i = 0; i < std::min<size_t>(data.returned.vendorInfoCount, data.vendors.size()); ++i) {
                auto v = data.vendors[i];
                v.description[VK_MAX_DESCRIPTION_SIZE - 1] = 0;
                log << "vendor code=" << v.vendorFaultCode << " data=" << v.vendorFaultData
                    << " description=" << v.description << '\n';
            }
        }
        if (checkpoints_ && vkGetQueueCheckpointDataNV) {
            for (auto queue : {mainQueue_, secondaryQueue_}) {
                if (!queue) continue;
                std::array<VkCheckpointDataNV, 32> entries{};
                for (auto &entry : entries) entry.sType = VK_STRUCTURE_TYPE_CHECKPOINT_DATA_NV;
                uint32_t count = uint32_t(entries.size());
                vkGetQueueCheckpointDataNV(queue, &count, entries.data());
                for (size_t i = 0; i < std::min<size_t>(count, entries.size()); ++i) {
                    auto token = reinterpret_cast<uintptr_t>(entries[i].pCheckpointMarker);
                    log << "GPU checkpoint queue=" << queue << " stage=" << entries[i].stage
                        << " fingerprint=" << uint32_t(uint64_t(token) >> 32) << " sequence=" << uint32_t(token)
                        << '\n';
                }
            }
        }
        log << "END (no GPU idle wait performed)\n";
    } catch (...) {
        failureState_.noteDetailFailure();
        mcvr::failure::globalState.noteDetailFailure();
    }
}

void vk::Device::recordFailure(VkResult result, std::string_view operation) noexcept {
    if (result == VK_SUCCESS || result == VK_NOT_READY || result == VK_TIMEOUT || result == VK_EVENT_SET ||
        result == VK_EVENT_RESET || result == VK_INCOMPLETE || result == VK_SUBOPTIMAL_KHR ||
        result == VK_ERROR_OUT_OF_DATE_KHR) {
        return;
    }

    const auto kind = result == VK_ERROR_DEVICE_LOST ? mcvr::failure::Kind::deviceLost : mcvr::failure::Kind::runtime;
    // Publish the process-visible state before attempting optional text or log
    // formatting. Both State instances preserve the first cause separately
    // from the monotonic device-lost cleanup flag.
    mcvr::failure::globalState.record(kind, result, operation);
    const bool firstLocalFailure = !failureState_.fatal();
    failureState_.record(kind, result, operation);
    if (result == VK_ERROR_DEVICE_LOST) captureFaultDiagnostics();
    if (firstLocalFailure) {
        try {
            deviceCerr() << operation << " failed with VkResult=" << result << std::endl;
        } catch (...) {
            failureState_.noteDetailFailure();
            mcvr::failure::globalState.noteDetailFailure();
        }
    }
}

bool vk::Device::hasFailure() const noexcept {
    return failureState_.fatal() || mcvr::failure::globalState.fatal();
}

bool vk::Device::isDeviceLost() const noexcept {
    return !mcvr::failure::shouldWaitForGpuIdle(failureState_);
}

VkResult vk::Device::lastFailure() const noexcept {
    const VkResult global = mcvr::failure::globalState.firstResult();
    return global == VK_SUCCESS ? failureState_.firstResult() : global;
}

std::string vk::Device::lastFailureOperation() const {
    if (mcvr::failure::globalState.fatal()) return mcvr::failure::globalState.snapshot().operation;
    return failureState_.snapshot().operation;
}

bool vk::Device::hasExtendedDynamicState2LogicOp() const {
    return extendedDynamicState2LogicOp_;
}

bool vk::Device::isDlssDeviceExtensionsCompatible() const {
    return dlssDeviceExtensionsCompatible_;
}

bool vk::Device::isXessDeviceExtensionsCompatible() const {
    return xessDeviceExtensionsCompatible_;
}
