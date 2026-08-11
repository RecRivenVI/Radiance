package com.radiance.mixins.compatibility.veil;

import foundry.veil.api.client.render.shader.texture.ShaderTextureSource;
import foundry.veil.impl.client.render.shader.program.ShaderProgramImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = ShaderProgramImpl.ShaderTexture.class, remap = false)
public abstract class VeilShaderTextureMixins {

    @Inject(method = "create", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$createWithoutSamplerObject(ShaderTextureSource source,
        CallbackInfoReturnable<ShaderProgramImpl.ShaderTexture> cir) {
        cir.setReturnValue(new ShaderProgramImpl.ShaderTexture(source, null));
    }
}
