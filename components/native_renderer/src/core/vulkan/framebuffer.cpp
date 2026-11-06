#include "core/vulkan/framebuffer.hpp"

#include "core/logging.hpp"
#include "core/failure_state.hpp"

#include "core/vulkan/device.hpp"
#include "core/vulkan/image.hpp"
#include "core/vulkan/render_pass.hpp"

#include <array>
#include <iostream>

auto framebufferCout() {
    return mcvr::log::info("Framebuffer");
}

auto framebufferCerr() {
    return mcvr::log::error("Framebuffer");
}

vk::Framebuffer::Framebuffer(std::shared_ptr<Device> device, VkFramebuffer framebuffer)
    : device_(device), framebuffer_(framebuffer) {
#ifdef DEBUG
    framebufferCout() << "framebuffer created" << std::endl;
#endif
}

vk::Framebuffer::~Framebuffer() {
    vkDestroyFramebuffer(device_->vkDevice(), framebuffer_, nullptr);

#ifdef DEBUG
    framebufferCout() << "framebuffer deconstructed" << std::endl;
#endif
}

VkFramebuffer &vk::Framebuffer::vkFramebuffer() {
    return framebuffer_;
}

vk::FramebufferBuilder::FramebufferAttachmentsBuilder::FramebufferAttachmentsBuilder(FramebufferBuilder &parent)
    : parent(parent) {}

vk::FramebufferBuilder::FramebufferAttachmentsBuilder &
vk::FramebufferBuilder::FramebufferAttachmentsBuilder::defineAttachment(std::shared_ptr<Image> image, int viewIndex) {
    const auto view = image ? image->describeView(viewIndex) : ImageViewDescription{};
    if (!view.handle)
        mcvr::failure::invariant("FramebufferBuilder::defineAttachment", "missing image or invalid view index");
    attachments.push_back(view);

    return *this;
}

vk::FramebufferBuilder &vk::FramebufferBuilder::FramebufferAttachmentsBuilder::endAttachment() {
    return parent;
}

vk::FramebufferBuilder::FramebufferBuilder() : framebufferAttachmentsBuilder_(*this) {}

vk::FramebufferBuilder::FramebufferAttachmentsBuilder &vk::FramebufferBuilder::beginAttachment() {
    return framebufferAttachmentsBuilder_;
}

std::shared_ptr<vk::Framebuffer> vk::FramebufferBuilder::build(std::shared_ptr<Device> device,
                                                               std::shared_ptr<RenderPass> renderPass) {
    const auto &attachments = framebufferAttachmentsBuilder_.attachments;
    if (attachments.empty())
        mcvr::failure::invariant("FramebufferBuilder::build", "attachmentless framebuffer requires an explicit extent");
    const auto extent = attachments.front().extent;
    return build(std::move(device), std::move(renderPass), extent.width, extent.height);
}

std::shared_ptr<vk::Framebuffer> vk::FramebufferBuilder::build(std::shared_ptr<Device> device,
                                                               std::shared_ptr<RenderPass> renderPass,
                                                               uint32_t width,
                                                               uint32_t height) {
    const auto &views = framebufferAttachmentsBuilder_.attachments;
    const auto attachments = renderPass->attachments();
    if (width == 0 || height == 0) mcvr::failure::invariant("FramebufferBuilder::build", "empty framebuffer extent");
    if (views.size() != attachments.size())
        mcvr::failure::invariant("FramebufferBuilder::build", "attachment count does not match render pass");

    std::array<VkImageView, 8> inlineHandles;
    std::vector<VkImageView> largeHandles;
    if (views.size() > inlineHandles.size()) largeHandles.resize(views.size());
    auto *handles = largeHandles.empty() ? inlineHandles.data() : largeHandles.data();
    for (size_t i = 0; i < views.size(); ++i) {
        const auto &view = views[i];
        const auto &required = attachments[i];
        if (view.format != required.format)
            mcvr::failure::invariant("FramebufferBuilder::build",
                                     "attachment " + std::to_string(i) + " format " + std::to_string(view.format) +
                                         " does not match render pass format " + std::to_string(required.format));
        if ((view.usage & required.usage) != required.usage)
            mcvr::failure::invariant("FramebufferBuilder::build", "image lacks required attachment usage");
        if (view.extent.width < width || view.extent.height < height)
            mcvr::failure::invariant("FramebufferBuilder::build", "attachment view is smaller than framebuffer");
        handles[i] = view.handle;
    }

    VkFramebufferCreateInfo createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    createInfo.renderPass = renderPass->vkRenderPass();
    createInfo.attachmentCount = static_cast<uint32_t>(views.size());
    createInfo.pAttachments = handles;
    createInfo.width = width;
    createInfo.height = height;
    createInfo.layers = 1;

    VkFramebuffer framebuffer;
    if (const auto result = vkCreateFramebuffer(device->vkDevice(), &createInfo, nullptr, &framebuffer);
        result != VK_SUCCESS) {
        framebufferCerr() << "failed to create framebuffer" << std::endl;
        mcvr::failure::raise(mcvr::failure::Kind::runtime, result, "vkCreateFramebuffer");
    }

    return std::make_shared<Framebuffer>(device, framebuffer);
}
