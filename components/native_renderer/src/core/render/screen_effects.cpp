#include "core/render/modules/ui_module.hpp"
#include "core/render/buffers.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"
#include "core/render/effect_spirv.hpp"

namespace {
std::shared_ptr<vk::DescriptorTable> effectTable(const std::shared_ptr<vk::Device> &device) {
    auto builder = vk::DescriptorTableBuilder{};
    auto &images = builder.beginDescriptorLayoutSet().beginDescriptorLayoutSetBinding();
    for (uint32_t binding : {1u, 2u, 4u, 5u})
        images.defineDescriptorLayoutSetBinding({.binding = binding,
                                                 .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                                                 .descriptorCount = 1,
                                                 .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT});
    images.endDescriptorLayoutSetBinding().endDescriptorLayoutSet();
    builder.beginDescriptorLayoutSet()
        .beginDescriptorLayoutSetBinding()
        .defineDescriptorLayoutSetBinding({.binding = 1,
                                           .descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC,
                                           .descriptorCount = 1,
                                           .stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT})
        .endDescriptorLayoutSetBinding()
        .endDescriptorLayoutSet();
    return builder.definePushConstant({VK_SHADER_STAGE_FRAGMENT_BIT, 0, 16}).build(device);
}
std::vector<uint32_t> builtinInputs(uint32_t program) {
    switch (program) {
        case BLUR:
        case CREEPER:
        case INVERT:
        case SPIDER_BLUR_MAIN_H15:
        case SPIDER_BLUR_MAIN_H7: return {1};
        case SPIDER_BLUR_TEMP_V15:
        case SPIDER_BLUR_TEMP_V7: return {5};
        case SPIDER: return {1, 2, 4};
        default: throw std::invalid_argument("Unknown built-in screen program");
    }
}
} // namespace

uint32_t UIModule::registerEffectProgram(std::string key, std::vector<uint32_t> spirv, uint32_t inputs) {
    mcvr::effects::validateSpirv(spirv, inputs);
    if (key.empty() || effectPrograms_.size() >= 256 || nextEffectProgram_ == UINT32_MAX)
        throw std::invalid_argument("Compiled screen program registration budget");
    auto framework = framework_.lock();
    auto program = std::make_shared<EffectProgramRecord>();
    program->key = std::move(key);
    program->spirv = std::move(spirv);
    program->shaders.vertexShader = overlayPostPipelineShaders_.at(CREEPER).vertexShader;
    program->shaders.fragmentShader =
        vk::Shader::create(framework->device(), vk::Shader::CompileResult{.sourcePath = program->key,
                                                                          .stage = VK_SHADER_STAGE_FRAGMENT_BIT,
                                                                          .spirv = program->spirv});
    const uint32_t bindings[]{1, 2, 4};
    program->inputBindings.assign(bindings, bindings + inputs);
    const auto id = nextEffectProgram_++;
    effectPrograms_.emplace(id, std::move(program));
    return id;
}
void UIModule::releaseEffectProgram(uint32_t id) {
    auto it = effectPrograms_.find(id);
    if (it == effectPrograms_.end()) throw std::invalid_argument("Screen program is not owned");
    auto framework = framework_.lock();
    framework->frameResourceRetainer().retain(it->second);
    effectPrograms_.erase(it);
    for (auto p = effectPipelines_.begin(); p != effectPipelines_.end();) {
        if (std::get<0>(p->first) == id) {
            framework->frameResourceRetainer().retain(p->second);
            p = effectPipelines_.erase(p);
        } else
            ++p;
    }
}

