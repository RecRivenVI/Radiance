package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.render.RenderCaptureContract;
import com.radiance.client.render.WorldMeshSink;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public abstract class RenderTypeWorldSinkMixins {

    @Inject(method = "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("HEAD"),
        cancellable = true)
    private void radiance$submitWorldMesh(MeshData mesh, CallbackInfo ci) {
        RenderCaptureContract.Scope scope = RenderCaptureContract.currentScope();
        if (scope.kind() != RenderCaptureContract.ScopeKind.WORLD_STAGE
            && scope.kind() != RenderCaptureContract.ScopeKind.DIMENSION_EFFECT) {
            return;
        }
        RenderType renderType = (RenderType) (Object) this;
        WorldMeshSink.Submission submission = WorldMeshSink.submit(renderType, mesh,
            renderType, scope.label());
        if (!submission.consumed()) {
            if (FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER) != 0) {
                // A custom render target owns this draw; the raster bridge replays it there.
                RenderAuditBridge.transition(0L, "FORWARDED", "CUSTOM_TARGET_RASTER",
                    submission.reason(), true);
                return;
            }
            // Rendering to the main target with a non-main output state (for example glint)
            // has no world consumer here. Falling through would reach the raw world-stage
            // rejection and crash, so drop the layer explicitly instead.
            RenderCaptureContract.reportDiscard("RenderType.draw world MeshData",
                submission.reason());
            RenderAuditBridge.transition(0L, "INTENTIONALLY_SKIPPED", "WORLD_MESH",
                submission.reason(), true);
            mesh.close();
            ci.cancel();
            return;
        }
        try {
            if (submission.status() != WorldMeshSink.Status.ACCEPTED) {
                RenderCaptureContract.reportDiscard("RenderType.draw world MeshData",
                    submission.reason());
            }
            RenderAuditBridge.transition(0L,
                submission.status() == WorldMeshSink.Status.ACCEPTED
                    ? "TRANSLATED" : "INTENTIONALLY_SKIPPED",
                "WORLD_MESH", submission.reason(), true);
        } finally {
            mesh.close();
        }
        ci.cancel();
    }
}
