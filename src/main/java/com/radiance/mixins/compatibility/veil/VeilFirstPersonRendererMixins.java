package com.radiance.mixins.compatibility.veil;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Veil's first-person pipeline builds a wrapper around the vanilla OpenGL main
 * framebuffer and queries it with {@code glGetTexLevelParameteri}. Radiance has no
 * OpenGL context and renders the hand through its own pipeline, so both hooks are
 * skipped.
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.pipeline.VeilFirstPersonRenderer",
    remap = false)
public abstract class VeilFirstPersonRendererMixins {

    @Inject(method = "bind", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$skipOpenGlFirstPersonBind(int mask, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "unbind", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$skipOpenGlFirstPersonUnbind(CallbackInfo ci) {
        ci.cancel();
    }
}
