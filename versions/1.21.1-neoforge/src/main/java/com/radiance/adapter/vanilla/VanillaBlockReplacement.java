package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.api.render.RenderSpace;
import com.radiance.client.vertex.*;
import com.radiance.mixin_related.SourceCompatibility;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** World-only dispatch; GUI item/preview renderers keep their original execution. */
public final class VanillaBlockReplacement {
    private static final ThreadLocal<BlockEntity> WORLD_FALLBACK = new ThreadLocal<>();
    private static final ClassValue<Boolean> DISTANCE_ONLY =
            new ClassValue<>() {
                @Override
                protected Boolean computeValue(Class<?> type) {
                    try {
                        return type.getMethod("shouldRender", BlockEntity.class, Vec3.class)
                                        .getDeclaringClass()
                                == BlockEntityRenderer.class;
                    } catch (NoSuchMethodException e) {
                        throw new IllegalStateException(
                                "Missing block entity renderer contract", e);
                    }
                }
            };

    private VanillaBlockReplacement() {}

    public interface Source {
        default boolean radiance$supportsBlock(BlockEntity entity) {
            return true;
        }

        /** Immutable equality key for sources that can select their last complete publication. */
        default Object radiance$stableInputs(BlockEntity entity, float partial) {
            return null;
        }

        /**
         * True when every change of the stable inputs is announced by a block event, a block state
         * change, a data packet, {@code setChanged} or removal. A source that can change on its own
         * tick must keep the default.
         */
        default boolean radiance$restable(BlockEntity entity) {
            return false;
        }

        void radiance$collect(
                BlockEntity entity,
                float partial,
                PoseStack pose,
                StorageVertexConsumerProvider buffers,
                BoundPartCapture requests,
                int light,
                int overlay);
    }

    public static boolean eligible(BlockEntityRenderer<?> renderer) {
        return PartModelCapture.ENABLED
                && renderer instanceof Source
                && PartModelCapture.sourceCompatible()
                && SourceCompatibility.producer(renderer.getClass())
                && SourceCompatibility.compatible(SourceCompatibility.Source.STATIC_STATE);
    }

    public static boolean eligible(BlockEntityRenderer<?> renderer, BlockEntity entity) {
        return eligible(renderer) && ((Source) renderer).radiance$supportsBlock(entity);
    }

    private record Inputs(
            Object renderer,
            Object state,
            Object content,
            RenderSpace space,
            Object origin,
            boolean restable)
            implements com.radiance.api.render.BlockEntityPublications.StableInputs {}

    public static Object stableInputs(
            BlockEntityRenderer<?> renderer,
            BlockEntity entity,
            float partial,
            RenderSpace.Placement placement) {
        if (!eligible(renderer, entity) || !valid(entity) || !distanceOnly(renderer)) return null;
        Object content = ((Source) renderer).radiance$stableInputs(entity, partial);
        return content == null
                ? null
                : new Inputs(
                        renderer,
                        entity.getBlockState(),
                        content,
                        placement == null ? null : placement.space(),
                        placement == null ? null : placement.origin(),
                        placement == null && ((Source) renderer).radiance$restable(entity));
    }

    public static boolean distanceOnly(BlockEntityRenderer<?> renderer) {
        return DISTANCE_ONLY.get(renderer.getClass())
                || renderer.getClass() == BeaconRenderer.class
                        && SourceCompatibility.producer(BeaconRenderer.class);
    }

    public static boolean valid(BlockEntity entity) {
        return !entity.isRemoved()
                && entity.hasLevel()
                && entity.getType().isValid(entity.getBlockState());
    }

    public static void renderWorldFallback(
            BlockEntityRenderDispatcher dispatcher,
            BlockEntity entity,
            float partial,
            PoseStack pose,
            MultiBufferSource buffers) {
        var parent = WORLD_FALLBACK.get();
        WORLD_FALLBACK.set(entity);
        try {
            dispatcher.render(entity, partial, pose, buffers);
        } finally {
            if (parent == null) WORLD_FALLBACK.remove();
            else WORLD_FALLBACK.set(parent);
        }
    }

    public static boolean bypassFallbackDistance(
            BlockEntityRenderer<?> renderer, BlockEntity entity) {
        return WORLD_FALLBACK.get() == entity && distanceOnly(renderer);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(
            BlockEntity entity,
            float partial,
            PoseStack pose,
            StorageVertexConsumerProvider buffers) {
        render(
                entity,
                partial,
                pose,
                buffers,
                Minecraft.getInstance().getBlockEntityRenderDispatcher().camera.getPosition());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(
            BlockEntity entity,
            float partial,
            PoseStack pose,
            StorageVertexConsumerProvider buffers,
            net.minecraft.world.phys.Vec3 cameraPosition) {
        BlockEntityRenderer renderer =
                Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
        if (!valid(entity)
                || !distanceOnly(renderer) && !renderer.shouldRender(entity, cameraPosition))
            return;
        buffers.declaredParts(System.identityHashCode(entity), renderer.getClass().getName());
        buffers.sourceOwner = entity;
        try (var evaluation = VanillaModelPose.source(entity)) {
            var requests = new BoundPartCapture(buffers);
            ((Source) renderer)
                    .radiance$collect(
                            entity,
                            partial,
                            pose,
                            buffers,
                            requests,
                            LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos()),
                            OverlayTexture.NO_OVERLAY);
            requests.submit();
        }
    }

    public static void add(
            List<VanillaModelPose.Part> parts,
            Material material,
            Function<net.minecraft.resources.ResourceLocation, RenderType> factory,
            StorageVertexConsumerProvider buffers,
            BoundPartCapture requests,
            int light,
            int overlay) {
        var type = material.renderType(factory);
        requests.add(parts, type, material.sprite().wrap(buffers.getBuffer(type)), light, overlay);
    }
}
