package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.radiance.audit.FrameProfiler;
import com.radiance.client.vertex.RigidModelCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RigidModelCapture.Draw.class, remap = false)
public abstract class ProfileRigidModelMixin {
    @Shadow private Object sourceOwner;

    @Inject(method = "submit", at = @At("HEAD"))
    private void audit$verify(CallbackInfo ci) {
        com.radiance.audit.RigidModelProbe.check(this);
    }

    @ModifyExpressionValue(
            method = "submit",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/radiance/api/render/LocalModelBatch;selectUnchanged(JLjava/lang/Object;JILcom/radiance/client/texture/TextureTasks$Owner;IDDDILorg/joml/Matrix4fc;Ljava/lang/String;Lcom/radiance/render/contract/DrawStateSnapshot;Lcom/radiance/api/render/RenderSpace;Lcom/radiance/api/render/LocalSurface;I)Z"))
    private boolean audit$unchangedEntity(boolean unchanged) {
        FrameProfiler.persistentEntityMatch(sourceOwner, unchanged);
        return unchanged;
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "submit",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/radiance/client/render/MaterialFaces;captureSnapshot(Lnet/minecraft/client/renderer/RenderType;)Lcom/radiance/render/contract/DrawStateSnapshot;"))
    private com.radiance.render.contract.DrawStateSnapshot audit$faces(
            net.minecraft.client.renderer.RenderType layer,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<
                            com.radiance.render.contract.DrawStateSnapshot>
                    original) {
        try (var attribution = com.radiance.audit.ProducerCensus.rigidFaces(this);
                var census =
                        attribution == null ? null : com.radiance.audit.ProducerCensus.face(layer);
                var ignored =
                        com.radiance.audit.FrameProfiler.span(
                                com.radiance.audit.FrameTimings.Stage.GEOMETRY_FACE_STATE)) {
            return original.call(layer);
        }
    }
}
