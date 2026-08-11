package com.radiance.mixins.compatibility.simulated;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.compatibility.simulated.SimulatedFramebufferRecovery;
import com.radiance.compatibility.simulated.SimulatedRenderRecovery;
import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.end_sea.EndSeaShadowRenderer", remap = false)
public abstract class SimulatedEndSeaShadowMixins {
    @Shadow private static boolean isRenderingShadowMap;

    @WrapMethod(method = "renderShadowMap")
    private static void radiance$restoreShadowState(VeilRenderLevelStageEvent.Stage stage,
        LevelRenderer renderer, MultiBufferSource.BufferSource buffers, MatrixStack matrices,
        Matrix4fc frustumMatrix, Matrix4fc projection, int tick, DeltaTracker delta,
        Camera camera, Frustum frustum, Operation<Void> original) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) {
            original.call(stage, renderer, buffers, matrices, frustumMatrix,
                projection, tick, delta, camera, frustum);
            return;
        }
        SimulatedRenderRecovery recovery = new SimulatedRenderRecovery();
        boolean previous = isRenderingShadowMap;
        recovery.add(() -> isRenderingShadowMap = previous);
        SimulatedFramebufferRecovery.capture(recovery);
        recovery.run(() -> original.call(stage, renderer, buffers, matrices, frustumMatrix,
            projection, tick, delta, camera, frustum));
    }
}
