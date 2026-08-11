package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.api.render.WorldLines;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class WorldLineBufferSourceMixins {
    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void radiance$captureLine(
            RenderType layer, CallbackInfoReturnable<VertexConsumer> cir) {
        if ((Object) this instanceof StorageVertexConsumerProvider) return;
        var captured = WorldLines.getBuffer(this, layer);
        if (captured != null) cir.setReturnValue(captured);
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"))
    private void radiance$flushLine(RenderType layer, CallbackInfo ci) {
        WorldLines.flush(this, layer);
    }

    @Inject(method = "endBatch()V", at = @At("HEAD"))
    private void radiance$flushLines(CallbackInfo ci) {
        WorldLines.flush(this, null);
    }
}
