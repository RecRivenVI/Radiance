#include "core/render/pipeline.hpp"
#include "core/render/modules/world/tone_mapping/tone_mapping_module.hpp"
#include "core/diagnostics/ponder_capture.hpp"
#include "core/render/buffers.hpp"
#include "core/render/display_output.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"
#include "core/render/display_profile.hpp"
#include "core/vulkan/window.hpp"
#include <stdexcept>

std::shared_ptr<vk::DeviceLocalImage> DisplayOutput::encode(Framework &f,
                                                            const std::shared_ptr<vk::CommandBuffer> &cmd,
                                                            uint32_t frame,
                                                            uint32_t role,
                                                            const std::shared_ptr<vk::DeviceLocalImage> &source) {
    if (!source || role > 1) throw std::invalid_argument("Invalid display output source");
    const auto &profile = f.swapchain()->displayProfile();
    if (frames_.empty()) frames_.resize(f.swapchain()->imageCount());
    auto &slot = frames_.at(frame).at(role);
    if (!slot.image) {
        if (!pass_) {
            pass_ = vk::RenderPassBuilder{}
                        .beginAttachmentDescription()
                        .defineAttachmentDescription({.format = profile.surface.format,
                                                      .samples = VK_SAMPLE_COUNT_1_BIT,
                                                      .loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                                                      .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
                                                      .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE,
                                                      .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
                                                      .initialLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                                                      .finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL})
                        .endAttachmentDescription()
                        .beginAttachmentReference()
                        .defineAttachmentReference({0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL})
                        .endAttachmentReference()
                        .beginSubpassDescription()
                        .defineSubpassDescription(
                            {.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS, .colorAttachmentIndices = {0}})
                        .endSubpassDescription()
                        .build(f.device());
            sampler_ = vk::Sampler::create(f.device(), VK_FILTER_LINEAR, VK_SAMPLER_MIPMAP_MODE_NEAREST,
                                           VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        }
        slot.image = vk::DeviceLocalImage::create(
            f.device(), f.vma(), false, source->width(), source->height(), 1, profile.surface.format,
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        slot.framebuffer = vk::FramebufferBuilder{}
                               .beginAttachment()
                               .defineAttachment(slot.image)
                               .endAttachment()
                               .build(f.device(), pass_);
        slot.table = vk::DescriptorTableBuilder{}
                         .beginDescriptorLayoutSet()
                         .beginDescriptorLayoutSetBinding()
                         .defineDescriptorLayoutSetBinding(
                             {0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_FRAGMENT_BIT})
                         .endDescriptorLayoutSetBinding()
                         .endDescriptorLayoutSet()
                         .definePushConstant({VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(glm::vec4)})
                         .build(f.device());
        if (!pipeline_) {
            const auto path = Renderer::folderPath / "shaders/world/tone_mapping";
            auto vert = vk::Shader::create(f.device(), (path / "tone_mapping_vert.spv").string());
            auto frag = vk::Shader::create(f.device(), (path / "display_frag.spv").string());
            pipeline_ = vk::GraphicsPipelineBuilder{}
                            .defineRenderPass(pass_, 0)
                            .beginShaderStage()
                            .defineShaderStage(vert, VK_SHADER_STAGE_VERTEX_BIT)
                            .defineShaderStage(frag, VK_SHADER_STAGE_FRAGMENT_BIT)
                            .endShaderStage()
                            .defineVertexInputState<void>()
                            .defineViewportScissorState(
                                {.viewport = {0, 0, float(source->width()), float(source->height()), 0, 1},
                                 .scissor = {{0, 0}, {source->width(), source->height()}}})
                            .defineDepthStencilState({.depthTestEnable = VK_FALSE,
                                                      .depthWriteEnable = VK_FALSE,
                                                      .depthCompareOp = VK_COMPARE_OP_ALWAYS})
                            .beginColorBlendAttachmentState()
                            .defineDefaultColorBlendAttachmentState()
                            .endColorBlendAttachmentState()
                            .definePipelineLayout(slot.table)
                            .build(f.device());
        }
    }
    slot.table->bindSamplerImage(sampler_, source, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 0, 0, 0);
    auto transition = [&](auto image, VkImageLayout next) {
        cmd->barriersBufferImage({}, {{.srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                       .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                       .dstStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                       .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                                       .oldLayout = image->imageLayout(),
                                       .newLayout = next,
                                       .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                       .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                                       .image = image,
                                       .subresourceRange = vk::wholeColorSubresourceRange}});
        image->imageLayout() = next;
    };
    transition(source, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    transition(slot.image, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
    const bool srgbAttachment =
        profile.surface.format == VK_FORMAT_R8G8B8A8_SRGB || profile.surface.format == VK_FORMAT_B8G8R8A8_SRGB;
    const glm::vec4 pc(float(profile.encoding), profile.uiWhite, profile.peakNits, srgbAttachment ? 1.f : 0.f);
    cmd->beginRenderPass({.renderPass = pass_,
                          .framebuffer = slot.framebuffer,
                          .renderAreaExtent = {source->width(), source->height()}});
    cmd->bindGraphicsPipeline(pipeline_)->bindDescriptorTable(slot.table, VK_PIPELINE_BIND_POINT_GRAPHICS);
    vkCmdPushConstants(cmd->vkCommandBuffer(), slot.table->vkPipelineLayout(), VK_SHADER_STAGE_FRAGMENT_BIT, 0,
                       sizeof(pc), &pc);
    cmd->draw(3, 1)->endRenderPass();
    if (role == 0) {
        if (auto capture = mcvr::diagnostics::PonderCapture::begin(f.shared_from_this(), Renderer::instance().buffers(),
                                                                   "DISPLAY")) {
            capture->copy(f.shared_from_this(), cmd, "display-canvas", source);
            capture->copy(f.shared_from_this(), cmd, "display-encoded", slot.image);
            auto context = f.pipeline()->acquirePipelineContext(f.contexts()[frame]);
            if (context->worldPipelineContext)
                for (auto &module : context->worldPipelineContext->worldModuleContexts)
                    if (auto tone = std::dynamic_pointer_cast<ToneMappingModuleContext>(module)) {
                        capture->copy(f.shared_from_this(), cmd, "world-hdr", tone->hdrImage);
                        capture->copyBuffer(f.shared_from_this(), cmd, "exposure", tone->exposureBuffer);
                        std::ofstream(capture->directory / "tone-state.txt")
                            << "auto=" << tone->lastParameters.autoExposure
                            << "\nmanual=" << tone->lastParameters.manualExposure
                            << "\nbias=" << tone->lastParameters.exposureBias
                            << "\nbloom=" << tone->lastParameters.padding2 << "\n";
                    }

            std::ofstream(capture->directory / "display.txt")
                << "encoding=" << uint32_t(profile.encoding) << "\nformat=" << profile.surface.format
                << "\ncolorSpace=" << profile.surface.colorSpace << "\nreferenceWhite=" << profile.referenceWhite
                << "\nuiWhite=" << profile.uiWhite << "\npeakNits=" << profile.peakNits
                << "\nuiWhiteAutomatic=" << (Renderer::options.uiWhiteNits <= 0 ? 1 : 0)
                << "\nsystemWhite=" << mcvr::display::monitor(f.window()->window()).sdrWhiteNits
                << "\nvisualAcceptance=pending-real-display\n";
            capture->sealCommands(cmd);
        }
    }
    return slot.image;
}
