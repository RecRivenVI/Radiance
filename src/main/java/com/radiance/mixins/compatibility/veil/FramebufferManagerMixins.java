package com.radiance.mixins.compatibility.veil;

import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Materializes buffers intentionally registered with build(false) when first consumed. */
@Mixin(value = FramebufferManager.class, remap = false)
public abstract class FramebufferManagerMixins {
    @Inject(method = "getFramebuffer", at = @At("RETURN"))
    private void radiance$createDeferredFramebuffer(ResourceLocation name,
        CallbackInfoReturnable<AdvancedFbo> cir) {
        AdvancedFbo framebuffer = cir.getReturnValue();
        if (framebuffer != null && framebuffer.getId() == -1) {
            framebuffer.create();
        }
    }
}
