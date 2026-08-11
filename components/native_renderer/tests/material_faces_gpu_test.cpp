#include "core/render/material_faces.hpp"
#include "core/render/water_clip.hpp"
#include <glm/gtc/matrix_transform.hpp>
#include <array>
#include <cstring>
#include <cmath>
#include <fstream>
#include <functional>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>
#include <volk.h>
using namespace mcvr;
void check(VkResult r) {
    if (r != VK_SUCCESS) throw std::runtime_error("Vulkan result " + std::to_string(r));
}
struct Buffer {
    VkBuffer handle;
    VkDeviceMemory memory;
    void *mapped;
    VkDeviceAddress address;
};
struct AS {
    VkAccelerationStructureKHR handle;
    VkDeviceAddress address;
};
int main(int argc, char **argv) {
    try {
        if (argc != 2 && argc != 3) return 1;
        const bool coverage = argc == 3 && std::string(argv[2]) == "--coverage-blend";
        const bool optics = argc == 3 && std::string(argv[2]) == "--medium";
        const bool continuation = optics || (argc == 3 && std::string(argv[2]) == "--continuation");
        const bool queryPolicy = argc == 3 && std::string(argv[2]) == "--query-policy";
        const bool bilateral =
            coverage || continuation || queryPolicy || (argc == 3 && std::string(argv[2]) == "--bilateral");
        check(volkInitialize());
        VkApplicationInfo ai{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        ai.apiVersion = VK_API_VERSION_1_2;
        VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        ici.pApplicationInfo = &ai;
        VkInstance instance;
        check(vkCreateInstance(&ici, nullptr, &instance));
        volkLoadInstance(instance);
        uint32_t count = 0;
        check(vkEnumeratePhysicalDevices(instance, &count, nullptr));
        std::vector<VkPhysicalDevice> devices(count);
        check(vkEnumeratePhysicalDevices(instance, &count, devices.data()));
        VkPhysicalDevice physical{};
        for (auto d : devices) {
            VkPhysicalDeviceRayQueryFeaturesKHR rq{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_QUERY_FEATURES_KHR};
            VkPhysicalDeviceAccelerationStructureFeaturesKHR asFeatures{
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR};
            VkPhysicalDeviceBufferDeviceAddressFeatures addressFeatures{
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES};
            rq.pNext = &asFeatures;
            asFeatures.pNext = &addressFeatures;
            VkPhysicalDeviceFeatures2 f{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
            f.pNext = &rq;
            vkGetPhysicalDeviceFeatures2(d, &f);
            if (rq.rayQuery && asFeatures.accelerationStructure && addressFeatures.bufferDeviceAddress) {
                physical = d;
                break;
            }
        }
        if (!physical) {
            vkDestroyInstance(instance, nullptr);
            return 77;
        }
        vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, nullptr);
        std::vector<VkQueueFamilyProperties> qs(count);
        vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, qs.data());
        uint32_t family = 0;
        while (!(qs.at(family).queueFlags & VK_QUEUE_COMPUTE_BIT)) ++family;
        float priority = 1;
        VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        qi.queueFamilyIndex = family;
        qi.queueCount = 1;
        qi.pQueuePriorities = &priority;
        VkPhysicalDeviceRayQueryFeaturesKHR rq{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_QUERY_FEATURES_KHR};
        rq.rayQuery = VK_TRUE;
        VkPhysicalDeviceAccelerationStructureFeaturesKHR af{
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR};
        af.accelerationStructure = VK_TRUE;
        af.pNext = &rq;
        VkPhysicalDeviceBufferDeviceAddressFeatures bf{
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES};
        bf.bufferDeviceAddress = VK_TRUE;
        bf.pNext = &af;
        const char *extensions[] = {VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME, VK_KHR_RAY_QUERY_EXTENSION_NAME,
                                    VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME};
        VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        di.pNext = &bf;
        di.queueCreateInfoCount = 1;
        di.pQueueCreateInfos = &qi;
        di.enabledExtensionCount = 3;
        di.ppEnabledExtensionNames = extensions;
        VkDevice device;
        check(vkCreateDevice(physical, &di, nullptr, &device));
        volkLoadDevice(device);
        VkQueue queue;
        vkGetDeviceQueue(device, family, 0, &queue);
        VkPhysicalDeviceMemoryProperties mp;
        vkGetPhysicalDeviceMemoryProperties(physical, &mp);
        VkPhysicalDeviceAccelerationStructurePropertiesKHR asProperties{
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR};
        VkPhysicalDeviceProperties2 properties{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
        properties.pNext = &asProperties;
        vkGetPhysicalDeviceProperties2(physical, &properties);
        const VkDeviceSize scratchAlignment = asProperties.minAccelerationStructureScratchOffsetAlignment;
        std::vector<Buffer> buffers;
        std::vector<AS> structures;
        auto buffer = [&](VkDeviceSize bytes, VkBufferUsageFlags usage, const void *data = nullptr) {
            Buffer b{};
            VkBufferCreateInfo ci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
            ci.size = bytes;
            ci.usage = usage | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
            check(vkCreateBuffer(device, &ci, nullptr, &b.handle));
            VkMemoryRequirements req;
            vkGetBufferMemoryRequirements(device, b.handle, &req);
            uint32_t type = 0;
            constexpr auto hostFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            while (type < mp.memoryTypeCount && (!(req.memoryTypeBits & (1u << type)) ||
                                                 (mp.memoryTypes[type].propertyFlags & hostFlags) != hostFlags))
                ++type;
            if (type == mp.memoryTypeCount) throw std::runtime_error("Fixture requires host-coherent buffer memory");
            VkMemoryAllocateFlagsInfo flags{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO};
            flags.flags = VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT;
            VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
            alloc.pNext = &flags;
            alloc.allocationSize = req.size;
            alloc.memoryTypeIndex = type;
            check(vkAllocateMemory(device, &alloc, nullptr, &b.memory));
            check(vkBindBufferMemory(device, b.handle, b.memory, 0));
            check(vkMapMemory(device, b.memory, 0, VK_WHOLE_SIZE, 0, &b.mapped));
            if (data) std::memcpy(b.mapped, data, bytes);
            VkBufferDeviceAddressInfo address{VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO};
            address.buffer = b.handle;
            b.address = vkGetBufferDeviceAddress(device, &address);
            buffers.push_back(b);
            return b;
        };
        VkCommandPoolCreateInfo pc{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        pc.queueFamilyIndex = family;
        pc.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        VkCommandPool pool;
        check(vkCreateCommandPool(device, &pc, nullptr, &pool));
        VkCommandBufferAllocateInfo ca{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        ca.commandPool = pool;
        ca.commandBufferCount = 1;
        ca.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        VkCommandBuffer cmd;
        check(vkAllocateCommandBuffers(device, &ca, &cmd));
        VkFenceCreateInfo fc{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        VkFence fence;
        check(vkCreateFence(device, &fc, nullptr, &fence));
        auto submit = [&](auto record) {
            check(vkResetCommandBuffer(cmd, 0));
            VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
            check(vkBeginCommandBuffer(cmd, &begin));
            record();
            check(vkEndCommandBuffer(cmd));
            VkSubmitInfo si{VK_STRUCTURE_TYPE_SUBMIT_INFO};
            si.commandBufferCount = 1;
            si.pCommandBuffers = &cmd;
            check(vkQueueSubmit(queue, 1, &si, fence));
            check(vkWaitForFences(device, 1, &fence, VK_TRUE, 10'000'000'000ull));
            check(vkResetFences(device, 1, &fence));
        };
        auto build = [&](VkAccelerationStructureTypeKHR type, std::vector<VkAccelerationStructureGeometryKHR> geoms,
                         std::vector<uint32_t> sizes) {
            VkAccelerationStructureBuildGeometryInfoKHR bi{
                VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR};
            bi.type = type;
            bi.mode = VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR;
            bi.geometryCount = uint32_t(geoms.size());
            bi.pGeometries = geoms.data();
            VkAccelerationStructureBuildSizesInfoKHR sz{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR};
            vkGetAccelerationStructureBuildSizesKHR(device, VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR, &bi,
                                                    sizes.data(), &sz);
            auto store = buffer(sz.accelerationStructureSize, VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR);
            auto scratch = buffer(sz.buildScratchSize + scratchAlignment - 1, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            AS as{};
            VkAccelerationStructureCreateInfoKHR ci{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR};
            ci.buffer = store.handle;
            ci.size = sz.accelerationStructureSize;
            ci.type = type;
            check(vkCreateAccelerationStructureKHR(device, &ci, nullptr, &as.handle));
            bi.dstAccelerationStructure = as.handle;
            bi.scratchData.deviceAddress = (scratch.address + scratchAlignment - 1) & ~(scratchAlignment - 1);
            std::vector<VkAccelerationStructureBuildRangeInfoKHR> ranges(geoms.size());
            for (size_t i = 0; i < ranges.size(); i++) ranges[i].primitiveCount = sizes[i];
            const auto *pr = ranges.data();
            submit([&] {
                VkMemoryBarrier host{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
                host.srcAccessMask = VK_ACCESS_HOST_WRITE_BIT;
                host.dstAccessMask = VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR;
                vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_HOST_BIT,
                                     VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR, 0, 1, &host, 0, nullptr, 0,
                                     nullptr);
                vkCmdBuildAccelerationStructuresKHR(cmd, 1, &bi, &pr);
                VkMemoryBarrier done{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
                done.srcAccessMask = VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR;
                done.dstAccessMask = VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR;
                vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
                                     VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                                         VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                                     0, 1, &done, 0, nullptr, 0, nullptr);
            });
            VkAccelerationStructureDeviceAddressInfoKHR addr{
                VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR};
            addr.accelerationStructure = as.handle;
            as.address = vkGetAccelerationStructureDeviceAddressKHR(device, &addr);
            structures.push_back(as);
            return as;
        };
        std::array<float, 18> vertices = {-1, -1, 0, 1, -1, 0, 0, 1, 0, 2, -1, 0, 4, -1, 0, 3, 1, 0};
        auto vb = buffer(sizeof(vertices), VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
                         vertices.data());
        std::vector<std::array<uint32_t, 4>> cases;
        std::vector<VkAccelerationStructureInstanceKHR> instances;
        for (auto model : std::array<std::array<uint32_t, 2>, 6>{
                 {{faces::back, 0},
                  {faces::front, 0},
                  {faces::back, faces::back},
                  {faces::back | faces::clockwise, 0},
                  {faces::back | faces::front, 0},
                  {faces::back | faces::mediumBoundary, faces::back | faces::mediumBoundary}}}) {
            std::vector<VkAccelerationStructureGeometryKHR> geoms;
            for (int g = 0; g < 2; g++) {
                VkAccelerationStructureGeometryKHR geom{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
                geom.geometryType = VK_GEOMETRY_TYPE_TRIANGLES_KHR;
                geom.flags = faces::ModelRules(model, bilateral).needsAnyHit(model[g]) ? 0 : VK_GEOMETRY_OPAQUE_BIT_KHR;
                auto &t = geom.geometry.triangles;
                t.sType = VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR;
                t.vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
                t.vertexData.deviceAddress = vb.address + g * 9 * sizeof(float);
                t.vertexStride = 12;
                t.maxVertex = 2;
                t.indexType = VK_INDEX_TYPE_NONE_KHR;
                geoms.push_back(geom);
            }
            auto blas = build(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR, geoms, {1, 1});
            for (int mirror = 0; mirror < 2; mirror++) {
                VkAccelerationStructureInstanceKHR i{};
                i.transform.matrix[0][0] = mirror ? -1 : 1;
                i.transform.matrix[1][1] = i.transform.matrix[2][2] = 1;
                i.transform.matrix[1][3] = float(cases.size()) * 4;
                i.mask = 1;
                i.flags = faces::ModelRules(model, bilateral).instanceFlags(i.transform);
                i.accelerationStructureReference = blas.address;
                instances.push_back(i);
                cases.push_back(
                    {model[0], model[1], uint32_t(mirror),
                     (bilateral ? 0x80000000u : 0u) | (queryPolicy ? 0x20000000u : 0u) | faces::uniform(model)});
            }
        }
        if (optics) {
            // An outward-wound closed glass slab. Camera queries retain their
            // production back-face flag; the material instance must expose exits.
            std::array<float, 18> slab = {-1, -1, 0, 1, -1, 0, 0, 1, 0, -1, -1, -1, 0, 1, -1, 1, -1, -1};
            auto sv =
                buffer(sizeof(slab), VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR, slab.data());
            std::array<uint32_t, 2> media{faces::back | faces::mediumBoundary, faces::back | faces::mediumBoundary};
            std::vector<VkAccelerationStructureGeometryKHR> geoms;
            for (int g = 0; g < 2; ++g) {
                VkAccelerationStructureGeometryKHR geom{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
                geom.geometryType = VK_GEOMETRY_TYPE_TRIANGLES_KHR;
                auto &t = geom.geometry.triangles;
                t.sType = VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR;
                t.vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
                t.vertexData.deviceAddress = sv.address + g * 9 * sizeof(float);
                t.vertexStride = 12;
                t.maxVertex = 2;
                t.indexType = VK_INDEX_TYPE_NONE_KHR;
                geoms.push_back(geom);
            }
            auto slabAs = build(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR, geoms, {1, 1});
            VkAccelerationStructureInstanceKHR instance{};
            instance.transform.matrix[0][0] = instance.transform.matrix[1][1] = instance.transform.matrix[2][2] = 1;
            instance.mask = 16;
            instance.flags = faces::ModelRules(media).instanceFlags(instance.transform);
            instance.accelerationStructureReference = slabAs.address;
            instances.push_back(instance);
        }
        if (bilateral)
            for (uint32_t source :
                 {faces::back, faces::front, 0u, faces::back | faces::front, faces::back | faces::clockwise}) {
                // Coincident reverse members carry independent attributes. Neither is deleted.
                std::array<float, 18> twins = {-1, -1, 0, 1, -1, 0, 0, 1, 0, 0, 1, 0, 1, -1, 0, -1, -1, 0};
                auto tv = buffer(sizeof(twins), VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
                                 twins.data());
                std::array<uint32_t, 2> model{source | faces::pairedGeometry, source | faces::pairedGeometry};
                std::vector<VkAccelerationStructureGeometryKHR> geoms;
                for (int g = 0; g < 2; ++g) {
                    VkAccelerationStructureGeometryKHR geom{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
                    geom.geometryType = VK_GEOMETRY_TYPE_TRIANGLES_KHR;
                    geom.flags = faces::ModelRules(model, true).needsAnyHit(model[g]) ? 0 : VK_GEOMETRY_OPAQUE_BIT_KHR;
                    auto &t = geom.geometry.triangles;
                    t.sType = VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR;
                    t.vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
                    t.vertexData.deviceAddress = tv.address + g * 9 * sizeof(float);
                    t.vertexStride = 12;
                    t.maxVertex = 2;
                    t.indexType = VK_INDEX_TYPE_NONE_KHR;
                    geoms.push_back(geom);
                }
                auto blas = build(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR, geoms, {1, 1});
                for (int mirror = 0; mirror < 2; ++mirror) {
                    VkAccelerationStructureInstanceKHR i{};
                    i.transform.matrix[0][0] = mirror ? -1 : 1;
                    i.transform.matrix[1][1] = i.transform.matrix[2][2] = 1;
                    i.transform.matrix[1][3] = float(cases.size()) * 4;
                    i.mask = 1;
                    i.flags = faces::ModelRules(model, true).instanceFlags(i.transform);
                    i.accelerationStructureReference = blas.address;
                    instances.push_back(i);
                    cases.push_back({model[0], model[1], uint32_t(mirror),
                                     0xc0000000u | (queryPolicy ? 0x20000000u : 0u) | faces::uniform(model)});
                }
            }
        // Contrasting opaque background on the far side of either viewing ray.
        // Separate masks keep the background in front of the opposite view out
        // of that query. Foreground model flags/opaque fast paths remain unchanged.
        for (int side = 0; side < 2; ++side) {
            const float z = (side == 0 ? -1.0f : 1.0f) * (queryPolicy ? 0.0002f : 1.0f);
            const float height = float(cases.size()) * 4 + 4;
            std::array<float, 18> bgVertices = {-6, -2, z, 6, -2,     z, 6,  height, z,
                                                -6, -2, z, 6, height, z, -6, height, z};
            auto bgBuffer =
                buffer(sizeof(bgVertices), VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
                       bgVertices.data());
            VkAccelerationStructureGeometryKHR geometry{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
            geometry.geometryType = VK_GEOMETRY_TYPE_TRIANGLES_KHR;
            geometry.flags = VK_GEOMETRY_OPAQUE_BIT_KHR;
            auto &triangles = geometry.geometry.triangles;
            triangles.sType = VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR;
            triangles.vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
            triangles.vertexData.deviceAddress = bgBuffer.address;
            triangles.vertexStride = 12;
            triangles.maxVertex = 5;
            triangles.indexType = VK_INDEX_TYPE_NONE_KHR;
            auto background = build(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR, {geometry}, {2});
            VkAccelerationStructureInstanceKHR instance{};
            instance.transform.matrix[0][0] = instance.transform.matrix[1][1] = instance.transform.matrix[2][2] = 1;
            instance.mask = side == 0 ? 2 : 4;
            instance.instanceCustomIndex = 1;
            instance.flags = VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR;
            instance.accelerationStructureReference = background.address;
            instances.push_back(instance);
        }
        // Real TLAS mask selection, not just a CPU mask assertion. Category
        // fixtures are spatially disjoint from the face/alpha fixtures above.
        std::array<float, 9> categoryVertices{-1, -1, 0, 1, -1, 0, 0, 1, 0};
        auto categoryBuffer =
            buffer(sizeof(categoryVertices), VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
                   categoryVertices.data());
        VkAccelerationStructureGeometryKHR categoryGeometry{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
        categoryGeometry.geometryType = VK_GEOMETRY_TYPE_TRIANGLES_KHR;
        categoryGeometry.flags = VK_GEOMETRY_OPAQUE_BIT_KHR;
        categoryGeometry.geometry.triangles.sType =
            VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR;
        categoryGeometry.geometry.triangles.vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
        categoryGeometry.geometry.triangles.vertexData.deviceAddress = categoryBuffer.address;
        categoryGeometry.geometry.triangles.vertexStride = 12;
        categoryGeometry.geometry.triangles.maxVertex = 2;
        categoryGeometry.geometry.triangles.indexType = VK_INDEX_TYPE_NONE_KHR;
        auto categoryBlas = build(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR, {categoryGeometry}, {1});
        for (uint32_t category = 0; category < 3; ++category) {
            VkAccelerationStructureInstanceKHR value{};
            value.transform.matrix[0][0] = value.transform.matrix[1][1] = value.transform.matrix[2][2] = 1;
            value.transform.matrix[0][3] = 20.0f + 10.0f * category;
            value.mask = category == 0 ? 2u : (category == 1 ? 8u : 4u);
            value.flags = VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR;
            value.instanceCustomIndex = 2u + category;
            value.accelerationStructureReference = categoryBlas.address;
            instances.push_back(value);
        }
        if (continuation) {
            auto plane = [&](float x, float z, uint32_t mask, uint32_t owner) {
                VkAccelerationStructureInstanceKHR value{};
                value.transform.matrix[0][0] = value.transform.matrix[1][1] = value.transform.matrix[2][2] = 1;
                value.transform.matrix[0][3] = x;
                value.transform.matrix[2][3] = z;
                value.mask = mask;
                value.instanceCustomIndex = owner;
                value.flags = VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR;
                value.accelerationStructureReference = categoryBlas.address;
                instances.push_back(value);
            };
            // The overlay at x=40 is already present. Its transmitted path crosses
            // two world interfaces before SELF and the visible world background.
            plane(40, -.2f, 1, 5);
            plane(40, -.4f, 1, 5);
            plane(40, -.6f, 2, 2);
            plane(40, -1, 1, 6);
            // Additive thin layers continue the same query without consuming its
            // scattering budget. All 48 layers must be crossed even at 16 bounces.
            plane(80, 0, 4, 4);
            for (int layer = 1; layer <= 48; ++layer) plane(80, -.02f * layer, 1, 7);
            plane(80, -1, 2, 2);
            plane(80, -1.1f, 1, 6);
            // Both sides of the split surface: reflection must include SELF,
            // while the independently initialized camera refraction must not.
            for (float side : {-1.0f, 1.0f}) {
                plane(60, .5f * side, 2, 2);
                plane(60, side, 1, 6);
            }
        }
        auto ib = buffer(instances.size() * sizeof(instances[0]),
                         VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR, instances.data());
        VkAccelerationStructureGeometryKHR ig{VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
        ig.geometryType = VK_GEOMETRY_TYPE_INSTANCES_KHR;
        ig.geometry.instances.sType = VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR;
        ig.geometry.instances.data.deviceAddress = ib.address;
        auto tlas = build(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR, {ig}, {uint32_t(instances.size())});
        auto out = buffer(cases.size() * 4 * sizeof(uint32_t), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        auto input = buffer(cases.size() * sizeof(cases[0]), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, cases.data());
        // A narrow real source-derived exclusion removes only the foreground plane;
        // the distinct interface 0.0002 units away must still be queried.
        const std::array<glm::vec3, 8> clipPositions{
            {{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}}};
        const std::array<uint32_t, 36> clipIndices{{0, 1, 2, 2, 3, 0, 4, 7, 6, 6, 5, 4, 0, 4, 5, 5, 1, 0,
                                                    3, 2, 6, 6, 7, 3, 0, 3, 7, 7, 4, 0, 1, 5, 6, 6, 2, 1}};
        std::vector<glm::vec4> clipRecords;
        water::appendVolume(clipRecords, water::clipPlanes(clipPositions, clipIndices),
                            glm::scale(glm::mat4(1), glm::vec3(1000, 1000, 0.00005f)));
        auto boatPositions = clipPositions;
        for (auto &p : boatPositions) {
            p.x *= .875f;
            p.z *= .5f;
            p.y = p.y < 0 ? .1875f : .5625f;
        }
        water::appendVolume(clipRecords, water::clipPlanes(boatPositions, clipIndices),
                            glm::translate(glm::mat4(1), glm::vec3(2000, 0, 0)));
        auto clip =
            buffer(clipRecords.size() * sizeof(glm::vec4), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, clipRecords.data());
        std::array<VkDescriptorSetLayoutBinding, 4> bindings{
            {{0, VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR, 1, VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
             {1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
             {2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
             {3, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT, nullptr}}};
        VkDescriptorSetLayoutCreateInfo li{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        li.bindingCount = 4;
        li.pBindings = bindings.data();
        VkDescriptorSetLayout layout;
        check(vkCreateDescriptorSetLayout(device, &li, nullptr, &layout));
        std::array<VkDescriptorPoolSize, 2> ps{
            {{VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR, 1}, {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 3}}};
        VkDescriptorPoolCreateInfo dpi{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        dpi.maxSets = 1;
        dpi.poolSizeCount = 2;
        dpi.pPoolSizes = ps.data();
        VkDescriptorPool dp;
        check(vkCreateDescriptorPool(device, &dpi, nullptr, &dp));
        VkDescriptorSetAllocateInfo da{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        da.descriptorPool = dp;
        da.descriptorSetCount = 1;
        da.pSetLayouts = &layout;
        VkDescriptorSet ds;
        check(vkAllocateDescriptorSets(device, &da, &ds));
        VkWriteDescriptorSetAccelerationStructureKHR aw{
            VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR};
        aw.accelerationStructureCount = 1;
        aw.pAccelerationStructures = &tlas.handle;
        std::array<VkDescriptorBufferInfo, 3> bis{
            {{out.handle, 0, VK_WHOLE_SIZE}, {input.handle, 0, VK_WHOLE_SIZE}, {clip.handle, 0, VK_WHOLE_SIZE}}};
        std::array<VkWriteDescriptorSet, 4> ws{};
        for (int i = 0; i < 4; i++) {
            ws[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            ws[i].dstSet = ds;
            ws[i].dstBinding = i;
            ws[i].descriptorCount = 1;
            ws[i].descriptorType = bindings[i].descriptorType;
            if (i)
                ws[i].pBufferInfo = &bis[i - 1];
            else
                ws[i].pNext = &aw;
        }
        vkUpdateDescriptorSets(device, 4, ws.data(), 0, nullptr);
        std::ifstream file(argv[1], std::ios::binary | std::ios::ate);
        if (!file) throw std::runtime_error("Missing shader");
        size_t bytes = size_t(file.tellg());
        file.seekg(0);
        std::vector<uint32_t> words(bytes / 4);
        file.read((char *)words.data(), bytes);
        VkShaderModuleCreateInfo smi{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        smi.codeSize = bytes;
        smi.pCode = words.data();
        VkShaderModule sm;
        check(vkCreateShaderModule(device, &smi, nullptr, &sm));
        VkPipelineLayoutCreateInfo pli{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        pli.setLayoutCount = 1;
        pli.pSetLayouts = &layout;
        VkPipelineLayout pl;
        check(vkCreatePipelineLayout(device, &pli, nullptr, &pl));
        VkComputePipelineCreateInfo pci{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
        pci.layout = pl;
        pci.stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                     nullptr,
                     0,
                     VK_SHADER_STAGE_COMPUTE_BIT,
                     sm,
                     "main",
                     nullptr};
        VkPipeline pipeline;
        check(vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &pci, nullptr, &pipeline));
        submit([&] {
            vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pl, 0, 1, &ds, 0, nullptr);
            vkCmdDispatch(cmd, uint32_t(cases.size()) * 4, 1, 1);
            VkMemoryBarrier b{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
            b.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
            b.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &b, 0,
                                 nullptr, 0, nullptr);
        });
        bool passed = true;
        if (coverage) {
            for (size_t i = 0; i < cases.size() * 4; ++i) {
                const float a = float((i / 4) % 10) / 9.0f;
                const auto red = uint32_t(std::lround(a * 255));
                const auto blue = uint32_t(std::lround((1 - a) * 255));
                const auto actual = static_cast<uint32_t *>(out.mapped)[i];
                if (actual != (red | (blue << 16))) {
                    std::cerr << "Coverage scene case=" << i << " actual=" << actual
                              << " expected=" << (red | (blue << 16)) << "\n";
                    passed = false;
                }
            }
        }
        if (continuation) {
            for (size_t i = 0; i < cases.size() * 4; ++i)
                if (static_cast<uint32_t *>(out.mapped)[i] != 1) {
                    std::cerr << "Production continuation case=" << i << " failed\n";
                    passed = false;
                }
        }
        for (size_t c = 0; !coverage && !continuation && c < cases.size(); c++)
            for (int g = 0; g < 2; g++)
                for (int side = 0; side < 2; side++) {
                    bool expected = (bilateral && !queryPolicy) || (cases[c][g] & faces::mediumBoundary) != 0u ||
                                    faces::accepts(cases[c][g], (side == 0) != (cases[c][2] != 0));
                    if (queryPolicy && (cases[c][3] & 0x40000000u) != 0)
                        expected = faces::effective(cases[c][0]) != (faces::back | faces::front);
                    uint32_t actual = ((uint32_t *)out.mapped)[c * 4 + g * 2 + side];
                    bool reverse = bilateral || expected;
                    bool endpoint = bilateral || expected;
                    uint32_t wanted = uint32_t(expected) | 58u | (reverse ? 64u : 0u) | (endpoint ? 128u : 0u) |
                                      (expected ? 256u : 512u);
                    if ((cases[c][3] & 0x40000000u) != 0 && expected) {
                        uint32_t face = cases[c][0];
                        if (faces::effective(face) == 0 || faces::effective(face) == (faces::back | faces::front))
                            face = (face & ~(faces::back | faces::front)) | faces::back;
                        const uint32_t owner = faces::accepts(face, (side == 0) != (cases[c][2] != 0)) ? 0 : 1;
                        wanted |= (1024u << owner) | 4096u; // exactly one interface, including NO_CULL twins
                    }
                    if (queryPolicy) wanted |= (1u << 24) | (1u << 25) | (1u << 26);
                    if (actual != wanted) {
                        std::cerr << "case=" << c << " geometry=" << g << " side=" << side << " expected=" << expected
                                  << " wanted=" << wanted << " actual=" << actual << '\n';
                        passed = false;
                    }
                }
        vkDestroyPipeline(device, pipeline, nullptr);
        vkDestroyPipelineLayout(device, pl, nullptr);
        vkDestroyShaderModule(device, sm, nullptr);
        vkDestroyDescriptorPool(device, dp, nullptr);
        vkDestroyDescriptorSetLayout(device, layout, nullptr);
        for (auto as : structures) vkDestroyAccelerationStructureKHR(device, as.handle, nullptr);
        for (auto b : buffers) {
            vkUnmapMemory(device, b.memory);
            vkDestroyBuffer(device, b.handle, nullptr);
            vkFreeMemory(device, b.memory, nullptr);
        }
        vkDestroyFence(device, fence, nullptr);
        vkDestroyCommandPool(device, pool, nullptr);
        vkDestroyDevice(device, nullptr);
        vkDestroyInstance(instance, nullptr);
        if (!passed) return 1;
        if (coverage) {
            std::cout
                << "GPU coverage scene: real two-sided/mirrored TLAS rays, opaque endpoints and straight background, submit/fence/readback PASS\n";
            return 0;
        }
        if (continuation) {
            std::cout
                << (optics ?
                        "GPU production medium stack: nested IOR entry/exit, Beer absorption, Snell direction, Fresnel/TIR, receiver scope, and projected depth/strength mask PASS\n" :
                        "Production priority loop and extracted Vanilla split dispatch: real TLAS queries, coverage, repeated refraction, irreversible reflection/diffuse, sibling payload isolation and third-person ownership passed\n");
            return 0;
        }
        std::cout
            << (queryPolicy ?
                    "Query-policy GPU: direct/refraction versus bilateral transport, exact twins and close legal interfaces; " :
                bilateral ?
                    "320 bilateral GPU rays: unpaired opacity, exact twin member ownership and single interface; " :
                    "160 source GPU rays: ")
            << " source selection, ray-facing light visibility, radiance endpoints and red-foreground/blue-background opacity; mixed opaque/double-sided, uniform hardware, front/back/both, clockwise, mirrors; UI owner rules passed\n";
        return 0;
    } catch (const std::exception &e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
