package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilFramebufferCompatibility;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.impl.client.render.framebuffer.AdvancedFboMutableTextureAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvancedFboMutableTextureAttachment.class, remap = false)
public abstract class AdvancedFboMutableTextureAttachmentMixins {
    @Inject(method = "attach", at = @At("HEAD"), cancellable = true)
    private void radiance$attach(AdvancedFbo framebuffer, int attachment, CallbackInfo ci) {
        AdvancedFboMutableTextureAttachment self =
            (AdvancedFboMutableTextureAttachment) (Object) this;
        VeilFramebufferCompatibility.attachTexture(framebuffer, self.getAttachmentType(),
            attachment, self.getId());
        ci.cancel();
    }
}
