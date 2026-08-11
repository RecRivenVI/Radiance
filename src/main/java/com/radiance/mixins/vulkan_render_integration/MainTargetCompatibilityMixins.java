package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.RenderTargetAllocationCleanup;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IMainTargetExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MainTarget.class)
public abstract class MainTargetCompatibilityMixins extends RenderTarget implements IMainTargetExt {

    protected MainTargetCompatibilityMixins(boolean useDepth) {
        super(useDepth);
    }

    @Inject(method = "createFrameBuffer(II)V", at = @At("HEAD"), cancellable = true)
    private void radiance$createDefaultFramebuffer(int width, int height, CallbackInfo ci) {
        this.radiance$initializeDefaultTarget(width, height, false, false);
        ci.cancel();
    }

    @Override
    public void radiance$initializeDefaultTarget(int width, int height, boolean clear, boolean clearError) {
        RenderSystem.assertOnRenderThreadOrInit();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Main target dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.viewWidth = width;
        this.viewHeight = height;
        this.frameBufferId = 0;
        try {
            this.colorTextureId = TextureUtil.generateTextureId();
            this.depthBufferId = TextureUtil.generateTextureId();
            FramebufferProxy.configureMainTargetAliases(this.colorTextureId, this.depthBufferId, width, height);
            TextureProxy.setFilter(this.colorTextureId,
                VulkanConstants.VkFilter.VK_FILTER_NEAREST.getValue(),
                VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
            TextureProxy.setClamp(this.colorTextureId,
                VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.getValue());
            TextureProxy.setFilter(this.depthBufferId,
                VulkanConstants.VkFilter.VK_FILTER_NEAREST.getValue(),
                VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
            TextureProxy.setClamp(this.depthBufferId,
                VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.getValue());
            TextureTracker.GLID2Texture.put(this.colorTextureId,
                new TextureTracker.Texture(width, height, 4,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM, 0));
            if (clear) {
                RenderTarget target = (RenderTarget) (Object) this;
                target.clear(clearError);
                target.unbindRead();
            }
        } catch (RuntimeException | Error failure) {
            int color = this.colorTextureId;
            int depth = this.depthBufferId;
            this.frameBufferId = -1;
            this.colorTextureId = -1;
            this.depthBufferId = -1;
            try {
                RenderTargetAllocationCleanup.release(-1, color, depth, () -> {
                }, TextureUtil::releaseTextureId, ignored -> {
                });
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
}
