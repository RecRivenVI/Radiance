package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.render.RenderCaptureContract;
import com.radiance.client.render.WorldMeshSink;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Brackets NeoForge's synchronous RenderLevelStageEvent dispatch itself.
 * Bracketing the event-bus post, rather than observing the event from another
 * listener, keeps the scope active for every listener and closes it on both
 * normal return and exception.
 */
@Mixin(value = ClientHooks.class, remap = false)
public abstract class RenderLevelStageCompatibilityMixins {

    @Redirect(
        method = "dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;"
            + "Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;"
            + "Lnet/minecraft/client/renderer/culling/Frustum;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)"
                + "Lnet/neoforged/bus/api/Event;"),
        remap = false)
    private static Event radiance$dispatchRenderStageWithScope(IEventBus eventBus,
        Event event) {
        if (!(event instanceof RenderLevelStageEvent stage)) {
            return eventBus.post(event);
        }

        String label = "RenderLevelStageEvent." + stage.getStage();
        try (WorldMeshSink.StageToken worldStage = WorldMeshSink.enterStage(
                 WorldMeshSink.StageKey.from(stage.getStage().toString()),
                 WorldMeshSink.CoordinateSpace.CAMERA_SHIFT,
                 net.minecraft.world.phys.Vec3.ZERO,
                 WorldMeshSink.TargetKind.DEFAULT_WORLD, label);
             RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
                 RenderCaptureContract.ScopeKind.WORLD_STAGE, label)) {
            Event result = eventBus.post(event);
            worldStage.commit();
            return result;
        }
    }
}
