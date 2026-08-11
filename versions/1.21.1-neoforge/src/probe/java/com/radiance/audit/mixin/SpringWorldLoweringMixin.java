package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.radiance.audit.SpringWorldProbe;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.compatibility.simulated.SpringDrawContract;
import com.radiance.compatibility.simulated.SpringWorldLowering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tracks the explicit source-grounded physical spring consumer, not all PBR draws. */
@Mixin(value = SpringWorldLowering.class, remap = false)
public abstract class SpringWorldLoweringMixin {
    @Inject(method = "createConsumer", at = @At("RETURN"), remap = false)
    private static void audit$springConsumer(
            ByteBufferBuilder allocator,
            SpringDrawContract.Draw draw,
            CallbackInfoReturnable<PBRVertexConsumer> cir) {
        SpringWorldProbe.consumer(cir.getReturnValue());
    }
}
