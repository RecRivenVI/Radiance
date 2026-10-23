package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.audit.FrameProfiler;
import com.radiance.client.vertex.PBRVertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts completed allocations, not vertex calls or repeated JNI reads of persistent meshes. */
@Mixin(value = PBRVertexConsumer.class, remap = false)
public abstract class ProfilePbrMeshMixin {
    @Inject(method = "endNullable", at = @At("RETURN"))
    private void generated(CallbackInfoReturnable<MeshData> result) {
        FrameProfiler.generatedPbr(result.getReturnValue());
    }
}
