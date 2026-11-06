#include <volk.h>
#include <array>
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>
#include "core/render/material_palette.hpp"

static void require(VkResult result) {
    if (result != VK_SUCCESS) throw std::runtime_error("Vulkan result " + std::to_string(result));
}
struct Vec4 {
    float x, y, z, w;
};
static bool near(float a, float b, float epsilon = 2e-5f) {
    return std::isfinite(a) && std::isfinite(b) && std::abs(a - b) <= epsilon;
}
static bool near3(Vec4 a, Vec4 b) {
    return near(a.x, b.x) && near(a.y, b.y) && near(a.z, b.z);
}

int main(int argc, char **argv) {
    try {
        if (argc != 2 && argc != 3) return 1;
        const bool display = argc == 3 && std::string(argv[2]) == "--display-colour";
        const bool colourField = argc == 3 && std::string(argv[2]) == "--colour-field";
        const bool reconstruction = argc == 3 && std::string(argv[2]) == "--reconstruction-material";
        const bool palette = argc == 3 && std::string(argv[2]) == "--material-palette";
        const bool coordinates = argc == 3 && std::string(argv[2]) == "--layered-coordinates";
        const bool coveragePath = argc == 3 && std::string(argv[2]) == "--coverage-path";
        const bool coverage = argc == 3 && std::string(argv[2]) == "--coverage-blend";
        const bool layers = argc == 3 && std::string(argv[2]) == "--surface-layers";
        require(volkInitialize());
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        app.apiVersion = VK_API_VERSION_1_2;
        VkInstanceCreateInfo instanceInfo{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        instanceInfo.pApplicationInfo = &app;
        VkInstance instance{};
        require(vkCreateInstance(&instanceInfo, nullptr, &instance));
        volkLoadInstance(instance);
        uint32_t deviceCount = 0;
        require(vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr));
        std::vector<VkPhysicalDevice> physicals(deviceCount);
        require(vkEnumeratePhysicalDevices(instance, &deviceCount, physicals.data()));
        VkPhysicalDevice physical{};
        uint32_t family = 0;
        for (auto candidate : physicals) {
            uint32_t families = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &families, nullptr);
            std::vector<VkQueueFamilyProperties> queues(families);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &families, queues.data());
            for (uint32_t index = 0; index < families; ++index) {
                if ((queues[index].queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                    physical = candidate;
                    family = index;
                    break;
                }
            }
            if (physical) break;
        }
        if (!physical) {
            vkDestroyInstance(instance, nullptr);
            return 77;
        }
        float priority = 1.0f;
        VkDeviceQueueCreateInfo queueInfo{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        queueInfo.queueFamilyIndex = family;
        queueInfo.queueCount = 1;
        queueInfo.pQueuePriorities = &priority;
        VkDeviceCreateInfo deviceInfo{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        deviceInfo.queueCreateInfoCount = 1;
        deviceInfo.pQueueCreateInfos = &queueInfo;
        VkPhysicalDeviceVulkan12Features addresses{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
        VkPhysicalDeviceFeatures features{};
        if (palette) {
            addresses.bufferDeviceAddress = VK_TRUE;
            features.shaderInt64 = VK_TRUE;
            deviceInfo.pNext = &addresses;
            deviceInfo.pEnabledFeatures = &features;
        }
        if (coordinates) {
            features.shaderFloat64 = VK_TRUE;
            deviceInfo.pEnabledFeatures = &features;
        }
        VkDevice device{};
        require(vkCreateDevice(physical, &deviceInfo, nullptr, &device));
        volkLoadDevice(device);
        VkQueue queue{};
        vkGetDeviceQueue(device, family, 0, &queue);

        constexpr uint32_t cases = 10, rowsPerCase = 8;
        const VkDeviceSize bytes = palette ? 32768 : cases * rowsPerCase * sizeof(Vec4);
        VkBufferCreateInfo bufferInfo{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        bufferInfo.size = bytes;
        bufferInfo.usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        if (palette) bufferInfo.usage |= VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
        VkBuffer buffer{};
        require(vkCreateBuffer(device, &bufferInfo, nullptr, &buffer));
        VkMemoryRequirements requirements{};
        vkGetBufferMemoryRequirements(device, buffer, &requirements);
        VkPhysicalDeviceMemoryProperties memoryProperties{};
        vkGetPhysicalDeviceMemoryProperties(physical, &memoryProperties);
        uint32_t memoryType = memoryProperties.memoryTypeCount;
        constexpr auto host = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        for (uint32_t i = 0; i < memoryProperties.memoryTypeCount; ++i)
            if ((requirements.memoryTypeBits & (1u << i)) != 0 &&
                (memoryProperties.memoryTypes[i].propertyFlags & host) == host) {
                memoryType = i;
                break;
            }
        if (memoryType == memoryProperties.memoryTypeCount)
            throw std::runtime_error("No host-coherent memory for bounded spring fixture");
        VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = memoryType;
        VkMemoryAllocateFlagsInfo addressFlags{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO};
        addressFlags.flags = VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT;
        if (palette) allocation.pNext = &addressFlags;
        VkDeviceMemory memory{};
        require(vkAllocateMemory(device, &allocation, nullptr, &memory));
        require(vkBindBufferMemory(device, buffer, memory, 0));
        void *mapped{};
        require(vkMapMemory(device, memory, 0, bytes, 0, &mapped));
        std::memset(mapped, 0, bytes);
        if (palette) {
            using Material = std::array<uint32_t, 20>;
            std::vector<Material> source(96);
            for (size_t i = 0; i < source.size(); ++i)
                for (size_t w = 0; w < 20; ++w) source[i][w] = uint32_t(0x3f000000 + (i % 12) * 1024 + w);
            source[0][0] = 0x80000000U;
            source[1][0] = 0x7fc00031U;
            auto encoded = mcvr::materialPalette::pack<Material>(source);
            if (!encoded.dictionary) throw std::runtime_error("GPU fixture must exercise dictionary");
            VkBufferDeviceAddressInfo addressInfo{VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO};
            addressInfo.buffer = buffer;
            const uint64_t base = vkGetBufferDeviceAddress(device, &addressInfo);
            const size_t directOffset = 1312, dictionaryOffset = directOffset + source.size() * sizeof(Material);
            auto *data = static_cast<std::byte *>(mapped);
            std::memcpy(data + directOffset, source.data(), source.size() * sizeof(Material));
            std::memcpy(data + dictionaryOffset, encoded.bytes.data(), encoded.bytes.size());
            const std::array<uint64_t, 2> header{base + directOffset,
                                                 (base + dictionaryOffset) | mcvr::materialPalette::addressTag};
            std::memcpy(data + 1280, header.data(), 16);
        }

        struct ReconstructionImageState {
            VkDevice device{};
            VkImage image{};
            VkDeviceMemory memory{};
            VkImageView view{};
            VkPipeline pass1Pipeline{};
            void destroy() {
                if (!device) return;
                if (pass1Pipeline) vkDestroyPipeline(device, pass1Pipeline, nullptr);
                if (view) vkDestroyImageView(device, view, nullptr);
                if (image) vkDestroyImage(device, image, nullptr);
                if (memory) vkFreeMemory(device, memory, nullptr);
                pass1Pipeline = VK_NULL_HANDLE;
                view = VK_NULL_HANDLE;
                image = VK_NULL_HANDLE;
                memory = VK_NULL_HANDLE;
            }
            ~ReconstructionImageState() {
                destroy();
            }
        } recon{device};
        if (reconstruction) {
            VkFormatProperties formatProperties{};
            vkGetPhysicalDeviceFormatProperties(physical, VK_FORMAT_R16G16B16A16_SFLOAT, &formatProperties);
            if ((formatProperties.optimalTilingFeatures & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) == 0u) {
                throw std::runtime_error("VK_FORMAT_R16G16B16A16_SFLOAT lacks VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT");
            }
            VkImageCreateInfo imageInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
            imageInfo.imageType = VK_IMAGE_TYPE_2D;
            imageInfo.format = VK_FORMAT_R16G16B16A16_SFLOAT;
            imageInfo.extent = {cases, 1u, 1u};
            imageInfo.mipLevels = 1u;
            imageInfo.arrayLayers = 4u;
            imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
            imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
            imageInfo.usage = VK_IMAGE_USAGE_STORAGE_BIT;
            imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
            imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            require(vkCreateImage(device, &imageInfo, nullptr, &recon.image));
            VkMemoryRequirements imageRequirements{};
            vkGetImageMemoryRequirements(device, recon.image, &imageRequirements);
            VkPhysicalDeviceMemoryProperties memoryProperties{};
            vkGetPhysicalDeviceMemoryProperties(physical, &memoryProperties);
            uint32_t memoryTypeIndex = memoryProperties.memoryTypeCount;
            for (uint32_t idx = 0u; idx < memoryProperties.memoryTypeCount; ++idx) {
                if ((imageRequirements.memoryTypeBits & (1u << idx)) != 0u &&
                    (memoryProperties.memoryTypes[idx].propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) ==
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) {
                    memoryTypeIndex = idx;
                    break;
                }
            }
            if (memoryTypeIndex >= memoryProperties.memoryTypeCount)
                throw std::runtime_error("No device-local memory type for reconstruction rgba16f image");
            VkMemoryAllocateInfo imageAllocate{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
            imageAllocate.allocationSize = imageRequirements.size;
            imageAllocate.memoryTypeIndex = memoryTypeIndex;
            require(vkAllocateMemory(device, &imageAllocate, nullptr, &recon.memory));
            require(vkBindImageMemory(device, recon.image, recon.memory, 0u));
            VkImageViewCreateInfo viewInfo{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            viewInfo.image = recon.image;
            viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D_ARRAY;
            viewInfo.format = VK_FORMAT_R16G16B16A16_SFLOAT;
            viewInfo.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0u, 1u, 0u, 4u};
            require(vkCreateImageView(device, &viewInfo, nullptr, &recon.view));
        }

        VkDescriptorSetLayoutBinding bindings[2]{
            {0u, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1u, VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {1u, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1u, VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
        };
        VkDescriptorSetLayoutCreateInfo layoutInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        layoutInfo.bindingCount = reconstruction ? 2u : 1u;
        layoutInfo.pBindings = bindings;
        VkDescriptorSetLayout layout{};
        require(vkCreateDescriptorSetLayout(device, &layoutInfo, nullptr, &layout));
        VkDescriptorPoolSize poolSizes[2]{
            {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1u},
            {VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1u},
        };
        VkDescriptorPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        poolInfo.maxSets = 1u;
        poolInfo.poolSizeCount = reconstruction ? 2u : 1u;
        poolInfo.pPoolSizes = poolSizes;
        VkDescriptorPool pool{};
        require(vkCreateDescriptorPool(device, &poolInfo, nullptr, &pool));
        VkDescriptorSetAllocateInfo setInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        setInfo.descriptorPool = pool;
        setInfo.descriptorSetCount = 1u;
        setInfo.pSetLayouts = &layout;
        VkDescriptorSet set{};
        require(vkAllocateDescriptorSets(device, &setInfo, &set));
        VkDescriptorBufferInfo descriptor{buffer, 0, bytes};
        VkDescriptorImageInfo imageDescriptor{VK_NULL_HANDLE, recon.view, VK_IMAGE_LAYOUT_GENERAL};
        VkWriteDescriptorSet writes[2]{};
        writes[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        writes[0].dstSet = set;
        writes[0].dstBinding = 0u;
        writes[0].descriptorCount = 1u;
        writes[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[0].pBufferInfo = &descriptor;
        writes[1].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        writes[1].dstSet = set;
        writes[1].dstBinding = 1u;
        writes[1].descriptorCount = 1u;
        writes[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        writes[1].pImageInfo = &imageDescriptor;
        vkUpdateDescriptorSets(device, reconstruction ? 2u : 1u, writes, 0u, nullptr);

        std::ifstream file(argv[1], std::ios::binary | std::ios::ate);
        if (!file) throw std::runtime_error("Missing spring material SPIR-V");
        size_t shaderBytes = static_cast<size_t>(file.tellg());
        if (shaderBytes == 0 || shaderBytes % 4 != 0) throw std::runtime_error("Invalid SPIR-V byte count");
        file.seekg(0);
        std::vector<uint32_t> words(shaderBytes / 4);
        file.read(reinterpret_cast<char *>(words.data()), shaderBytes);
        VkShaderModuleCreateInfo shaderInfo{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        shaderInfo.codeSize = shaderBytes;
        shaderInfo.pCode = words.data();
        VkShaderModule shader{};
        require(vkCreateShaderModule(device, &shaderInfo, nullptr, &shader));
        VkPipelineLayoutCreateInfo pipelineLayoutInfo{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        pipelineLayoutInfo.setLayoutCount = 1;
        pipelineLayoutInfo.pSetLayouts = &layout;
        VkPipelineLayout pipelineLayout{};
        require(vkCreatePipelineLayout(device, &pipelineLayoutInfo, nullptr, &pipelineLayout));
        uint32_t pass0Id = 0u;
        uint32_t pass1Id = 1u;
        VkSpecializationMapEntry passEntry{0u, 0u, sizeof(uint32_t)};
        VkSpecializationInfo pass0Spec{1u, &passEntry, sizeof(uint32_t), &pass0Id};
        VkSpecializationInfo pass1Spec{1u, &passEntry, sizeof(uint32_t), &pass1Id};
        VkComputePipelineCreateInfo pipelineInfo{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
        pipelineInfo.layout = pipelineLayout;
        pipelineInfo.stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                              nullptr,
                              0,
                              VK_SHADER_STAGE_COMPUTE_BIT,
                              shader,
                              "main",
                              reconstruction ? &pass0Spec : nullptr};
        VkPipeline pipeline{};
        require(vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &pipeline));
        if (reconstruction) {
            pipelineInfo.stage.pSpecializationInfo = &pass1Spec;
            require(vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &recon.pass1Pipeline));
        }

        VkCommandPoolCreateInfo commandPoolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        commandPoolInfo.queueFamilyIndex = family;
        VkCommandPool commandPool{};
        require(vkCreateCommandPool(device, &commandPoolInfo, nullptr, &commandPool));
        VkCommandBufferAllocateInfo commandInfo{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        commandInfo.commandPool = commandPool;
        commandInfo.commandBufferCount = 1;
        commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        VkCommandBuffer command{};
        require(vkAllocateCommandBuffers(device, &commandInfo, &command));
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        require(vkBeginCommandBuffer(command, &begin));
        if (reconstruction) {
            VkImageMemoryBarrier toGeneral{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            toGeneral.srcAccessMask = 0u;
            toGeneral.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
            toGeneral.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            toGeneral.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            toGeneral.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            toGeneral.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            toGeneral.image = recon.image;
            toGeneral.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0u, 1u, 0u, 4u};
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0u,
                                 0u, nullptr, 0u, nullptr, 1u, &toGeneral);
            vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0, 1, &set, 0, nullptr);
            vkCmdDispatch(command, cases, 1, 1);
            VkMemoryBarrier ssboPassBarrier{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
            ssboPassBarrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
            ssboPassBarrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
            VkImageMemoryBarrier imageReadBarrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            imageReadBarrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
            imageReadBarrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
            imageReadBarrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
            imageReadBarrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            imageReadBarrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            imageReadBarrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            imageReadBarrier.image = recon.image;
            imageReadBarrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0u, 1u, 0u, 4u};
            vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                                 0u, 1u, &ssboPassBarrier, 0u, nullptr, 1u, &imageReadBarrier);
            vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_COMPUTE, recon.pass1Pipeline);
            vkCmdDispatch(command, cases, 1, 1);
        } else {
            vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0, 1, &set, 0, nullptr);
            vkCmdDispatch(command, cases, 1, 1);
        }
        VkMemoryBarrier barrier{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
        barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
        vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &barrier,
                             0, nullptr, 0, nullptr);
        require(vkEndCommandBuffer(command));
        VkFenceCreateInfo fenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        VkFence fence{};
        require(vkCreateFence(device, &fenceInfo, nullptr, &fence));
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &command;
        require(vkQueueSubmit(queue, 1, &submit, fence));
        require(vkWaitForFences(device, 1, &fence, VK_TRUE, 10'000'000'000ull));
        recon.destroy();

        const auto *rows = static_cast<const Vec4 *>(mapped);
        for (uint32_t i = 0; i < cases; ++i) {
            const Vec4 *r = rows + i * rowsPerCase;
            if (coordinates) {
                const double x = i < 5 ? 20000001.25 : -20000001.25, z = i < 5 ? -29999997.75 : 29999997.75;
                auto fraction = [](double x) { return float(x - std::floor(x)); };
                const float scale = .04f;
                if (!near(r[0].x, .5f + .25f / 256) || !near(r[0].y, .5f + .75f / 256) ||
                    !near(r[1].x, fraction((x + .25) * double(scale))) ||
                    !near(r[1].y, fraction((z + .75) * double(scale))) || !near(r[2].x, -.125f * float(i)) ||
                    !near(r[2].z, .25f * float(i)) || (near(r[3].x, r[0].x) && near(r[3].y, r[0].y)) ||
                    !near(r[4].x, float((x + .25) * double(scale)), .1f)) {
                    std::cerr << "case=" << i << " mask=" << r[0].x << ',' << r[0].y << " uv=" << r[1].x << ','
                              << r[1].y << " expected=" << fraction((x + .25) * double(scale)) << ','
                              << fraction((z + .75) * double(scale)) << " origin=" << r[2].x << ',' << r[2].z
                              << " oldMask=" << r[3].x << ',' << r[3].y << " clamp=" << r[4].x
                              << " expectedClamp=" << float((x + .25) * double(scale)) << '\n';
                    throw std::runtime_error("Far/negative plane mask, UV, sampler mode or local origin failed");
                }
                continue;
            }
            if (reconstruction) {
                if (r[0].w != 1) {
                    std::cerr << "Reconstruction rgba16f roundtrip debug: case=" << i << " isDelta=" << r[3].x
                              << " hasPrev=" << r[3].y << " normalErr=" << r[3].z << " quantizedRoughness=" << r[3].w
                              << " layer1=(" << r[4].x << ',' << r[4].y << ',' << r[4].z << ',' << r[4].w << ")"
                              << " layer2=(" << r[5].x << ',' << r[5].y << ',' << r[5].z << ',' << r[5].w << ")"
                              << " checks=(" << r[6].x << ',' << r[6].y << ',' << r[6].z << ',' << r[6].w << ")\n";
                    throw std::runtime_error(
                        "Reconstruction rgba16f image write, barrier, or readback contract failed");
                }
                if (r[0].x != 1)
                    throw std::runtime_error(
                        "Reconstruction reflectance, material energy or hit-distance sentinel failed");
                continue;
            }
            if (palette) {
                if (r[0].x != 1) throw std::runtime_error("GPU exact material decode mismatch");
                continue;
            }
            if (display) {
                if (!near3(r[0], {float(i) / 9.f, .25f, .75f, 1}) || !near(r[2].x, .5080784f, 1e-4f) ||
                    !near(r[2].y, .7518271f, 1e-4f) || !near(r[2].z, 1.f, 1e-5f) || !near3(r[3], {1, 1, 1, 1}) ||
                    !near3(r[4], {2, 4, 8, 1}) || !near(r[5].x, .4613561f, 1e-5f) || !std::isfinite(r[1].x) ||
                    r[1].x < 0 || r[1].x > 5)
                    throw std::runtime_error("Display transfer/primaries/radiance contract failed case=" +
                                             std::to_string(i));
                if (i > 0) {
                    auto previous = static_cast<Vec4 *>(mapped) + (i - 1) * rowsPerCase;
                    if (r[1].x < previous[1].x) throw std::runtime_error("HDR shoulder lost luminance ordering");
                }
                continue;
            }
            if (colourField) {
                const float value = float(i + 1) / 10.0f, raised = std::pow(value, .8f);
                if (!near3(r[0], {value, .5f * value, .25f * value, 1}) || !near3(r[1], {.5f * raised, raised, 0, 1}) ||
                    !near3(r[2], {0, .7f * raised, raised, 1}) || !near(r[3].x, std::pow(value, std::exp(-.7f))) ||
                    !near(r[4].x, std::max(0.0f, value - .12f * raised)))
                    throw std::runtime_error("HSV source colour operation mismatch case=" + std::to_string(i));
                continue;
            }
            if (coveragePath) {
                const Vec4 expected[] = {{0, 0, 1, 0}, {0, 0, 1, 0}, {.5f, 0, .5f, 0}, {.5f, .25f, .25f, 0},
                                         {0, 0, 1, 0}, {1, 0, 0, 0}, {1, 0, 0, 0},     {.5f, 0, .5f, 0},
                                         {1, 0, 0, 0}, {1, 0, 0, 0}};
                const float visits[] = {1, 1, 2, 3, 2, 1, 1, 2, 1, 9};
                if (!near3(r[0], expected[i]) || !near(r[1].x, visits[i]) || !near(r[1].y, 0) ||
                    !near(r[1].z, i == 9 ? 1.0f : 0.0f) || !near(r[1].w, 77))
                    throw std::runtime_error("Production coverage walker state/order/tail mismatch case=" +
                                             std::to_string(i));
                continue;
            }
            if (coverage) {
                const float a = float(i) / 9.0f;
                const float tail = std::pow(.75f, 8.0f);
                if (!near(r[0].x, 4 * a) || !near(r[0].y, 0) || !near(r[0].z, 2 * (1 - a)) || !near(r[0].w, 0) ||
                    !near(r[1].x, .25f) || !near(r[1].y, .5f) || !near(r[1].z, .25f) || !near(r[1].w, 0) ||
                    !near(r[2].x, .2f) || !near(r[2].y, 0) || !near(r[2].z, 0) || !near(r[2].w, 0) ||
                    !near(r[3].x, 1 - tail) || !near(r[3].y, 0) || !near(r[3].z, 3 * tail) || !near(r[3].w, tail) ||
                    !near(r[4].x, 6 * a) || !near(r[4].y, 4 * a) || !near(r[4].z, 2 * a) || !near(r[5].x, 2 * a) ||
                    !near(r[5].y, 0) || !near(r[5].z, a))
                    throw std::runtime_error("Coverage HDR/order/source-discard/opaque/tail mismatch case=" +
                                             std::to_string(i));
                continue;
            }
            if (layers) {
                float a = float(i) / 9.0f;
                if (a < 0.1f) a = 0;
                if (!near(r[0].x, a, .012f) || !near(r[0].z, 1 - a, .012f) || !near(r[0].w, a, .012f) ||
                    !near(r[0].y, 0) || !near(r[1].x, 0) || !near(r[2].x, .25f, .012f) || !near(r[2].y, .5f, .012f) ||
                    !near(r[2].z, .25f, .012f))
                    throw std::runtime_error("Ordered layer coverage/emission or stage identity mismatch");
                continue;
            }
            const float expectedZ = i == 2 ? -1.0f : 1.0f;
            if (!near(r[0].x, 0) || !near(r[0].y, 0) || !near(r[0].z, expectedZ) || !near3(r[1], r[2]) ||
                !near3(r[3], r[4]))
                throw std::runtime_error("Spring base/cache/normal-map mismatch case=" + std::to_string(i));
            if (i < 4) {
                const float originalLength = std::sqrt(0.3f * 0.3f + 0.4f * 0.4f + 0.8660254f * 0.8660254f);
                float x = 0.3f / originalLength, y = 0.4f / originalLength;
                float z = 0.8660254f / originalLength;
                if (i == 2) {
                    y = -y;
                    z = -z;
                }
                if (i == 3) {
                    x *= 0.5f;
                    y *= 2.0f;
                    z *= 2.0f / 3.0f;
                    const float transformedLength = std::sqrt(x * x + y * y + z * z);
                    x /= transformedLength;
                    y /= transformedLength;
                    z /= transformedLength;
                }
                if (!near(r[1].x, x) || !near(r[1].y, y) || !near(r[1].z, z))
                    throw std::runtime_error("Triangle/source inverse-transpose oracle failed case=" +
                                             std::to_string(i));
            }
            if (i < 4 && near3(r[1], r[3]))
                throw std::runtime_error("Nonflat normal map had no effect case=" + std::to_string(i));
            if (i == 4 || i == 5 || i == 6 || i == 7 || i == 9)
                if (!near3(r[0], r[1]))
                    throw std::runtime_error("Invalid or untagged normal did not fall back to geometry");
            if (i == 8 && !(r[1].x > 0.99999f && r[1].z > 1e-5f && near3(r[1], r[2])))
                throw std::runtime_error("Near-cutoff source normal changed during cache decode");
            if (!near(r[5].y, 123) || !near(r[5].z, 456))
                throw std::runtime_error("Emissive-overlay ID channels changed");
            // Fixed source-byte cases: spring #EB3230 at 0/38/76/255 coverage,
            // entity hurt red with inverse alpha 178/255, and white-flash rows.
            constexpr std::array<std::array<float, 3>, cases> expectedTint{
                {{0.800000000f, 0.600000000f, 0.400000000f},
                 {0.818116109f, 0.539807766f, 0.368442907f},
                 {0.836232218f, 0.479615532f, 0.336885813f},
                 {0.860392157f, 0.418823529f, 0.279215686f},
                 {0.581176471f, 0.335058824f, 0.279215686f},
                 {0.400000000f, 0.480000000f, 0.400000000f},
                 {0.950588235f, 0.901176471f, 0.851764706f},
                 {0.800000000f, 0.600000000f, 0.400000000f},
                 {0.921568627f, 0.196078431f, 0.188235294f},
                 {0.880000000f, 0.760000000f, 0.640000000f}}};
            if (!near(r[7].x, expectedTint[i][0]) || !near(r[7].y, expectedTint[i][1]) ||
                !near(r[7].z, expectedTint[i][2]) || !near(r[7].w, i == 0 ? 0.0f : 1.0f))
                throw std::runtime_error("Source stress/hurt/white tint or cutout alpha changed case=" +
                                         std::to_string(i));
            if (i == 6) {
                if (!near(r[5].x, 167) || !near(r[5].w, -17) || !near(r[6].x, -19))
                    throw std::runtime_error("Ordinary cache auxiliary bytes changed");
            } else if (!near(r[5].x, r[1].x) || !near(r[5].w, r[1].y) || !near(r[6].x, r[1].z)) {
                throw std::runtime_error("Tagged normal was not stored in three auxiliary channels");
            }
        }

        vkDestroyFence(device, fence, nullptr);
        vkDestroyCommandPool(device, commandPool, nullptr);
        vkDestroyPipeline(device, pipeline, nullptr);
        vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
        vkDestroyShaderModule(device, shader, nullptr);
        vkDestroyDescriptorPool(device, pool, nullptr);
        vkDestroyDescriptorSetLayout(device, layout, nullptr);
        vkUnmapMemory(device, memory);
        vkDestroyBuffer(device, buffer, nullptr);
        vkFreeMemory(device, memory, nullptr);
        vkDestroyDevice(device, nullptr);
        vkDestroyInstance(instance, nullptr);
        if (coordinates) {
            std::cout
                << "10 far signed origins, camera motion, precise UV/mask and old-formula negative controls PASS\n";
            return 0;
        }
        if (palette) {
            std::cout << "96 real CPU-packed materials, tagged GPU decode vs direct bits PASS\n";
            return 0;
        }
        if (coveragePath) {
            std::cout
                << "10 GPU production coverage walker cases: opaque/zero/half/ordered layers, sky, purpose/UI/aux sibling isolation, finite stochastic tail PASS\n";
            return 0;
        }
        if (coverage) {
            std::cout
                << "10 GPU production coverage blend cases: HDR, alpha endpoints, ordering, source alpha/discard, mode separation, nonzero overflow tail PASS\n";
            return 0;
        }
        if (layers) {
            std::cout
                << "10 GPU ordered layer sampling cases, 65536 samples each, alpha/order/physical emission/stage identity PASS\n";
            return 0;
        }
        if (reconstruction) {
            std::cout
                << "10 GPU reconstruction material cases: fp32 BRDF/NRD contracts + two-pass rgba16f imageStore/barrier/imageLoad roundtrip; submit/fence/readback PASS\n";
            return 0;
        }
        std::cout
            << "10 GPU spring helper/cache cases: triangle-cross geom oracle, mirrored/nonuniform source normal, near-cutoff cache roundtrip, fixture nonflat map, cutout 0.099/0.100/0.101, source stress/hurt/white tint, ordinary cache bytes; submit/fence/readback PASS\n";
        return 0;
    } catch (const std::exception &failure) {
        std::cerr << failure.what() << '\n';
        return 1;
    }
}
