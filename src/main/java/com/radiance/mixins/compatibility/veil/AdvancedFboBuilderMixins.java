package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilFramebufferCompatibility;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AdvancedFbo.Builder.class, remap = false)
public abstract class AdvancedFboBuilderMixins {
    @Redirect(method = "validateColorSize", at = @At(value = "INVOKE",
        target = "Lfoundry/veil/api/client/render/VeilRenderSystem;maxColorAttachments()I"))
    private int radiance$supportedColorAttachments() {
        return VeilFramebufferCompatibility.supportedColorAttachments();
    }
}
