package com.radiance.mixins.compatibility.veil;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Veil's necromancer bone/instance buffers are never created under Radiance's Vulkan
 * renderer, but {@code delete()} still issues an unconditional OpenGL
 * {@code glDeleteBuffers} call on level transitions.
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.necromancer.render.NecromancerRenderDispatcher",
    remap = false)
public abstract class VeilNecromancerDispatcherMixins {

    @Inject(method = "delete", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$skipOpenGlBufferDelete(CallbackInfo ci) {
        ci.cancel();
    }
}
