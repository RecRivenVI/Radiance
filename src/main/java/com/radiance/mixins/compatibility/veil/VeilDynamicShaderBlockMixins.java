package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilShaderBlockData;
import com.radiance.compatibility.veil.VeilShaderAdapter;
import foundry.veil.impl.client.render.shader.block.DynamicShaderBlockImpl;
import foundry.veil.impl.client.render.shader.block.ShaderBlockImpl;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = DynamicShaderBlockImpl.class, remap = false)
public abstract class VeilDynamicShaderBlockMixins implements VeilShaderBlockData {

    @Shadow
    @Final
    private BiConsumer<Object, ByteBuffer> serializer;

    @Shadow
    public abstract int getSize();

    @Inject(method = {"bind", "unbind"}, at = @At("HEAD"), cancellable = true,
        remap = false)
    private void radiance$skipOpenGlBlockBinding(int index, CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public ByteBuffer radiance$snapshotBlock() {
        Object value = ((ShaderBlockImpl<?>) (Object) this).getValue();
        return VeilShaderAdapter.snapshotBlock(value, this.getSize(), this.serializer);
    }
}
