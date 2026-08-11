package com.radiance.mixins.compatibility.veil;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.radiance.compatibility.veil.VeilFramebufferCompatibility;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.impl.client.render.framebuffer.DSAAdvancedFboImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DSAAdvancedFboImpl.class, remap = false)
public abstract class DSAAdvancedFboMixins {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void radiance$create(CallbackInfo ci) {
        VeilFramebufferCompatibility.create((AdvancedFbo) (Object) this);
        ci.cancel();
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void radiance$clear(float red, float green, float blue, float alpha, float depth,
        int clearMask, int[] buffers, CallbackInfo ci) {
        VeilFramebufferCompatibility.clear((AdvancedFbo) (Object) this, red, green, blue, alpha,
            depth, clearMask, buffers);
        ci.cancel();
    }

    @Inject(method = "resetDrawBuffers", at = @At("HEAD"), cancellable = true)
    private void radiance$resetDrawBuffers(CallbackInfo ci) {
        AdvancedFbo framebuffer = (AdvancedFbo) (Object) this;
        VeilFramebufferCompatibility.drawBuffers(framebuffer, framebuffer.getDrawBuffers());
        ci.cancel();
    }

    @Inject(method = "drawBuffers", at = @At("HEAD"), cancellable = true)
    private void radiance$drawBuffers(int[] buffers, CallbackInfo ci) {
        VeilFramebufferCompatibility.drawBuffers((AdvancedFbo) (Object) this, buffers);
        ci.cancel();
    }

    @Inject(method = "resolveToFbo", at = @At("HEAD"), cancellable = true)
    private void radiance$resolveToFbo(int id, int width, int height, int mask, int filtering,
        CallbackInfo ci) {
        VeilFramebufferCompatibility.resolve((AdvancedFbo) (Object) this, id, width, height,
            mask, filtering);
        ci.cancel();
    }

    @Inject(method = "resolveToAdvancedFbo", at = @At("HEAD"), cancellable = true)
    private void radiance$resolveToAdvancedFbo(AdvancedFbo target, int mask, int filtering,
        CallbackInfo ci) {
        VeilFramebufferCompatibility.resolve((AdvancedFbo) (Object) this, target.getId(),
            target.getWidth(), target.getHeight(), mask, filtering);
        ci.cancel();
    }

    @Inject(method = "resolveToRenderTarget", at = @At("HEAD"), cancellable = true)
    private void radiance$resolveToRenderTarget(RenderTarget target, int mask, int filtering,
        CallbackInfo ci) {
        VeilFramebufferCompatibility.resolve((AdvancedFbo) (Object) this, target.frameBufferId,
            target.width, target.height, mask, filtering);
        ci.cancel();
    }
}
