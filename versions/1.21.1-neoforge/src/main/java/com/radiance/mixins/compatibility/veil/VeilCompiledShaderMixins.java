package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilShaderAdapter;
import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = CompiledShader.class, remap = false)
public abstract class VeilCompiledShaderMixins {

    @Shadow
    public abstract int id();

    @Inject(method = "free", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$releaseCapturedStage(CallbackInfo ci) {
        VeilShaderAdapter.releaseStage(this.id());
        ci.cancel();
    }
}
