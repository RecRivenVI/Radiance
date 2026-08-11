package com.radiance.mixins.compatibility.veil;

import foundry.veil.api.client.render.shader.program.ShaderUniformCache;
import foundry.veil.impl.client.render.shader.program.ShaderTextureCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ShaderTextureCache.class, remap = false)
public abstract class VeilShaderTextureCacheMixins {

    @Inject(method = "bind", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$keepSamplerBindingsOnCpu(ShaderUniformCache cache, int samplerStart,
        CallbackInfo ci) {
        ci.cancel();
    }
}
