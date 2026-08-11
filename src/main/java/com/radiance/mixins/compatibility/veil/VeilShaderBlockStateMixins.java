package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilShaderAdapter;
import foundry.veil.api.client.render.shader.block.ShaderBlock;
import foundry.veil.impl.client.render.pipeline.VeilShaderBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = VeilShaderBlockState.class, remap = false)
public abstract class VeilShaderBlockStateMixins {

    @Inject(method = "bind(Ljava/lang/CharSequence;Lfoundry/veil/api/client/render/shader/block/ShaderBlock;)V",
        at = @At("RETURN"), remap = false)
    private void radiance$captureBlock(CharSequence name, ShaderBlock<?> block, CallbackInfo ci) {
        VeilShaderAdapter.bindBlock(name, block);
    }

    @Inject(method = "unbind", at = @At("HEAD"), remap = false)
    private void radiance$releaseBlock(ShaderBlock<?> block, CallbackInfo ci) {
        VeilShaderAdapter.unbindBlock(block);
    }
}
