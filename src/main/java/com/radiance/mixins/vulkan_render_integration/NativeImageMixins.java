package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public abstract class NativeImageMixins implements
    com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt {

    @Shadow
    private long pixels;

    @Final
    @Shadow
    private long size;

    @Final
    @Shadow
    private int width;

    @Final
    @Shadow
    private NativeImage.Format format;

    @Final
    @Shadow
    private int height;

    @Shadow
    public abstract NativeImage mappedCopy(IntUnaryOperator operator);

    @Shadow
    public abstract NativeImage.Format format();

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "upload(IIIIIIIZZZZ)V")
    private void radiance$ownedUpload(int level, int x, int y, int skipX, int skipY, int w, int h,
        boolean blur, boolean clamp, boolean mipmap, boolean close,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        INativeImageExt self = (INativeImageExt) this;
        var owner = self.radiance$getTargetOwner();
        if (owner == null && self.radiance$getTargetID() < 0) {
            owner = TextureProxy.TASKS.owner(TextureProxy.boundTexture());
            self.radiance$setTargetOwner(owner);
        }
        if (owner == null) {
            if (close) this.close();
            throw new IllegalStateException("NativeImage upload has no owned Vulkan texture");
        }
        final var capturedOwner = owner;
        TextureProxy.TASKS.submit(capturedOwner, true, work -> {
            if (RenderSystem.isOnRenderThreadOrInit()) work.run();
            else RenderSystem.recordRenderCall(work::run);
        }, () -> {
            // A NativeImage may be retargeted while this upload is queued. Auxiliary-image
            // resolution and the JNI call must both see the owner captured at production.
            var previous = self.radiance$getTargetOwner();
            self.radiance$setTargetOwner(capturedOwner);
            try { original.call(level, x, y, skipX, skipY, w, h, blur, clamp, mipmap, false); }
            finally { self.radiance$setTargetOwner(previous); }
        },
            () -> { if (close) this.close(); });
    }

    @Inject(method = "_upload(IIIIIIIZZZZ)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;checkAllocated()V", shift = At.Shift.AFTER), cancellable = true)
    public void redirectUploadInternal(int level, int offsetX, int offsetY, int unpackSkipPixels,
        int unpackSkipRows, int regionWidth, int regionHeight, boolean blur, boolean clamp,
        boolean mipmap, boolean close, CallbackInfo ci) {
        try {
            INativeImageExt self = (INativeImageExt) this;
            var owner = self.radiance$getTargetOwner();
            if (!Thread.holdsLock(TextureProxy.class) || owner == null ||
                !owner.equals(TextureProxy.TASKS.owner(owner.id())))
                throw new IllegalStateException("NativeImage upload bypassed the texture ownership task");
            int targetId = owner.id();
            AuxiliaryTextures.loadAndUpload((NativeImage) (Object) this, self, level, offsetX,
                offsetY, unpackSkipPixels, unpackSkipRows, regionWidth, regionHeight, blur);

            TextureProxy.setFilter(targetId,
                (blur ? com.radiance.client.constant.VulkanConstants.VkFilter.VK_FILTER_LINEAR
                    : com.radiance.client.constant.VulkanConstants.VkFilter.VK_FILTER_NEAREST).getValue(),
                mipmap
                    ? (blur
                        ? com.radiance.client.constant.VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR
                        : com.radiance.client.constant.VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST).getValue()
                    : com.radiance.client.constant.VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
            TextureProxy.setClamp(targetId,
                (clamp
                    ? com.radiance.client.constant.VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
                    : com.radiance.client.constant.VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT).getValue());

            TextureProxy.queueUpload(pixels, (int) size, width, targetId, unpackSkipPixels,
                unpackSkipRows, offsetX, offsetY, regionWidth, regionHeight, level);
        } finally {
            if (close) {
                this.close();
            }
        }
        ci.cancel();
    }

    @Inject(method = "close()V", at = @At(value = "HEAD"))
    public void closeImage(CallbackInfo ci) {
        INativeImageExt self = (INativeImageExt) this;
        NativeImage specularImage = self.radiance$getSpecularNativeImage();
        NativeImage normalImage = self.radiance$getNormalNativeImage();
        NativeImage flagImage = self.radiance$getFlagNativeImage();
        if (specularImage != null) {
            specularImage.close();
        }
        if (normalImage != null) {
            normalImage.close();
        }
        if (flagImage != null) {
            flagImage.close();
        }
    }

    @Override
    public NativeImage radiance$alignTo(NativeImage source) {
        int targetWidth = source.getWidth();
        int targetHeight = source.getHeight();
        NativeImage.Format targetFormat = source.format();

        if (width == targetWidth && height == targetHeight && format == targetFormat) {
            return (NativeImage) (Object) this;
        }

        NativeImage dest = new NativeImage(targetFormat, targetWidth, targetHeight, false);

        int srcChannels = this.format.components();
        int destChannels = targetFormat.components();
        int commonChannels = Math.min(srcChannels, destChannels);

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int sampleX = (x < this.width) ? x : (x % this.width);
                int sampleY = (y < this.height) ? y : (y % this.height);

                long srcPixelPtr =
                    this.pixels + (sampleX + (long) sampleY * this.width) * srcChannels;
                long destPixelPtr =
                    ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) dest).radiance$getPointer()
                        + (long) (x + (long) y * targetWidth) * destChannels;

                for (int c = 0; c < commonChannels; c++) {
                    byte val = MemoryUtil.memGetByte(srcPixelPtr + c);
                    MemoryUtil.memPutByte(destPixelPtr + c, val);
                }

                if (destChannels > srcChannels) {
                    for (int c = srcChannels; c < destChannels; c++) {
                        MemoryUtil.memPutByte(destPixelPtr + c, (byte) 0);
                    }
                }
            }
        }
        return dest;
    }

    @Override
    public long radiance$getPointer() {
        return pixels;
    }

    @Shadow
    protected abstract void checkAllocated();

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Shadow
    public abstract int getPixelRGBA(int x, int y);

    @Shadow
    public abstract void setPixelRGBA(int x, int y, int color);

    @Shadow
    public abstract void close();

    @Inject(method = "downloadTexture(IZ)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectLoadFromTextureImage(int level, boolean removeAlpha, CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        this.checkAllocated();
        TextureProxy.downloadTexture(TextureProxy.boundTexture(), level, this.width, this.height,
            this.format.components(), this.pixels);
        if (removeAlpha && this.format.hasAlpha()) {
            for (int i = 0; i < this.getHeight(); i++) {
                for (int j = 0; j < this.getWidth(); j++) {
                this.setPixelRGBA(j, i,
                    this.getPixelRGBA(j, i) | 255 << this.format.alphaOffset());
                }
            }
        }
        ci.cancel();
    }

    @Inject(method = "downloadDepthBuffer(F)V", at = @At("HEAD"), cancellable = true)
    private void radiance$readDepth(float unused, CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        if (this.format.components() != 1) throw new IllegalStateException("Depth buffer requires one component");
        this.checkAllocated();
        FramebufferProxy.readPixels(0, 0, this.width, this.height, 0x1902, 0x1401, this.pixels);
        ci.cancel();
    }

    @Override
    public void radiance$loadFromTextureImageWithoutUI(int level, boolean removeAlpha) {
        RenderSystem.assertOnRenderThread();
        this.checkAllocated();
        RendererProxy.takeScreenshot(false, this.width, this.height, this.format.components(),
            this.pixels);
        if (removeAlpha && this.format.hasAlpha()) {
            for (int i = 0; i < this.getHeight(); i++) {
                for (int j = 0; j < this.getWidth(); j++) {
                this.setPixelRGBA(j, i,
                    this.getPixelRGBA(j, i) | 255 << this.format.alphaOffset());
                }
            }
        }
    }
}
