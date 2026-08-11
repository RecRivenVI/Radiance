package com.radiance.mixins.compatibility.veil;

import foundry.veil.impl.client.render.shader.block.WrapperShaderBlockImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = WrapperShaderBlockImpl.class, remap = false)
public abstract class VeilWrapperShaderBlockMixins {

    @Inject(method = {"bind", "unbind"}, at = @At("HEAD"), cancellable = true,
        remap = false)
    private void radiance$rejectOpaqueOpenGlBlock(int index, CallbackInfo ci) {
        throw new UnsupportedOperationException(
            "Radiance cannot consume a Veil block backed only by an OpenGL buffer id");
    }
}
