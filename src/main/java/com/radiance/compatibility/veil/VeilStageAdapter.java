package com.radiance.compatibility.veil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.render.AfterWorldRender;
import com.radiance.client.render.WorldMeshSink;
import com.radiance.compatibility.simulated.SimulatedStaffCapture;
import com.radiance.client.render.WorldRasterPass;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Owns Veil render-stage relocation across the native world/UI boundary. */
public final class VeilStageAdapter {
    private VeilStageAdapter() {
    }

    public static VeilRenderLevelStageEvent pathTraceStaff(VeilRenderLevelStageEvent original) {
        return (WorldGeometryStageListener) (stage, renderer, buffers, matrices, view, projection,
                tick, delta, camera, frustum) -> {
            if (WorldMeshSink.currentStage() == null ||
                WorldMeshSink.currentStage().targetKind() != WorldMeshSink.TargetKind.DEFAULT_WORLD ||
                FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER) != 0 ||
                stage != VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                original.onRenderLevelStage(stage, renderer, buffers, matrices, view, projection,
                    tick, delta, camera, frustum);
                return;
            }
            try (var capture = new SimulatedStaffCapture()) {
                original.onRenderLevelStage(stage, renderer, capture.locks, matrices, view,
                    projection, tick, delta, camera, frustum);
                capture.submit();
            }
        };
    }

    public static boolean deferFixedStage(RenderLevelStageEvent event, Runnable replay) {
        if (!shouldDeferWorldDraw()) {
            return false;
        }
        int order = event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL ? 200 : 0;
        WorldRasterPass.defer(order, "veil:fixed/" + event.getStage(),
            event.getModelViewMatrix(), event.getProjectionMatrix(), replay);
        return true;
    }

    public static VeilRenderLevelStageEvent wrap(VeilRenderLevelStageEvent listener) {
        if (listener instanceof WorldGeometryStageListener) return listener;
        return (stage, renderer, buffers, matrices, view, projection, tick, delta, camera,
            frustum) -> {
            if (!shouldDeferWorldDraw()) {
                listener.onRenderLevelStage(stage, renderer, buffers, matrices, view, projection,
                    tick, delta, camera, frustum);
                return;
            }
            PoseStack pose = new PoseStack();
            pose.last().pose().set(matrices.toPoseStack().last().pose());
            pose.last().normal().set(matrices.toPoseStack().last().normal());
            Matrix4f savedView = new Matrix4f(view);
            Matrix4f savedProjection = new Matrix4f(projection);
            WorldRasterPass.defer("veil:stage/" + stage, savedView, savedProjection,
                () -> listener.onRenderLevelStage(stage, renderer, buffers,
                    VeilRenderBridge.create(pose), savedView, savedProjection, tick, delta,
                    camera, frustum));
        };
    }

    private static boolean shouldDeferWorldDraw() {
        return AfterWorldRender.isActive()
            && FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER) == 0;
    }
}
