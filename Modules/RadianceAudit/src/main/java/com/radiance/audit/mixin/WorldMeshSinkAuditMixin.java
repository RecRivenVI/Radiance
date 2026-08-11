package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.render.WorldMeshSink;
import com.radiance.audit.WorldMeshSubmissionProbe;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldMeshSink.class, remap = false)
public abstract class WorldMeshSinkAuditMixin {
    @Inject(method = "submit", at = @At("RETURN"), remap = false)
    private static void radianceAudit$recordSubmission(RenderType renderType, MeshData mesh,
        Object sourceOwner, String contentName,
        CallbackInfoReturnable<WorldMeshSink.Submission> cir) {
        WorldMeshSink.StageContext stage = WorldMeshSink.currentStage();
        String scope = stage == null ? "no-stage" : stage.sourceId();
        WorldMeshSubmissionProbe.record(renderType, mesh, cir.getReturnValue(), scope);
    }
}
