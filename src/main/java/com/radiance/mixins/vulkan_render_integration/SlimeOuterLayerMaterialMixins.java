package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PBRMaterialContext;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Gives only the dedicated slime outer shell the dielectric gel material semantic. */
@Mixin(SlimeOuterLayer.class)
public abstract class SlimeOuterLayerMaterialMixins {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/model/EntityModel;renderToBuffer("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void radiance$renderGelShell(EntityModel<?> model, PoseStack poseStack,
        VertexConsumer vertexConsumer, int light, int overlay, Operation<Void> original,
        @Local(argsOnly = true) LivingEntity slime) {
        // An invisible-but-glowing slime reaches this call with RenderType.outline(); that
        // synthetic outline must keep the outline material rather than becoming dielectric.
        try (var ignored = PBRMaterialContext.pushEntityTransmission(!slime.isInvisible())) {
            original.call(model, poseStack, vertexConsumer, light, overlay);
        }
    }
}
