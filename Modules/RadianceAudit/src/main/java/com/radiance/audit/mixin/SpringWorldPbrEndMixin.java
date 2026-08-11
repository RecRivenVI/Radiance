package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.audit.SpringWorldProbe;
import com.radiance.client.vertex.PBRVertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Checks the actual finished world mesh bytes before native PT conversion. */
@Mixin(value = PBRVertexConsumer.class, remap = false)
public abstract class SpringWorldPbrEndMixin {
    @Inject(method = "endNullable", at = @At("RETURN"), remap = false)
    private void audit$finishedSpring(CallbackInfoReturnable<MeshData> cir) {
        SpringWorldProbe.finished((PBRVertexConsumer) (Object) this, cir.getReturnValue());
    }
}
