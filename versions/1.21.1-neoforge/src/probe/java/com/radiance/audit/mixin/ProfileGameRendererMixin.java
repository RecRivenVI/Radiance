package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GameRenderer.class, priority = 2100)
public abstract class ProfileGameRendererMixin {
    @WrapMethod(method = "pick(F)V")
    private void audit$pick(float partialTick, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.CAMERA_PICK)) {
            original.call(partialTick);
        }
    }

    @WrapMethod(method = "render")
    private void audit$render(DeltaTracker time, boolean tick, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.GAME_RENDER_OTHER)) {
            original.call(time, tick);
        }
    }

    @WrapMethod(method = "renderLevel")
    private void audit$world(DeltaTracker time, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.WORLD_RENDER_OTHER)) {
            original.call(time);
        }
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/LightTexture;updateLightTexture(F)V"))
    private void audit$worldLightTexture(
            LightTexture texture, float tick, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.WORLD_LIGHT_TEXTURE)) {
            original.call(texture, tick);
        }
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"))
    private void audit$cameraSetup(
            Camera camera,
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean mirrored,
            float tick,
            Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.WORLD_CAMERA_SETUP)) {
            original.call(camera, level, entity, detached, mirrored, tick);
        }
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/GameRenderer;getFov(Lnet/minecraft/client/Camera;FZ)D"))
    private double audit$worldFov(
            GameRenderer renderer,
            Camera camera,
            float tick,
            boolean playerFov,
            Operation<Double> original) {
        try (var ignored = FrameProfiler.span(Stage.WORLD_CAMERA_FOV)) {
            return original.call(renderer, camera, tick, playerFov);
        }
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/GameRenderer;shouldRenderBlockOutline()Z"))
    private boolean audit$outlineCheck(GameRenderer renderer, Operation<Boolean> original) {
        try (var ignored = FrameProfiler.span(Stage.WORLD_OUTLINE_CHECK)) {
            return original.call(renderer);
        }
    }

    @WrapOperation(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void audit$frustumSetup(
            LevelRenderer renderer,
            Vec3 position,
            Matrix4f view,
            Matrix4f projection,
            Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.WORLD_FRUSTUM_SETUP)) {
            original.call(renderer, position, view, projection);
        }
    }
}
