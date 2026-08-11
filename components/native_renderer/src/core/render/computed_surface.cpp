#include "computed_surface.hpp"
#include "render_framework.hpp"
#include "core/diagnostics/frame_profile.hpp"
#include "core/diagnostics/computed_surface_audit.hpp"
#include <spirv_reflect.h>
#include <array>
#include <stdexcept>

namespace {
std::shared_ptr<vk::DescriptorTable> layout(Framework &f) {
    vk::DescriptorTableBuilder builder;
    auto &bindings = builder.beginDescriptorLayoutSet().beginDescriptorLayoutSetBinding();
    for (uint32_t i = 0; i < 8; ++i)
        bindings.defineDescriptorLayoutSetBinding({i,
                                                   i == 6 ? VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER :
                                                   i == 7 ? VK_DESCRIPTOR_TYPE_STORAGE_IMAGE :
                                                            VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                                                   1, VK_SHADER_STAGE_COMPUTE_BIT});
    return bindings.endDescriptorLayoutSetBinding()
        .endDescriptorLayoutSet()
        .definePushConstant({VK_SHADER_STAGE_COMPUTE_BIT, 0, 16})
        .build(f.device());
}
void validate(std::span<const uint32_t> words) {
    if (words.size() < 5 || words.size_bytes() > 1024 * 1024) throw std::invalid_argument("Surface SPIR-V size");
    SpvReflectShaderModule module{};
    if (spvReflectCreateShaderModule(words.size_bytes(), words.data(), &module) != SPV_REFLECT_RESULT_SUCCESS)
        throw std::invalid_argument("Invalid surface SPIR-V");
    struct Cleanup {
        SpvReflectShaderModule *m;
        ~Cleanup() {
            spvReflectDestroyShaderModule(m);
        }
    } cleanup{&module};
    if (module.entry_point_count != 1 || module.shader_stage != SPV_REFLECT_SHADER_STAGE_COMPUTE_BIT ||
        std::string_view(module.entry_point_name) != "main" || module.descriptor_binding_count != 8 ||
        module.entry_points[0].local_size.x != 64 || module.entry_points[0].local_size.y != 1 ||
        module.entry_points[0].local_size.z != 1 || module.push_constant_block_count != 1 ||
        module.push_constant_blocks[0].offset != 0 || module.push_constant_blocks[0].size != 16)
        throw std::invalid_argument("Surface compute entry point/ABI mismatch");
    std::array<bool, 8> found{};
    for (uint32_t i = 0; i < module.descriptor_binding_count; ++i) {
        const auto &b = module.descriptor_bindings[i];
        if (b.set != 0 || b.binding >= 8 || found[b.binding] || b.count != 1 ||
            b.descriptor_type != (b.binding == 6 ? SPV_REFLECT_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER :
                                  b.binding == 7 ? SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_IMAGE :
                                                   SPV_REFLECT_DESCRIPTOR_TYPE_STORAGE_BUFFER))
            throw std::invalid_argument("Surface descriptor ABI mismatch");
        found[b.binding] = true;
    }
    for (uint32_t i = 0; i < module.capability_count; ++i) {
        const auto c = module.capabilities[i].value;
        if (c != SpvCapabilityShader && c != SpvCapabilityMatrix && c != SpvCapabilityImageQuery)
            throw std::invalid_argument("Unsupported surface SPIR-V capability");
    }
}
} // namespace

std::shared_ptr<ComputedSurfaceResource> makeSurfaceProgram(Framework &f, std::span<const uint32_t> spirv) {
    validate(spirv);
    auto resource = std::make_shared<ComputedSurfaceResource>();
    resource->layout = layout(f);
    auto shader = vk::Shader::create(
        f.device(),
        vk::Shader::CompileResult{"source-owned-surface", VK_SHADER_STAGE_COMPUTE_BIT, {spirv.begin(), spirv.end()}});
    resource->pipeline =
        vk::ComputePipelineBuilder{}.defineShader(shader).definePipelineLayout(resource->layout).build(f.device());
    return resource;
}

