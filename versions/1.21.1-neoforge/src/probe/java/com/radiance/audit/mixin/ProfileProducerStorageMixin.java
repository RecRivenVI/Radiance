package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.audit.ProducerCensus;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StorageVertexConsumerProvider.class, remap = false)
public abstract class ProfileProducerStorageMixin {
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "getBuffer",
            at = @At(value = "NEW", target = "(I)Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;"))
    private com.mojang.blaze3d.vertex.ByteBufferBuilder audit$allocation(
            int size,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<
                            com.mojang.blaze3d.vertex.ByteBufferBuilder>
                    original) {
        try (var stage =
                com.radiance.audit.FrameProfiler.span(
                        com.radiance.audit.FrameTimings.Stage.GEOMETRY_ALLOCATE)) {
            ProducerCensus.captureAllocation(size);
            return original.call(size);
        }
    }

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void audit$owner(RenderType type, CallbackInfoReturnable<VertexConsumer> callback) {
        ProducerCensus.provider(this);
    }

    @Inject(method = "takeRigidModels", at = @At("RETURN"))
    private void audit$rigid(
            CallbackInfoReturnable<
                            java.util.List<com.radiance.client.vertex.RigidModelCapture.Draw>>
                    callback) {
        ProducerCensus.rigidProduced(this, callback.getReturnValue());
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "captureDrawState")
    private com.radiance.render.contract.DrawStateSnapshot audit$drawState(
            RenderType layer,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<
                            com.radiance.render.contract.DrawStateSnapshot>
                    original) {
        try (var owner = ProducerCensus.providerFaces(this);
                var census = ProducerCensus.face(layer);
                var ignored =
                        com.radiance.audit.FrameProfiler.span(
                                com.radiance.audit.FrameTimings.Stage.GEOMETRY_FACE_STATE)) {
            return original.call(layer);
        }
    }
}
