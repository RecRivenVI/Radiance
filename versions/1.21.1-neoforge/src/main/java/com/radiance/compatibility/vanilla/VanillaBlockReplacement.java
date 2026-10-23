package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.*;
import com.radiance.mixin_related.SourceCompatibility;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.entity.BlockEntity;

/** World-only dispatch; GUI item/preview renderers keep their original execution. */
public final class VanillaBlockReplacement {
    private VanillaBlockReplacement() {}

    public interface Source {
        default boolean radiance$supportsBlock(BlockEntity entity) {
            return true;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void render(
            BlockEntity entity,
            float partial,
            PoseStack pose,
            StorageVertexConsumerProvider buffers) {
        BlockEntityRenderer renderer =
                Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
        if (!entity.hasLevel()
                || !entity.getType().isValid(entity.getBlockState())
                || !renderer.shouldRender(
                        entity,
                        Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition()))
            return;
        buffers.declaredParts(System.identityHashCode(entity), renderer.getClass().getName());
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
