package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilFramebufferCompatibility;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.AdvancedFboRenderAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AdvancedFboRenderAttachment.class, remap = false)
public abstract class AdvancedFboRenderAttachmentMixins {
    @Shadow private int id;
    @Shadow @Final private int attachmentType;
    @Shadow @Final private int attachmentFormat;
    @Shadow @Final private int width;
    @Shadow @Final private int height;
    @Shadow @Final private int samples;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
        target = "Lfoundry/veil/api/client/render/VeilRenderSystem;maxSamples()I"))
    private int radiance$supportedSamples() {
        return VeilFramebufferCompatibility.supportedSamples();
    }

    @Inject(method = "getId", at = @At("HEAD"), cancellable = true)
    private void radiance$getId(CallbackInfoReturnable<Integer> cir) {
        if (this.id == 0) {
            this.id = VeilFramebufferCompatibility.ensureRenderbuffer(this.id);
        }
        cir.setReturnValue(this.id);
    }

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void radiance$create(CallbackInfo ci) {
        AdvancedFboRenderAttachment self = (AdvancedFboRenderAttachment) (Object) this;
        VeilFramebufferCompatibility.createRenderAttachment(self.getId(), this.attachmentFormat,
            this.width, this.height, this.samples);
        ci.cancel();
    }

    @Inject(method = "attach", at = @At("HEAD"), cancellable = true)
    private void radiance$attach(AdvancedFbo framebuffer, int attachment, CallbackInfo ci) {
        AdvancedFboRenderAttachment self = (AdvancedFboRenderAttachment) (Object) this;
        VeilFramebufferCompatibility.attachRenderbuffer(framebuffer, this.attachmentType,
            attachment, self.getId());
        ci.cancel();
    }

    @Inject(method = "bindAttachment", at = @At("HEAD"), cancellable = true)
    private void radiance$bind(CallbackInfo ci) {
        VeilFramebufferCompatibility.bindRenderbuffer(
            ((AdvancedFboRenderAttachment) (Object) this).getId());
        ci.cancel();
    }

    @Inject(method = "unbindAttachment", at = @At("HEAD"), cancellable = true)
    private void radiance$unbind(CallbackInfo ci) {
        VeilFramebufferCompatibility.bindRenderbuffer(0);
        ci.cancel();
    }

    @Inject(method = "free", at = @At("HEAD"), cancellable = true)
    private void radiance$free(CallbackInfo ci) {
        this.id = VeilFramebufferCompatibility.releaseRenderbuffer(this.id);
        ci.cancel();
    }
}
