package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screenshot.class)
public class ScreenshotRecorderMixins {

    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectTakeScreenshot(RenderTarget framebuffer,
        CallbackInfoReturnable<NativeImage> cir) {
        Minecraft mc = Minecraft.getInstance();
        int
            width =
            mc.getWindow()
                .getScreenWidth();
        int
            height =
            mc.getWindow()
                .getScreenHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);
        RendererProxy.takeScreenshot(true, width, height, nativeImage.format().components(),
            ((INativeImageExt) (Object) nativeImage).radiance$getPointer());
        if (nativeImage.format().hasAlpha()) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    nativeImage.setPixelRGBA(x, y,
                        nativeImage.getPixelRGBA(x, y) | 255 << nativeImage.format().alphaOffset());
                }
            }
        }
        cir.setReturnValue(nativeImage);
    }
}
