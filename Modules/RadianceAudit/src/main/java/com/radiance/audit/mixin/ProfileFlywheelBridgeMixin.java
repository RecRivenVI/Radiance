package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.compatibility.flywheel.FlywheelRenderBridge;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FlywheelRenderBridge.class, remap = false)
public abstract class ProfileFlywheelBridgeMixin {
    @WrapMethod(method = "begin")
    private static FlywheelRenderBridge.Frame audit$begin(LevelRenderer renderer, ClientLevel level,
        RenderBuffers buffers, Matrix4fc view, Matrix4f projection, Camera camera, DeltaTracker delta,
        Operation<FlywheelRenderBridge.Frame> original) {
        try (var ignored = FrameProfiler.span(Stage.FLYWHEEL_BEGIN)) {
            return original.call(renderer, level, buffers, view, projection, camera, delta);
        }
    }
}