void UIModuleContext::executeEffects(const mcvr::effects::Plan &plan) {
    using namespace mcvr::effects;
    validate(plan);
    auto context = frameworkContext.lock();
    auto framework = context->framework.lock();
    auto module = uiModule.lock();
    if (!framework->isRunning()) return;
    // Resolve every program before ending the current pass or mutating GUI policy.
    for (const auto &pass : plan.passes) {
        const auto inputs = pass.program < MAX_OVERLAY_POST_PIPELINE_TYPE ?
                                builtinInputs(pass.program) :
                                module->effectPrograms_.at(pass.program)->inputBindings;
        if (inputs.size() != pass.inputs.size() || (pass.program == BLUR && pass.filter != Filter::Linear))
            throw std::invalid_argument("Effect program input/sampling contract mismatch");
    }
    end();
    if (plan.fullScreenBlur) context->fgGui.fullScreenBlur = true;
    std::vector<EffectScratchImage> targets{{overlayDrawColorImage, {}}};
    if (effectScratch.size() < plan.targets.size()) effectScratch.resize(plan.targets.size());
    const auto extent = framework->swapchain()->vkExtent();
    for (size_t i = 0; i < plan.targets.size(); ++i) {
        const auto size = plan.targets[i];
        const uint32_t w = std::max(1u, uint32_t((uint64_t(extent.width) + size.divisorX - 1) / size.divisorX));
        const uint32_t h = std::max(1u, uint32_t((uint64_t(extent.height) + size.divisorY - 1) / size.divisorY));
        if (w == extent.width && h == extent.height && i < 3) {
            if (i == 0) targets.push_back({overlayPostColorImage, overlayPostFramebuffer});
            if (i == 1) targets.push_back({diagramDrawColorImage, spiderLargeBlurFramebuffer});
            if (i == 2) targets.push_back({spiderSmallBlurImage, spiderSmallBlurFramebuffer});
        } else {
            auto &scratch = effectScratch[i];
            if (!scratch.image || scratch.image->width() != w || scratch.image->height() != h) {
                framework->frameResourceRetainer().retain(scratch.framebuffer);
                framework->frameResourceRetainer().retain(scratch.image);
                scratch.image = vk::DeviceLocalImage::create(
                    framework->device(), framework->vma(), false, w, h, 1, VK_FORMAT_R8G8B8A8_UNORM,
                    VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
                scratch.framebuffer = vk::FramebufferBuilder{}
                                          .beginAttachment()
                                          .defineAttachment(scratch.image)
                                          .endAttachment()
                                          .build(framework->device(), module->overlayPostRenderPass_);
            }
            targets.push_back(scratch);
        }
    }
    const auto queue = context->physicalDevice->mainQueueIndex();
    auto transition = [&](const std::shared_ptr<vk::DeviceLocalImage> &image, VkImageLayout layout) {
        if (image->imageLayout() == layout) return;
        context->overlayCommandBuffer->barriersBufferImage(
            {}, {{.srcStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                  .srcAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                  .dstStageMask = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                  .dstAccessMask = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT,
                  .oldLayout = image->imageLayout(),
                  .newLayout = layout,
                  .srcQueueFamilyIndex = queue,
                  .dstQueueFamilyIndex = queue,
                  .image = image,
                  .subresourceRange = vk::wholeColorSubresourceRange}});
        image->imageLayout() = layout;
    };
    auto buffers = Renderer::instance().buffers();
    for (const auto &pass : plan.passes) {
        const auto &target = targets[pass.output];
        const auto &image = target.image;
        uint32_t offset = pass.uniformOffset;
        if (offset == UINT32_MAX && pass.program == BLUR) {
            vk::Data::OverlayPostUBO ubo{};
            const auto &input = targets[pass.inputs.front()].image;
            ubo.projectionMat = glm::mat4(1);
            ubo.inSize = {input->width(), input->height()};
            ubo.outSize = {image->width(), image->height()};
            ubo.blurDir = {pass.parameters[0], pass.parameters[1]};
            ubo.radius = pass.parameters[2];
            ubo.radiusMultiplier = pass.parameters[3];
            buffers->appendOverlayPostUniform(ubo);
            offset = buffers->overlayPostUniformOffset(buffers->getPostID());
        } else if (offset == UINT32_MAX)
            offset = 0;
        if (effectDescriptorCursor == effectDescriptors.size())
            effectDescriptors.push_back(effectTable(framework->device()));
        auto table = effectDescriptors[effectDescriptorCursor++];
        auto sampler = overlayDrawColorImageSampler;
        if (pass.filter == Filter::Nearest) {
            if (!module->effectNearestSampler_)
                module->effectNearestSampler_ =
                    vk::Sampler::create(framework->device(), VK_FILTER_NEAREST, VK_SAMPLER_MIPMAP_MODE_NEAREST,
                                        VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
            sampler = module->effectNearestSampler_;
        }
        transition(overlayDrawColorImage, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        for (uint32_t binding : {1u, 2u, 4u, 5u})
            table->bindSamplerImage(sampler, overlayDrawColorImage, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 0,
                                    binding, 0);
        const auto bindings = pass.program < MAX_OVERLAY_POST_PIPELINE_TYPE ?
                                  builtinInputs(pass.program) :
                                  module->effectPrograms_.at(pass.program)->inputBindings;
        for (size_t n = 0; n < pass.inputs.size(); ++n) {
            const auto &input = targets[pass.inputs[n]].image;
            transition(input, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            table->bindSamplerImage(sampler, input, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 0, bindings[n], 0);
        }
        table->bindBufferRange(buffers->overlayPostUniformBuffer(), 1, 1, 0,
                               buffers->overlayPostUniformDescriptorRange());
        const auto key = std::tuple{pass.program, image->width(), image->height()};
        if (!module->effectPipelines_.contains(key)) {
            auto shaders = pass.program < MAX_OVERLAY_POST_PIPELINE_TYPE ?
                               module->overlayPostPipelineShaders_.at(OverlayPostPipelineType(pass.program)) :
                               module->effectPrograms_.at(pass.program)->shaders;
            module->effectPipelines_[key] =
                vk::GraphicsPipelineBuilder{}
                    .defineRenderPass(module->overlayPostRenderPass_, 0)
                    .beginShaderStage()
                    .defineShaderStage(shaders.vertexShader, VK_SHADER_STAGE_VERTEX_BIT)
                    .defineShaderStage(shaders.fragmentShader, VK_SHADER_STAGE_FRAGMENT_BIT)
                    .endShaderStage()
                    .defineVertexInputState<void>()
                    .defineViewportScissorState(
                        {.viewport = {0, 0, float(image->width()), float(image->height()), 0, 1},
                         .scissor = {{0, 0}, {image->width(), image->height()}}})
                    .defineDepthStencilState({.depthTestEnable = VK_FALSE,
                                              .depthWriteEnable = VK_FALSE,
                                              .depthCompareOp = VK_COMPARE_OP_ALWAYS,
                                              .depthBoundsTestEnable = VK_FALSE,
                                              .stencilTestEnable = VK_FALSE})
                    .beginColorBlendAttachmentState()
                    .defineDefaultColorBlendAttachmentState()
                    .endColorBlendAttachmentState()
                    .definePipelineLayout(table)
                    .build(framework->device());
        }
        const auto pipeline = module->effectPipelines_.at(key);
        framework->frameResourceRetainer().retain(pipeline);
        transition(image, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        context->overlayCommandBuffer->beginRenderPass({.renderPass = module->overlayPostRenderPass_,
                                                        .framebuffer = target.framebuffer,
                                                        .renderAreaExtent = {image->width(), image->height()},
                                                        .clearValues = {{.color = {0, 0, 0, 0}}}});
        auto cmd = context->overlayCommandBuffer->vkCommandBuffer();
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline->vkPipeline());
        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, table->vkPipelineLayout(), 0,
                                uint32_t(table->descriptorSet().size()), table->descriptorSet().data(), 1, &offset);
        vkCmdPushConstants(cmd, table->vkPipelineLayout(), VK_SHADER_STAGE_FRAGMENT_BIT, 0, 16, pass.parameters.data());
        context->overlayCommandBuffer->draw(3, 1);
        context->overlayCommandBuffer->endRenderPass();
    }
    auto output = targets[plan.output].image;
    transition(output, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
    transition(overlayDrawColorImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    VkImageBlit copy{};
    copy.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copy.dstSubresource = copy.srcSubresource;
    copy.srcOffsets[1] = {int32_t(output->width()), int32_t(output->height()), 1};
    copy.dstOffsets[1] = {int32_t(overlayDrawColorImage->width()), int32_t(overlayDrawColorImage->height()), 1};
    vkCmdBlitImage(context->overlayCommandBuffer->vkCommandBuffer(), output->vkImage(), output->imageLayout(),
                   overlayDrawColorImage->vkImage(), overlayDrawColorImage->imageLayout(), 1, &copy,
                   plan.filter == Filter::Linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
    transition(output, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
    transition(overlayDrawColorImage, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    overlayMode = NONE;
    if (plan.stage == Stage::WorldPost) framework->captureFrameGenerationHudless(*context, overlayDrawColorImage);
}
void UIModuleContext::postOverlay(OverlayPostPipelineType type, uint32_t offset) {
    auto context = frameworkContext.lock();
    auto framework = context->framework.lock();
    if (!framework->isRunning()) return;
    if (type == BLUR) {
        end();
        if (!context->fgGui.fullScreenBlur)
            framework->blurFrameGenerationHudless(
                *context, Renderer::instance().buffers()->recordedOverlayPostUniform(offset), overlayDrawColorImage);
    }
    executeEffects(mcvr::effects::single(type, offset));
}
void UIModuleContext::postBlur(int times) {
    const int last = Renderer::instance().buffers()->getPostID();
    for (int i = last - times + 1; i <= last; i++)
        postOverlay(BLUR, Renderer::instance().buffers()->overlayPostUniformOffset(i));
}
void UIModuleContext::postEntityEffect(OverlayPostPipelineType type) {
    executeEffects(mcvr::effects::entity(type));
}
