package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = LevelRenderer.class, priority = 2100)
public abstract class ProfileLevelSetupMixin {
    @WrapMethod(method = "renderLevel")
    private void audit$level(DeltaTracker delta, boolean outline, Camera camera, GameRenderer renderer,
        LightTexture light, Matrix4f view, Matrix4f projection, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.LEVEL_RENDER_OTHER)) {
            original.call(delta, outline, camera, renderer, light, view, projection);
        }
    }
    @WrapMethod(method = "setupRender")
    private void audit$setup(Camera camera, Frustum frustum, boolean captured, boolean spectator,
        Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.LEVEL_SETUP)) { original.call(camera, frustum, captured, spectator); }
    }
}
