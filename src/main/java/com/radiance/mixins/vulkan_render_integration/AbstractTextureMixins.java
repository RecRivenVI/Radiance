package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IAbstractTextureExt;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public class AbstractTextureMixins implements IAbstractTextureExt {

    @Shadow
    protected int id;

    @Inject(method = "setFilter(ZZ)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/AbstractTexture;bind()V",
            shift = At.Shift.AFTER),
        cancellable = true)
    public void redirectSetFilter(boolean bilinear, boolean mipmap, CallbackInfo ci) {
        TextureProxy.setFilter(id,
            (bilinear ? VulkanConstants.VkFilter.VK_FILTER_LINEAR :
                VulkanConstants.VkFilter.VK_FILTER_NEAREST).getValue(),
            mipmap ? (bilinear
                ? VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR.getValue() :
                VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue()) :
                VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
        ci.cancel();
    }

    @Inject(method = "releaseId", at = @At(value = "HEAD"), cancellable = true)
    public void releaseVulkanTextureId(CallbackInfo ci) {
        synchronized (TextureProxy.class) {
            var owner = TextureProxy.TASKS.owner(this.id);
            this.id = -1;
            TextureProxy.TASKS.submit(owner, false, work -> {
                if (RenderSystem.isOnRenderThread()) work.run();
                else RenderSystem.recordRenderCall(work::run);
            }, () -> TextureProxy.releaseTextureId(owner, MissingTextureAtlasSprite.getTexture().getId()),
                () -> {});
        }
        ci.cancel();
    }

    @Override
    public int radiance$getGlIDUnsafe() {
        if (this.id < 0) {
            throw new IllegalStateException("Texture id is not initialized");
        }
        return this.id;
    }

    @Inject(method = "getId", at = @At(value = "HEAD"), cancellable = true)
    public void redirectGetId(CallbackInfoReturnable<Integer> cir) {
        synchronized (TextureProxy.class) {
            if (this.id == -1) {
                this.id = TextureUtil.generateTextureId();
            }

            cir.setReturnValue(this.id);
        }
    }
}