std::shared_ptr<ComputedSurfaceResource> makeSurfaceData(Framework &f, const void *bytes, size_t size) {
    if (!bytes || size == 0 || size % 4 || size > f.physicalDevice()->properties().limits.maxStorageBufferRange)
        throw std::invalid_argument("Surface input data range");
    auto resource = std::make_shared<ComputedSurfaceResource>();
    resource->data =
        vk::DeviceLocalBuffer::create(f.vma(), f.device(), false, size, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    resource->data->uploadToStagingBuffer(const_cast<void *>(bytes));
    if (mcvr::audit::computedSurfacesEnabled()) {
        const auto *start = static_cast<const std::byte *>(bytes);
        resource->auditBytes.assign(start, start + size);
    }
    return resource;
}

void ComputedSurfaceDispatch::bind(Framework &f,
                                   const std::shared_ptr<vk::DeviceLocalBuffer> &positions,
                                   const std::shared_ptr<vk::DeviceLocalBuffer> &materials,
                                   const std::shared_ptr<vk::DeviceLocalBuffer> &indexBuffer,
                                   uint32_t firstVertex,
                                   uint32_t firstIndex) {
    vertexOffset = firstVertex;
    indexOffset = firstIndex;
    outputs = {positions, materials, indexBuffer};
    table = layout(f);
    table->bindBuffer(source->data, 0, 0)
        ->bindBuffer(parameters, 0, 1)
        ->bindBuffer(positions ? positions : source->data, 0, 2)
        ->bindBuffer(materials ? materials : source->data, 0, 3)
        ->bindBuffer(indexBuffer ? indexBuffer : source->data, 0, 4)
        ->bindBuffer(contacts->data, 0, 5)
        ->bindSamplerImageForShader(sampler, noise, 0, 6, 0)
        ->bindImage(field, VK_IMAGE_LAYOUT_GENERAL, 0, 7);
}

void ComputedSurfaceDispatch::record(Framework &f, const std::shared_ptr<vk::CommandBuffer> &commands) {
    if (recorded) return;
    f.frameResourceRetainer().retain(shared_from_this());
    auto cmd = commands->vkCommandBuffer();
    auto stamp = f.safeAcquireCurrentContext()->auditGpu.begin(cmd, "surface-compute");
    VkImageMemoryBarrier image{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    image.srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    image.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    image.oldLayout = field->imageLayout();
    image.newLayout = VK_IMAGE_LAYOUT_GENERAL;
    image.srcQueueFamilyIndex = image.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    image.image = field->vkImage();
    image.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr,
                         0, nullptr, 1, &image);
    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, program->pipeline->vkPipeline());
    auto set = table->descriptorSet()[0];
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, table->vkPipelineLayout(), 0, 1, &set, 0, nullptr);
    const uint32_t limit = f.physicalDevice()->properties().limits.maxComputeWorkGroupCount[0];
    for (uint64_t first = 0; first < work;) {
        uint32_t groups = uint32_t(std::min<uint64_t>((work - first + 63) / 64, limit));
        uint32_t push[4]{uint32_t(first), vertexOffset, indexOffset, work};
        vkCmdPushConstants(cmd, table->vkPipelineLayout(), VK_SHADER_STAGE_COMPUTE_BIT, 0, 16, push);
        vkCmdDispatch(cmd, groups, 1, 1);
        first += uint64_t(groups) * 64;
    }
    image.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    image.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    image.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
    image.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    VkMemoryBarrier memory{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
    memory.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    memory.dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR;
    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                         VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR |
                             VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR,
                         0, 1, &memory, 0, nullptr, 1, &image);
    field->imageLayout() = image.newLayout;
    mcvr::audit::computedSurface(f, commands, *this);
    f.safeAcquireCurrentContext()->auditGpu.end(cmd, stamp);
    recorded = true;
}
