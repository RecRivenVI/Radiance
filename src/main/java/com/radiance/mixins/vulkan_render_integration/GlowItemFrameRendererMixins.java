package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.render.GlowItemFrameEmission;
import com.radiance.client.vertex.PBRMaterialContext;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Translates GlowItemFrame's vanilla lightmap overrides into physical PBR emission. */
@Mixin(ItemFrameRenderer.class)
public abstract class GlowItemFrameRendererMixins {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;renderModel("
            + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/client/resources/model/BakedModel;FFFII)V"))
    private void radiance$emitGlowFrame(ModelBlockRenderer renderer, PoseStack.Pose pose,
        VertexConsumer consumer, BlockState state, BakedModel model, float red, float green,
        float blue, int light, int overlay, Operation<Void> original,
        @Local(argsOnly = true) ItemFrame frame) {
        float emission = isGlowFrame(frame) ? GlowItemFrameEmission.FRAME : 0.0F;
        try (var ignored = PBRMaterialContext.pushAlbedoEmission(emission)) {
            original.call(renderer, pose, consumer, state, model, red, green, blue, light, overlay);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic("
            + "Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;II"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/world/level/Level;I)V"))
    private void radiance$emitGlowFrameItem(ItemRenderer renderer, ItemStack stack,
        ItemDisplayContext displayContext, int light, int overlay, PoseStack poseStack,
        MultiBufferSource buffers, Level level, int seed, Operation<Void> original,
        @Local(argsOnly = true) ItemFrame frame) {
        float emission = isGlowFrame(frame) ? GlowItemFrameEmission.fromPackedLight(light) : 0.0F;
        try (var ignored = PBRMaterialContext.pushAlbedoEmission(emission)) {
            original.call(renderer, stack, displayContext, light, overlay, poseStack, buffers,
                level, seed);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gui/MapRenderer;render("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/world/level/saveddata/maps/MapId;"
            + "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;ZI)V"))
    private void radiance$emitGlowFrameMap(MapRenderer renderer, PoseStack poseStack,
        MultiBufferSource buffers, MapId mapId, MapItemSavedData mapData, boolean active,
        int light, Operation<Void> original, @Local(argsOnly = true) ItemFrame frame) {
        float emission = isGlowFrame(frame) ? GlowItemFrameEmission.fromPackedLight(light) : 0.0F;
        try (var ignored = PBRMaterialContext.pushAlbedoEmission(emission)) {
            original.call(renderer, poseStack, buffers, mapId, mapData, active, light);
        }
    }

    private static boolean isGlowFrame(ItemFrame frame) {
        return frame.getType() == EntityType.GLOW_ITEM_FRAME;
    }
}
