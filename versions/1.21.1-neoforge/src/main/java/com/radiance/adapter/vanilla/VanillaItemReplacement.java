package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.MatrixUtil;
import com.radiance.client.render.RasterPreviewScope;
import com.radiance.client.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/**
 * Published JSON model requests. Dynamic model selection remains the original getModel calculation.
 */
public final class VanillaItemReplacement {
    private VanillaItemReplacement() {}

    public static boolean supportsWorldStack(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack)
                != net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.DEFAULT)
            return false;
        var model =
                net.minecraft.client.Minecraft.getInstance()
                        .getItemRenderer()
                        .getItemModelShaper()
                        .getItemModel(stack);
        return model.isCustomRenderer()
                ? VanillaBuiltinItemRequests.supports(stack)
                : BakedModelTakeover.declared(model);
    }

    public static boolean eligible(
            ItemStack stack,
            ItemDisplayContext context,
            MultiBufferSource buffers,
            BakedModel model) {
        return context != ItemDisplayContext.GUI
                && !RasterPreviewScope.active()
                && !stack.isEmpty()
                && net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack)
                        == net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
                                .DEFAULT
                && buffers instanceof StorageVertexConsumerProvider storage
                && storage.rigidBakedEnabled()
                && com.radiance.mixin_related.SourceCompatibility.producer(model.getClass())
                && (model.isCustomRenderer()
                        ? VanillaBuiltinItemRequests.supports(stack)
                        : BakedModelTakeover.declared(model));
    }

    public static void livingItem(
            net.minecraft.world.entity.LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext context,
            boolean left,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay,
            int seed) {
        if (stack.isEmpty()) return;
        var renderer = net.minecraft.client.Minecraft.getInstance().getItemRenderer();
        var model = renderer.getModel(stack, entity.level(), entity, seed);
        if (eligible(stack, context, buffers, model))
            render(stack, context, left, pose, buffers, light, overlay, model);
        else renderer.render(stack, context, left, pose, buffers, light, overlay, model);
    }

    public static void worldItem(
            net.minecraft.world.level.Level level,
            ItemStack stack,
            ItemDisplayContext context,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay,
            int seed) {
        if (stack.isEmpty()) return;
        var renderer = net.minecraft.client.Minecraft.getInstance().getItemRenderer();
        var model = renderer.getModel(stack, level, null, seed);
        if (eligible(stack, context, buffers, model))
            render(stack, context, false, pose, buffers, light, overlay, model);
        else renderer.render(stack, context, false, pose, buffers, light, overlay, model);
    }

    public static void render(
            ItemStack stack,
            ItemDisplayContext context,
            boolean left,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay,
            BakedModel model) {
        pose.pushPose();
        boolean flat = context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED;
        if (flat && (stack.is(Items.TRIDENT) || stack.is(Items.SPYGLASS)))
            model =
                    net.minecraft.client.Minecraft.getInstance()
                            .getItemRenderer()
                            .getItemModelShaper()
                            .getModelManager()
                            .getModel(
                                    net.minecraft.client.resources.model.ModelResourceLocation
                                            .inventory(
                                                    net.minecraft.core.registries.BuiltInRegistries
                                                            .ITEM
                                                            .getKey(stack.getItem())));
        model =
                net.neoforged.neoforge.client.ClientHooks.handleCameraTransforms(
                        pose, model, context, left);
        pose.translate(-.5F, -.5F, -.5F);
        if (model.isCustomRenderer() || stack.is(Items.TRIDENT) && !flat) {
            if (buffers instanceof StorageVertexConsumerProvider)
                VanillaBuiltinItemRequests.collect(stack, pose, buffers, light, overlay);
            else
                // Routed outputs (including outlines) do not supply a part-request owner.
                // Keep their original consumer routing instead of casting away that contract.
                net.minecraft.client.Minecraft.getInstance()
                        .getItemRenderer()
                        .getBlockEntityRenderer()
                        .renderByItem(stack, context, pose, buffers, light, overlay);
            pose.popPose();
            return;
        }
        boolean direct = true;
        if (!context.firstPerson() && stack.getItem() instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            direct =
                    !(block instanceof HalfTransparentBlock)
                            && !(block instanceof StainedGlassPaneBlock);
        }
        for (var pass : model.getRenderPasses(stack, direct))
            for (var type : pass.getRenderTypes(stack, direct)) {
                VertexConsumer out;
                if ((stack.is(ItemTags.COMPASSES) || stack.is(Items.CLOCK)) && stack.hasFoil()) {
                    var matrix = pose.last().copy();
                    if (context.firstPerson()) MatrixUtil.mulComponentWise(matrix.pose(), .75F);
                    out = ItemRenderer.getCompassFoilBuffer(buffers, type, matrix);
                } else
                    out =
                            direct
                                    ? ItemRenderer.getFoilBufferDirect(
                                            buffers, type, true, stack.hasFoil())
                                    : ItemRenderer.getFoilBuffer(
                                            buffers, type, true, stack.hasFoil());
                try (var material =
                        com.radiance.client.render.HandItemMaterialResolver.enter(stack)) {
                    BakedModelTakeover.request(pass, stack, light, overlay, pose.last(), out);
                }
            }
        pose.popPose();
    }
}
