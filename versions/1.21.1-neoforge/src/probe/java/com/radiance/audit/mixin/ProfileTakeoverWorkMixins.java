package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.audit.ProducerCensus;
import com.radiance.client.vertex.BoundPartCapture;
import com.radiance.client.vertex.RigidModelCapture;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.compatibility.vanilla.VanillaEntityReplacement;
import com.radiance.compatibility.vanilla.VanillaLivingReplacement;
import com.radiance.compatibility.vanilla.VanillaModelPose;
import java.util.List;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Observe owned extraction boundaries without modifying the vanilla source qualification. */
public final class ProfileTakeoverWorkMixins {
    private ProfileTakeoverWorkMixins() {}

    @Mixin(value = VanillaEntityReplacement.class, remap = false)
    public abstract static class Entities {
        @WrapMethod(
                method =
                        "render(Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lcom/radiance/client/vertex/StorageVertexConsumerProvider;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
        private static void audit$state(
                EntityRenderDispatcher dispatcher,
                Entity entity,
                float yaw,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider owner,
                MultiBufferSource buffers,
                int light,
                Operation<Void> original) {
            try (var stage = FrameProfiler.span(Stage.ENTITY_SOURCE_STATE);
                    var producer =
                            ProducerCensus.renderer(
                                    "takeover/entity", dispatcher.getRenderer(entity))) {
                original.call(dispatcher, entity, yaw, partial, pose, owner, buffers, light);
            }
        }
    }

    @Mixin(value = VanillaLivingReplacement.class, remap = false)
    public abstract static class Living {
        @WrapMethod(method = "renderOne")
        private static void audit$state(
                EntityRenderDispatcher dispatcher,
                LivingEntity entity,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider owner,
                MultiBufferSource buffers,
                int light,
                Operation<Void> original) {
            try (var stage = FrameProfiler.span(Stage.ENTITY_SOURCE_STATE);
                    var producer =
                            ProducerCensus.renderer(
                                    "takeover/living", dispatcher.getRenderer(entity))) {
                original.call(dispatcher, entity, partial, pose, owner, buffers, light);
            }
        }
    }

    @Mixin(value = VanillaModelPose.class, remap = false)
    public abstract static class Poses {
        @WrapMethod(
                method =
                        "extract(Lnet/minecraft/client/model/Model;Lcom/mojang/blaze3d/vertex/PoseStack;I)Ljava/util/List;")
        private static List<VanillaModelPose.Part> audit$pose(
                Model model,
                PoseStack pose,
                int color,
                Operation<List<VanillaModelPose.Part>> original) {
            try (var stage = FrameProfiler.span(Stage.ENTITY_POSE_EXTRACT)) {
                return original.call(model, pose, color);
            }
        }
    }

    @Mixin(value = BoundPartCapture.class, remap = false)
    public abstract static class Bindings {
        @WrapMethod(method = "submit")
        private void audit$bindings(Operation<Void> original) {
            try (var stage = FrameProfiler.span(Stage.ENTITY_PART_BINDINGS)) {
                original.call();
            }
        }
    }

    @Mixin(value = RigidModelCapture.class, remap = false)
    public abstract static class Packing {
        @WrapMethod(method = "submitAll")
        private static void audit$packing(
                List<RigidModelCapture.Draw> draws, Operation<Void> original) {
            try (var stage = FrameProfiler.span(Stage.ENTITY_BATCH_PACK)) {
                original.call(draws);
            }
        }
    }
}
