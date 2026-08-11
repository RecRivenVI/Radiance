package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.BakedModelTakeover;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Standard baked block definition; tint and source render types remain current-state inputs. */
public final class VanillaBlockModelRequests {
    private VanillaBlockModelRequests() {}

    public static boolean supports(BlockState state) {
        return state.getRenderShape() == RenderShape.INVISIBLE
                || state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED
                        && VanillaBuiltinItemRequests.supports(
                                new net.minecraft.world.item.ItemStack(state.getBlock()))
                || state.getRenderShape() == RenderShape.MODEL
                        && BakedModelTakeover.declared(
                                Minecraft.getInstance().getBlockRenderer().getBlockModel(state));
    }

    public static void add(
            BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;
        if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
            if (buffers instanceof com.radiance.client.vertex.StorageVertexConsumerProvider)
                VanillaBuiltinItemRequests.collect(
                        new net.minecraft.world.item.ItemStack(state.getBlock()),
                        pose,
                        buffers,
                        light,
                        overlay);
            else
                Minecraft.getInstance()
                        .getBlockRenderer()
                        .renderSingleBlock(state, pose, buffers, light, overlay);
            return;
        }
        var mc = Minecraft.getInstance();
        var model = mc.getBlockRenderer().getBlockModel(state);
        int color = mc.getBlockColors().getColor(state, null, null, 0);
        for (var type : model.getRenderTypes(state, RandomSource.create(42), ModelData.EMPTY))
            BakedModelTakeover.requestBlock(
                    model,
                    ((color >> 16) & 255) / 255F,
                    ((color >> 8) & 255) / 255F,
                    (color & 255) / 255F,
                    light,
                    overlay,
                    pose.last(),
                    buffers.getBuffer(
                            net.neoforged.neoforge.client.RenderTypeHelper.getEntityRenderType(
                                    type, false)));
    }

    public static void outline(
            BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        BakedModelTakeover.requestBlock(
                model,
                0,
                0,
                0,
                light,
                overlay,
                pose.last(),
                buffers.getBuffer(
                        RenderType.outline(
                                net.minecraft.client.renderer.texture.TextureAtlas
                                        .LOCATION_BLOCKS)));
    }
}
