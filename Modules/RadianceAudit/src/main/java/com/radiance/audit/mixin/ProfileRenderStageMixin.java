package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ClientHooks.class, remap = false)
public abstract class ProfileRenderStageMixin {
    @WrapMethod(method = "dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V")
    private static void audit$stage(RenderLevelStageEvent.Stage stage, LevelRenderer renderer,
        PoseStack pose, Matrix4f view, Matrix4f projection, int tick, Camera camera, Frustum frustum,
        Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.RENDER_STAGE_CALLBACKS)) {
            original.call(stage, renderer, pose, view, projection, tick, camera, frustum);
        }
    }
    @WrapMethod(method = "dispatchRenderStage(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V")
    private static void audit$layer(RenderType type, LevelRenderer renderer, Matrix4f view,
        Matrix4f projection, int tick, Camera camera, Frustum frustum, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.RENDER_STAGE_CALLBACKS)) {
            original.call(type, renderer, view, projection, tick, camera, frustum);
        }
    }
}
