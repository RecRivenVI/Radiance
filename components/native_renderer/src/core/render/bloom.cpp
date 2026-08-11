#include "core/render/bloom.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"

std::shared_ptr<vk::DeviceLocalImage> Bloom::record(Framework &f,
                                                    const std::shared_ptr<vk::CommandBuffer> &cmd,
                                                    uint32_t slot,
                                                    const std::shared_ptr<vk::DeviceLocalImage> &hdr,
                                                    const std::shared_ptr<vk::DeviceLocalBuffer> &exposure,
                                                    glm::vec4 parameters) {
    if (frames_.size() <= slot) frames_.resize(slot + 1);
    auto &levels = frames_[slot];
    if (!sampler_)
        sampler_ = vk::Sampler::create(f.device(), VK_FILTER_LINEAR, VK_SAMPLER_MIPMAP_MODE_NEAREST,
                                       VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
    if (!levels[0].down) {
        uint32_t width = hdr->width(), height = hdr->height();
        for (auto &level : levels) {
            width = std::max(1u, (width + 1) / 2);
            height = std::max(1u, (height + 1) / 2);
            for (auto target : {&level.down, &level.up})
                *target = vk::DeviceLocalImage::create(f.device(), f.vma(), false, width, height, 1,
                                                       VK_FORMAT_R16G16B16A16_SFLOAT,
                                                       VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
            for (auto table : {&level.downTable, &level.upTable}) {
                *table = vk::DescriptorTableBuilder{}
                             .beginDescriptorLayoutSet()
                             .beginDescriptorLayoutSetBinding()
                             .defineDescriptorLayoutSetBinding(
                                 {0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                             .defineDescriptorLayoutSetBinding(
                                 {1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                             .defineDescriptorLayoutSetBinding(
                                 {2, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                             .defineDescriptorLayoutSetBinding(
                                 {3, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT})
                             .endDescriptorLayoutSetBinding()
                             .endDescriptorLayoutSet()
                             .definePushConstant({VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(glm::vec4)})
                             .build(f.device());
                (*table)->bindBuffer(exposure, 0, 3);
            }
        }
        if (!pipeline_) {
            auto shader = vk::Shader::create(
                f.device(), (Renderer::folderPath / "shaders/world/tone_mapping/bloom_comp.spv").string());
            pipeline_ = vk::ComputePipelineBuilder{}
                            .defineShader(shader)
                            .definePipelineLayout(levels[0].downTable)
                            .build(f.device());
        }
    }
    auto transition = [&](auto image, VkImageLayout next) {
        cmd->barriersBufferImage(
            {}, {{.srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                  .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                  .dstStageMask = VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                  .dstAccessMask = VK_ACCESS_2_SHADER_READ_BIT | VK_ACCESS_2_SHADER_WRITE_BIT,
                  .oldLayout = image->imageLayout(),
                  .newLayout = next,
                  .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                  .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                  .image = image,
                  .subresourceRange = vk::wholeColorSubresourceRange}});
        image->imageLayout() = next;
    };
    auto dispatch = [&](auto table, auto source, auto fine, auto output, float phase) {
        table->bindSamplerImage(sampler_, source, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 0, 0, 0);
        table->bindSamplerImage(sampler_, fine, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 0, 1, 0);
        table->bindImage(output, VK_IMAGE_LAYOUT_GENERAL, 0, 2);
        transition(output, VK_IMAGE_LAYOUT_GENERAL);
        parameters.x = phase;
        cmd->bindDescriptorTable(table, VK_PIPELINE_BIND_POINT_COMPUTE)->bindComputePipeline(pipeline_);
        vkCmdPushConstants(cmd->vkCommandBuffer(), table->vkPipelineLayout(), VK_SHADER_STAGE_COMPUTE_BIT, 0,
                           sizeof(parameters), &parameters);
        vkCmdDispatch(cmd->vkCommandBuffer(), (output->width() + 7) / 8, (output->height() + 7) / 8, 1);
        transition(output, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    };
    auto source = hdr;
    for (size_t i = 0; i < levels.size(); ++i) {
        dispatch(levels[i].downTable, source, source, levels[i].down, i == 0 ? 1.f : 0.f);
        source = levels[i].down;
    }
    for (int i = int(levels.size()) - 2; i >= 0; --i) {
        dispatch(levels[i].upTable, source, levels[i].down, levels[i].up, 2.f);
        source = levels[i].up;
    }
    return source;
}
