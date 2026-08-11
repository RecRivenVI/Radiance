#include "core/render/modules/ui_module.hpp"
#include "core/render/buffers.hpp"
#include "core/render/render_framework.hpp"
#include "core/render/renderer.hpp"

void UIModuleContext::postDiagram() {
    auto context = frameworkContext.lock();
    auto framework = context->framework.lock();
    auto module = uiModule.lock();

    if (!framework->isRunning() || overlayMode != DIAGRAM_DRAW) return;

    context->overlayCommandBuffer->endRenderPass();
    diagramDrawColorImage->imageLayout() = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    diagramDrawDepthImage->imageLayout() = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
    overlayMode = NONE;

    auto mainQueueIndex = context->physicalDevice->mainQueueIndex();
    context->overlayCommandBuffer->barriersBufferImage(
        {}, {{
                 .srcStageMask = VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                 .srcAccessMask = VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                 .dstStageMask = VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                 .dstAccessMask = VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                 .oldLayout = diagramDrawColorImage->imageLayout(),
                 .newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                 .srcQueueFamilyIndex = mainQueueIndex,
                 .dstQueueFamilyIndex = mainQueueIndex,
                 .image = diagramDrawColorImage,
                 .subresourceRange = vk::wholeColorSubresourceRange,
             },
             {
                 .srcStageMask = VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                 .srcAccessMask = VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                 .dstStageMask = VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                 .dstAccessMask = VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                 .oldLayout = diagramDrawDepthImage->imageLayout(),
                 .newLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL,
                 .srcQueueFamilyIndex = mainQueueIndex,
                 .dstQueueFamilyIndex = mainQueueIndex,
                 .image = diagramDrawDepthImage,
                 .subresourceRange = vk::wholeDepthSubresourceRange,
             }});
    diagramDrawColorImage->imageLayout() = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    diagramDrawDepthImage->imageLayout() = VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;

    switchOverlayPost();
    int postID = Renderer::instance().buffers()->getPostID();
    if (postID < 0) { throw std::runtime_error("Diagram post-process uniform was not uploaded"); }

    vkCmdBindPipeline(context->overlayCommandBuffer->vkCommandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS,
                      module->overlayPostPipelines_[DIAGRAM]->vkPipeline());
    uint32_t dynamicOffsets[] = {0, Renderer::instance().buffers()->overlayPostUniformOffset(postID)};
    vkCmdBindDescriptorSets(context->overlayCommandBuffer->vkCommandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS,
                            overlayDescriptorTable->vkPipelineLayout(), 0,
                            overlayDescriptorTable->descriptorSet().size(),
                            overlayDescriptorTable->descriptorSet().data(), 2, dynamicOffsets);
    context->overlayCommandBuffer->draw(3, 1);

    context->overlayCommandBuffer->endRenderPass();
    overlayPostColorImage->imageLayout() = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    overlayMode = NONE;

    context->overlayCommandBuffer->barriersBufferImage(
        {},
        {{
             .srcStageMask = VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
             .srcAccessMask = VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
             .dstStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
             .dstAccessMask = VK_ACCESS_2_TRANSFER_READ_BIT,
             .oldLayout = overlayPostColorImage->imageLayout(),
             .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
             .srcQueueFamilyIndex = mainQueueIndex,
             .dstQueueFamilyIndex = mainQueueIndex,
             .image = overlayPostColorImage,
             .subresourceRange = vk::wholeColorSubresourceRange,
         },
         {
             .srcStageMask = VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
             .srcAccessMask = VK_ACCESS_2_SHADER_SAMPLED_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
             .dstStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
             .dstAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
             .oldLayout = overlayDrawColorImage->imageLayout(),
             .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
             .srcQueueFamilyIndex = mainQueueIndex,
             .dstQueueFamilyIndex = mainQueueIndex,
             .image = overlayDrawColorImage,
             .subresourceRange = vk::wholeColorSubresourceRange,
         }});
    overlayPostColorImage->imageLayout() = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    overlayDrawColorImage->imageLayout() = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;

    VkImageBlit imageBlit{};
    imageBlit.srcSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    imageBlit.srcSubresource.mipLevel = 0;
    imageBlit.srcSubresource.baseArrayLayer = 0;
    imageBlit.srcSubresource.layerCount = 1;
    imageBlit.srcOffsets[0] = {0, 0, 0};
    imageBlit.srcOffsets[1] = {static_cast<int>(overlayPostColorImage->width()),
                               static_cast<int>(overlayPostColorImage->height()), 1};
    imageBlit.dstSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    imageBlit.dstSubresource.mipLevel = 0;
    imageBlit.dstSubresource.baseArrayLayer = 0;
    imageBlit.dstSubresource.layerCount = 1;
    imageBlit.dstOffsets[0] = {0, 0, 0};
    imageBlit.dstOffsets[1] = {static_cast<int>(overlayDrawColorImage->width()),
                               static_cast<int>(overlayDrawColorImage->height()), 1};
    vkCmdBlitImage(context->overlayCommandBuffer->vkCommandBuffer(), overlayPostColorImage->vkImage(),
                   overlayPostColorImage->imageLayout(), overlayDrawColorImage->vkImage(),
                   overlayDrawColorImage->imageLayout(), 1, &imageBlit, VK_FILTER_NEAREST);

    context->overlayCommandBuffer->barriersBufferImage({}, {{
                                                                .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                                                .srcAccessMask = VK_ACCESS_2_TRANSFER_READ_BIT,
                                                                .dstStageMask = VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                                                                .dstAccessMask = VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                                                                .oldLayout = overlayPostColorImage->imageLayout(),
                                                                .newLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                                                                .srcQueueFamilyIndex = mainQueueIndex,
                                                                .dstQueueFamilyIndex = mainQueueIndex,
                                                                .image = overlayPostColorImage,
                                                                .subresourceRange = vk::wholeColorSubresourceRange,
                                                            },
                                                            {
                                                                .srcStageMask = VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                                                                .srcAccessMask = VK_ACCESS_2_TRANSFER_WRITE_BIT,
                                                                .dstStageMask = VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                                                                .dstAccessMask = VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                                                                .oldLayout = overlayDrawColorImage->imageLayout(),
                                                                .newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                                                                .srcQueueFamilyIndex = mainQueueIndex,
                                                                .dstQueueFamilyIndex = mainQueueIndex,
                                                                .image = overlayDrawColorImage,
                                                                .subresourceRange = vk::wholeColorSubresourceRange,
                                                            }});
    overlayPostColorImage->imageLayout() = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    overlayDrawColorImage->imageLayout() = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    if (diagramStateSaved) {
        overlayScissorEnabled = savedOverlayScissorEnabled;
        overlayScissor = savedOverlayScissor;
        overlayScissorGl = savedOverlayScissorGl;
        overlayViewport = savedOverlayViewport;
        overlayViewportGl = savedOverlayViewportGl;
        overlayDepthTestEnable = savedOverlayDepthTestEnable;
        overlayDepthWriteEnable = savedOverlayDepthWriteEnable;
        overlayDepthCompareOp = savedOverlayDepthCompareOp;
        diagramStateSaved = false;
    }
}
