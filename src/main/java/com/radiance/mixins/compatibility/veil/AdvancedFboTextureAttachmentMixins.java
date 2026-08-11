package com.radiance.mixins.compatibility.veil;

import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.compatibility.veil.VeilFramebufferCompatibility;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.AdvancedFboTextureAttachment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AdvancedFboTextureAttachment.class, remap = false)
public abstract class AdvancedFboTextureAttachmentMixins extends AbstractTexture {

    @Inject(method = "getId", at = @At("HEAD"), cancellable = true)
    private void radiance$getId(CallbackInfoReturnable<Integer> cir) {
        if (this.id == -1) {
            this.id = TextureUtil.generateTextureId();
        }
        cir.setReturnValue(this.id);
    }

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void radiance$create(CallbackInfo ci) {
        VeilFramebufferCompatibility.createTextureAttachment(
            (AdvancedFboTextureAttachment) (Object) this);
        ci.cancel();
    }

    @Inject(method = "setFilter", at = @At("HEAD"), cancellable = true)
    private void radiance$setFilter(boolean blur, boolean mipmap, CallbackInfo ci) {
        VeilFramebufferCompatibility.setTextureFilter(
            (AdvancedFboTextureAttachment) (Object) this, blur, mipmap);
        ci.cancel();
    }

    @Inject(method = "attach", at = @At("HEAD"), cancellable = true)
    private void radiance$attach(AdvancedFbo framebuffer, int attachment, CallbackInfo ci) {
        AdvancedFboTextureAttachment self = (AdvancedFboTextureAttachment) (Object) this;
        VeilFramebufferCompatibility.attachTexture(framebuffer, self.getAttachmentType(),
            attachment, self.getId());
        ci.cancel();
    }
}
