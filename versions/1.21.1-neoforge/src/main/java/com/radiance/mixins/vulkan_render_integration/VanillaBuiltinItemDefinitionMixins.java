package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.*;
import com.radiance.compatibility.vanilla.*;
import java.util.Map;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import org.spongepowered.asm.mixin.*;

@Mixin(BlockEntityWithoutLevelRenderer.class)
public abstract class VanillaBuiltinItemDefinitionMixins
        implements VanillaBuiltinItemRequests.Source {
    @Shadow @Final private static ShulkerBoxBlockEntity[] SHULKER_BOXES;
    @Shadow @Final private static ShulkerBoxBlockEntity DEFAULT_SHULKER_BOX;
    @Shadow @Final private BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    @Shadow @Final private ChestBlockEntity chest;
    @Shadow @Final private ChestBlockEntity trappedChest;
    @Shadow @Final private EnderChestBlockEntity enderChest;
    @Shadow @Final private BannerBlockEntity banner;
    @Shadow @Final private BedBlockEntity bed;
    @Shadow @Final private ConduitBlockEntity conduit;
    @Shadow @Final private DecoratedPotBlockEntity decoratedPot;
    @Shadow private ShieldModel shieldModel;
    @Shadow private TridentModel tridentModel;
    @Shadow private Map<SkullBlock.Type, SkullModelBase> skullModels;

    @Unique
    private BlockEntity radiance$itemBlock(ItemStack stack, boolean update) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        var block = item.getBlock();
        var state = block.defaultBlockState();
        if (block instanceof AbstractBannerBlock source) {
            if (update) banner.fromItem(stack, source.getColor());
            return banner;
        }
        if (block instanceof BedBlock source) {
            if (update) bed.setColor(source.getColor());
            return bed;
        }
        if (state.is(Blocks.CONDUIT)) return conduit;
        if (state.is(Blocks.CHEST)) return chest;
        if (state.is(Blocks.ENDER_CHEST)) return enderChest;
        if (state.is(Blocks.TRAPPED_CHEST)) return trappedChest;
        if (state.is(Blocks.DECORATED_POT)) {
            if (update) decoratedPot.setFromItem(stack);
            return decoratedPot;
        }
        if (block instanceof ShulkerBoxBlock) {
            var color = ShulkerBoxBlock.getColorFromItem(item);
            return color == null ? DEFAULT_SHULKER_BOX : SHULKER_BOXES[color.getId()];
        }
        return null;
    }

    public boolean radiance$supportsItem(ItemStack stack) {
        if (stack.is(Items.TRIDENT)) return VanillaModelPose.supports(tridentModel);
        if (stack.is(Items.SHIELD)) return VanillaModelPose.supports(shieldModel);
        if (stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof AbstractSkullBlock skull)
            return VanillaModelPose.supports(skullModels.get(skull.getType()));
        var block = radiance$itemBlock(stack, false);
        return block != null
                && VanillaBlockReplacement.eligible(
                        blockEntityRenderDispatcher.getRenderer(block), block);
    }

    public void radiance$collectItem(
            ItemStack stack,
            PoseStack pose,
            StorageVertexConsumerProvider buffers,
            BoundPartCapture requests,
            int light,
            int overlay) {
        if (stack.getItem() instanceof BlockItem item) {
            if (item.getBlock() instanceof AbstractSkullBlock skull) {
                var profile = stack.get(DataComponents.PROFILE);
                if (profile != null && !profile.isResolved()) {
                    stack.remove(DataComponents.PROFILE);
                    profile.resolve()
                            .thenAcceptAsync(
                                    value -> stack.set(DataComponents.PROFILE, value),
                                    net.minecraft.client.Minecraft.getInstance());
                    profile = null;
                }
                VanillaSkullRequests.add(
                        null,
                        180,
                        0,
                        pose,
                        buffers,
                        requests,
                        light,
                        skullModels.get(skull.getType()),
                        SkullBlockRenderer.getRenderType(skull.getType(), profile));
            } else {
                var block = radiance$itemBlock(stack, true);
                ((VanillaBlockReplacement.Source) blockEntityRenderDispatcher.getRenderer(block))
                        .radiance$collect(block, 0, pose, buffers, requests, light, overlay);
            }
        } else if (stack.is(Items.SHIELD)) {
            var patterns =
                    stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
            var dye = stack.get(DataComponents.BASE_COLOR);
            boolean patterned = !patterns.layers().isEmpty() || dye != null;
            var material = patterned ? ModelBakery.SHIELD_BASE : ModelBakery.NO_PATTERN_SHIELD;
            pose.pushPose();
            pose.scale(1, -1, -1);
            var type = shieldModel.renderType(material.atlasLocation());
            var out =
                    material.sprite()
                            .wrap(
                                    ItemRenderer.getFoilBufferDirect(
                                            buffers, type, true, stack.hasFoil()));
            requests.add(
                    VanillaModelPose.extract(shieldModel.handle(), pose, -1),
                    type,
                    out,
                    light,
                    overlay);
            if (patterned)
                VanillaBannerRequests.patterns(
                        shieldModel.plate(),
                        material,
                        false,
                        dye == null ? DyeColor.WHITE : dye,
                        patterns,
                        pose,
                        buffers,
                        requests,
                        light,
                        overlay,
                        stack.hasFoil());
            else
                requests.add(
                        VanillaModelPose.extract(shieldModel.plate(), pose, -1),
                        type,
                        out,
                        light,
                        overlay);
            pose.popPose();
        } else if (stack.is(Items.TRIDENT)) {
            pose.pushPose();
            pose.scale(1, -1, -1);
            var type = tridentModel.renderType(TridentModel.TEXTURE);
            requests.add(
                    VanillaModelPose.extract(tridentModel, pose, -1),
                    type,
                    ItemRenderer.getFoilBufferDirect(buffers, type, false, stack.hasFoil()),
                    light,
                    overlay);
            pose.popPose();
        }
    }
}
