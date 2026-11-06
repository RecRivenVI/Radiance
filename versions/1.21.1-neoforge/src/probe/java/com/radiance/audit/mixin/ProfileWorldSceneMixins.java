package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radiance.api.render.BlockEntityPublications;
import com.radiance.api.render.LocalModelBatch;
import com.radiance.api.render.SourceHooks;
import com.radiance.api.render.SourceIntegration;
import com.radiance.api.render.WorldScene;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.client.render.AfterWorldRender;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class ProfileWorldSceneMixins {
    private ProfileWorldSceneMixins() {}

    @Mixin(value = WorldScene.class, remap = false)
    public abstract static class Scene {
        @WrapMethod(method = "beginFrame")
        private static WorldScene.FrameToken audit$beginFrame(
                ClientLevel level, Vec3 cameraOrigin, Operation<WorldScene.FrameToken> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_SCENE_BEGIN)) {
                return original.call(level, cameraOrigin);
            }
        }

        @WrapMethod(method = "flushNativeAuditOutcomes")
        private static void audit$flushOutcomes(Operation<Void> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_AUDIT_OUTCOMES)) {
                original.call();
            }
        }
    }

    @Mixin(value = AfterWorldRender.class, remap = false)
    public abstract static class Deferred {
        @WrapMethod(method = "flush")
        private static void audit$flush(Operation<Void> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_AFTER_RENDER)) {
                original.call();
            }
        }
    }

    @Mixin(value = SourceHooks.class, remap = false)
    public abstract static class Sources {
        @WrapMethod(method = "update")
        private static void audit$update(
                ClientLevel level, Camera camera, float tick, Operation<Void> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_SOURCE_UPDATE)) {
                original.call(level, camera, tick);
            }
        }

        @WrapMethod(method = "begin")
        private static SourceIntegration.Frame audit$begin(
                SourceIntegration.FrameInput input, Operation<SourceIntegration.Frame> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_SOURCE_BEGIN)) {
                return original.call(input);
            }
        }

        @WrapMethod(method = "blockEntities")
        private static void audit$blockEntities(
                ClientLevel level,
                Camera camera,
                BlockEntityRenderDispatcher dispatcher,
                float tick,
                Operation<Void> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_SOURCE_BLOCK_ENTITIES)) {
                original.call(level, camera, dispatcher, tick);
            }
        }

        @WrapMethod(method = "worldEffects")
        private static void audit$effects(
                Camera camera,
                GameRenderer renderer,
                Matrix4f view,
                Matrix4f projection,
                Operation<Void> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_SOURCE_EFFECTS)) {
                original.call(camera, renderer, view, projection);
            }
        }
    }

    @Mixin(value = BlockEntityPublications.class, remap = false)
    public abstract static class BlockEntities {
        @WrapMethod(method = "endFrame")
        private static void audit$publish(Operation<Void> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_BLOCK_ENTITY_PUBLICATION)) {
                original.call();
            }
            FrameProfiler.blockEntityRest(BlockEntityPublications.restCounters());
        }

        @WrapOperation(
                method = "endFrame",
                at =
                        @At(
                                value = "INVOKE",
                                target = "Lcom/radiance/api/render/LocalModelBatch;submit()V"))
        private static void audit$submitResident(LocalModelBatch batch, Operation<Void> original) {
            try (var ignored = FrameProfiler.span(Stage.WORLD_BLOCK_ENTITY_RESIDENT_SUBMIT)) {
                original.call(batch);
            }
        }
    }
}
