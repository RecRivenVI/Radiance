package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilShaderUniformData;
import foundry.veil.impl.client.render.shader.uniform.ShaderUniformImpl;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ShaderUniformImpl.class, remap = false)
public abstract class VeilShaderUniformImplMixins implements VeilShaderUniformData {

    @Shadow
    private ByteBuffer value;

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$keepUniformOnCpu(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "uploadMatrix", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$keepMatrixOnCpu(boolean transpose, CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public ByteBuffer radiance$getUniformBytes() {
        if (this.value == null) {
            return ByteBuffer.allocate(0);
        }
        return this.value.duplicate().order(this.value.order()).clear();
    }
}
