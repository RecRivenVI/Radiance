package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilAdvancedFboAccess;
import com.radiance.compatibility.veil.VeilFramebufferCompatibility;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.AdvancedFboAttachment;
import foundry.veil.impl.client.render.framebuffer.AdvancedFboImpl;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvancedFboImpl.class, remap = false)
public abstract class AdvancedFboImplMixins implements VeilAdvancedFboAccess {
    @Shadow protected int id;
    @Shadow @Final protected AdvancedFboAttachment[] colorAttachments;
    @Shadow @Final protected AdvancedFboAttachment depthAttachment;
    @Shadow protected int[] currentDrawBuffers;

    @Inject(method = "free", at = @At("HEAD"), cancellable = true)
    private void radiance$free(CallbackInfo ci) {
        VeilFramebufferCompatibility.free((AdvancedFbo) (Object) this);
        ci.cancel();
    }

    @Override
    public int radiance$getFramebufferId() {
        return this.id;
    }

    @Override
    public void radiance$setFramebufferId(int id) {
        this.id = id;
    }

    @Override
    public AdvancedFboAttachment[] radiance$getColorAttachments() {
        return this.colorAttachments;
    }

    @Override
    public AdvancedFboAttachment radiance$getDepthAttachmentOrNull() {
        return this.depthAttachment;
    }

    @Override
    public void radiance$setCurrentDrawBuffers(int[] buffers) {
        this.currentDrawBuffers = Arrays.copyOf(buffers, buffers.length);
    }
}
