package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.GlStateManager;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes Blaze3D framebuffer operations to real Vulkan attachments and render targets. */
@Mixin(GlStateManager.class)
public abstract class OpenGlFramebufferCompatibilityMixins {

    @Inject(method = "_glBindFramebuffer(II)V", at = @At("HEAD"), cancellable = true)
    private static void radiance$BindFramebuffer(int target, int framebuffer,
        CallbackInfo ci) {
        FramebufferProxy.bindFramebuffer(target, framebuffer);
        ci.cancel();
    }

    @Inject(method = "_glBlitFrameBuffer(IIIIIIIIII)V", at = @At("HEAD"), cancellable = true)
    private static void radiance$BlitFramebuffer(int srcX0, int srcY0, int srcX1,
        int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter,
        CallbackInfo ci) {
        FramebufferProxy.blit(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
        ci.cancel();
    }

    @Inject(method = "_glBindRenderbuffer(II)V", at = @At("HEAD"), cancellable = true)
    private static void radiance$BindRenderbuffer(int target, int renderbuffer,
        CallbackInfo ci) {
        FramebufferProxy.requireRenderbuffer(target);
        FramebufferProxy.bindRenderbuffer(renderbuffer);
        ci.cancel();
    }

    @Inject(method = "_glDeleteRenderbuffers(I)V", at = @At("HEAD"), cancellable = true)
    private static void radiance$DeleteRenderbuffers(int renderbuffer, CallbackInfo ci) {
        FramebufferProxy.deleteRenderbuffer(renderbuffer);
        ci.cancel();
    }

    @Inject(method = "_glDeleteFramebuffers(I)V", at = @At("HEAD"), cancellable = true)
    private static void radiance$DeleteFramebuffers(int framebuffer, CallbackInfo ci) {
        FramebufferProxy.deleteFramebuffer(framebuffer);
        ci.cancel();
    }

    @Inject(method = "glGenFramebuffers()I", at = @At("HEAD"), cancellable = true)
    private static void radiance$GenFramebuffers(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FramebufferProxy.createFramebuffer());
    }

    @Inject(method = "glGenRenderbuffers()I", at = @At("HEAD"), cancellable = true)
    private static void radiance$GenRenderbuffers(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FramebufferProxy.createRenderbuffer());
    }

    @Inject(method = "_glRenderbufferStorage(IIII)V", at = @At("HEAD"), cancellable = true)
    private static void radiance$RenderbufferStorage(int target, int internalFormat,
        int width, int height, CallbackInfo ci) {
        FramebufferProxy.requireRenderbuffer(target);
        FramebufferProxy.renderbufferStorage(internalFormat, width, height, 1);
        ci.cancel();
    }

    @Inject(method = "_glFramebufferRenderbuffer(IIII)V", at = @At("HEAD"), cancellable = true)
    private static void radiance$FramebufferRenderbuffer(int target, int attachment,
        int renderBufferTarget, int renderBuffer, CallbackInfo ci) {
        FramebufferProxy.requireRenderbuffer(renderBufferTarget);
        FramebufferProxy.attachRenderbuffer(target, attachment, renderBuffer);
        ci.cancel();
    }

    @Inject(method = "glCheckFramebufferStatus(I)I", at = @At("HEAD"), cancellable = true)
    private static void radiance$FramebufferStatus(int target,
        CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FramebufferProxy.checkStatus(target));
    }

    @Inject(method = "_glFramebufferTexture2D(IIIII)V", at = @At("HEAD"), cancellable = true)
    private static void radiance$FramebufferTexture2D(int target, int attachment,
        int textureTarget, int texture, int level, CallbackInfo ci) {
        FramebufferProxy.requireTexture2D(textureTarget, level);
        FramebufferProxy.attachTexture(target, attachment, texture, level);
        ci.cancel();
    }

    @Inject(method = "getBoundFramebuffer()I", at = @At("HEAD"), cancellable = true)
    private static void radiance$GetBoundFramebuffer(
        CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER));
    }
}
