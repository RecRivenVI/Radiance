package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.RenderTargetAllocationCleanup;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IMainTargetExt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetStorageMixins {

    @Unique
    private static final int RADIANCE_GL_TEXTURE_2D = 0x0DE1;
    @Unique
    private static final int RADIANCE_GL_NEAREST = 0x2600;
    @Unique
    private static final int RADIANCE_GL_LINEAR = 0x2601;
    @Unique
    private static final int RADIANCE_GL_RGBA8 = 0x8058;
    @Unique
    private static final int RADIANCE_GL_DEPTH_COMPONENT32F = 0x8CAC;
    @Unique
    private static final int RADIANCE_GL_DEPTH32F_STENCIL8 = 0x8CAD;
    @Unique
    private static final int RADIANCE_GL_COLOR_ATTACHMENT0 = 0x8CE0;
    @Unique
    private static final int RADIANCE_GL_DEPTH_ATTACHMENT = 0x8D00;
    @Unique
    private static final int RADIANCE_GL_STENCIL_ATTACHMENT = 0x8D20;
    @Unique
    private static final int RADIANCE_GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;
    @Unique
    private static final int RADIANCE_GL_FRAMEBUFFER_COMPLETE = 0x8CD5;

    @Shadow
    public int width;
    @Shadow
    public int height;
    @Shadow
    public int viewWidth;
    @Shadow
    public int viewHeight;
    @Shadow
    public int frameBufferId;
    @Shadow
    protected int colorTextureId;
    @Shadow
    protected int depthBufferId;
    @Shadow
    public int filterMode;
    @Shadow
    @Final
    public boolean useDepth;
    @Shadow
    private boolean stencilEnabled;

    @Shadow
    public abstract void clear(boolean getError);

    @Shadow
    public abstract void unbindRead();

    @Inject(method = "createBuffers(IIZ)V", at = @At("HEAD"), cancellable = true)
    private void radiance$createBuffers(int width, int height, boolean getError, CallbackInfo ci) {
        if ((Object) this instanceof IMainTargetExt mainTarget) {
            mainTarget.radiance$initializeDefaultTarget(width, height, true, getError);
            ci.cancel();
            return;
        }
        RenderSystem.assertOnRenderThreadOrInit();
        int maxSize = RenderSystem.maxSupportedTextureSize();
        if (width <= 0 || width > maxSize || height <= 0 || height > maxSize) {
            throw new IllegalArgumentException(
                "Window " + width + "x" + height + " size out of bounds (max. size: " + maxSize
                    + ")");
        }

        this.viewWidth = width;
        this.viewHeight = height;
        this.width = width;
        this.height = height;
        try {
            this.frameBufferId = FramebufferProxy.createFramebuffer();
            this.colorTextureId = TextureUtil.generateTextureId();
            FramebufferProxy.prepareAttachmentTexture(this.colorTextureId, 1, width, height,
                RADIANCE_GL_RGBA8);
            TextureTracker.GLID2Texture.put(this.colorTextureId,
                new TextureTracker.Texture(width, height, 4,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM, 0));
            this.radiance$applyFilter(RADIANCE_GL_NEAREST, true);
            TextureProxy.setClamp(this.colorTextureId,
                VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.getValue());

            if (this.useDepth) {
                this.depthBufferId = TextureUtil.generateTextureId();
                int depthFormat = this.stencilEnabled ? RADIANCE_GL_DEPTH32F_STENCIL8
                    : RADIANCE_GL_DEPTH_COMPONENT32F;
                FramebufferProxy.prepareAttachmentTexture(this.depthBufferId, 1, width, height,
                    depthFormat);
                TextureProxy.setFilter(this.depthBufferId,
                    VulkanConstants.VkFilter.VK_FILTER_NEAREST.getValue(),
                    VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
                TextureProxy.setClamp(this.depthBufferId,
                    VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.getValue());
            }

            FramebufferProxy.bindFramebuffer(FramebufferProxy.FRAMEBUFFER, this.frameBufferId);
            FramebufferProxy.requireTexture2D(RADIANCE_GL_TEXTURE_2D, 0);
            FramebufferProxy.attachTexture(FramebufferProxy.FRAMEBUFFER,
                RADIANCE_GL_COLOR_ATTACHMENT0, this.colorTextureId, 0);
            if (this.useDepth) {
                this.radiance$attachDepthTexture();
            }

            int status = FramebufferProxy.checkStatus(FramebufferProxy.FRAMEBUFFER);
            if (status != RADIANCE_GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException(
                    "Framebuffer allocation is incomplete: 0x" + Integer.toHexString(status));
            }
            this.clear(getError);
            this.unbindRead();
        } catch (RuntimeException | Error failure) {
            this.radiance$releasePartialAllocation(failure);
            throw failure;
        }
        ci.cancel();
    }

    @Inject(method = "enableStencil()V", at = @At("HEAD"))
    private void radiance$requireRealMainStencil(CallbackInfo ci) {
        if ((Object) this instanceof IMainTargetExt && !FramebufferProxy.defaultStencilAvailable()) {
            throw new UnsupportedOperationException("The Vulkan main target has no compatible stencil format");
        }
    }

    @Inject(method = "setFilterMode(IZ)V", at = @At("HEAD"), cancellable = true)
    private void radiance$setFilterMode(int filterMode, boolean force, CallbackInfo ci) {
        RenderSystem.assertOnRenderThreadOrInit();
        this.radiance$applyFilter(filterMode, force);
        ci.cancel();
    }

    @Unique
    private void radiance$applyFilter(int filterMode, boolean force) {
        if (!force && filterMode == this.filterMode) {
            return;
        }
        if (this.colorTextureId < 0) {
            throw new IllegalStateException("Render target color attachment is not allocated");
        }
        int vkFilter = switch (filterMode) {
            case RADIANCE_GL_NEAREST -> VulkanConstants.VkFilter.VK_FILTER_NEAREST.getValue();
            case RADIANCE_GL_LINEAR -> VulkanConstants.VkFilter.VK_FILTER_LINEAR.getValue();
            default -> throw new IllegalArgumentException(
                "Unsupported render target filter mode: " + filterMode);
        };
        TextureProxy.setFilter(this.colorTextureId, vkFilter,
            VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
        this.filterMode = filterMode;
    }

    @Unique
    private void radiance$attachDepthTexture() {
        if (!this.stencilEnabled) {
            FramebufferProxy.attachTexture(FramebufferProxy.FRAMEBUFFER,
                RADIANCE_GL_DEPTH_ATTACHMENT, this.depthBufferId, 0);
            return;
        }
        if (net.neoforged.neoforge.common.NeoForgeConfig.CLIENT.useCombinedDepthStencilAttachment
            .get()) {
            FramebufferProxy.attachTexture(FramebufferProxy.FRAMEBUFFER,
                RADIANCE_GL_DEPTH_STENCIL_ATTACHMENT, this.depthBufferId, 0);
        } else {
            FramebufferProxy.attachTexture(FramebufferProxy.FRAMEBUFFER,
                RADIANCE_GL_DEPTH_ATTACHMENT, this.depthBufferId, 0);
            FramebufferProxy.attachTexture(FramebufferProxy.FRAMEBUFFER,
                RADIANCE_GL_STENCIL_ATTACHMENT, this.depthBufferId, 0);
        }
    }

    @Unique
    private void radiance$releasePartialAllocation(Throwable allocationFailure) {
        int framebuffer = this.frameBufferId;
        int colorTexture = this.colorTextureId;
        int depthTexture = this.depthBufferId;
        this.frameBufferId = -1;
        this.colorTextureId = -1;
        this.depthBufferId = -1;
        try {
            RenderTargetAllocationCleanup.release(framebuffer, colorTexture, depthTexture,
                () -> FramebufferProxy.bindFramebuffer(FramebufferProxy.FRAMEBUFFER, 0),
                TextureUtil::releaseTextureId, FramebufferProxy::deleteFramebuffer);
        } catch (RuntimeException | Error cleanupFailure) {
            allocationFailure.addSuppressed(cleanupFailure);
        }
    }
}
