package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.audit.AuditHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BufferUploader.class, priority = 2000)
public abstract class BufferUploaderAuditMixin {
    private static final String SOURCE = "BufferUploader._drawWithShader";

    @Inject(method = "_drawWithShader", at = @At("HEAD"))
    private static void radianceAudit$begin(MeshData mesh, CallbackInfo ci) {
        MeshData.DrawState state = mesh.drawState();
        AuditHooks.enter("BUFFER_DRAW", SOURCE,
            state.mode() + "/" + state.format() + "/indices=" + state.indexCount());
    }

    @Inject(method = "_drawWithShader", at = @At("RETURN"))
    private static void radianceAudit$end(MeshData mesh, CallbackInfo ci) {
        AuditHooks.exit(SOURCE);
    }
}
