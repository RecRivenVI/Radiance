package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.render.HandItemMaterialResolver;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class HandItemMaterialMixins {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists("
            + "Lnet/minecraft/client/resources/model/BakedModel;"
            + "Lnet/minecraft/world/item/ItemStack;II"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void radiance$inheritWorldBlockMaterial(ItemRenderer renderer, BakedModel model,
        ItemStack stack, int light, int overlay, PoseStack poseStack, VertexConsumer consumer,
        Operation<Void> original) {
        try (var ignored = HandItemMaterialResolver.enter(stack)) {
            original.call(renderer, model, stack, light, overlay, poseStack, consumer);
        }
    }
}
