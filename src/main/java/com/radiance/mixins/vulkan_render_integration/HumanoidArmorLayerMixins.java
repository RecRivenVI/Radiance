package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PBRVertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Folds vanilla's second, coincident armor-glint draw into the captured base
 * surface. Vulkan ray tracing must see one armor mesh with a glint material
 * layer, rather than two overlapping acceleration-structure surfaces.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixins {

    @Unique
    private static final ThreadLocal<Boolean> radiance$mergedArmorGlint =
        ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;"
        + "Lnet/minecraft/world/entity/LivingEntity;"
        + "Lnet/minecraft/world/entity/EquipmentSlot;I"
        + "Lnet/minecraft/client/model/HumanoidModel;FFFFFF)V", at = @At("HEAD"))
    private void radiance$beginArmorGlintMerge(CallbackInfo ci) {
        radiance$mergedArmorGlint.set(false);
    }

    @WrapOperation(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;"
        + "Lnet/minecraft/world/entity/LivingEntity;"
        + "Lnet/minecraft/world/entity/EquipmentSlot;I"
        + "Lnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel("
                + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                + "Lnet/minecraft/client/model/Model;I"
                + "Lnet/minecraft/resources/ResourceLocation;)V"))
    private void radiance$mergeArmorGlintIntoBase(HumanoidArmorLayer<?, ?, ?> layer,
        PoseStack poseStack, MultiBufferSource buffers, int light, Model model, int color,
        ResourceLocation texture, Operation<Void> original,
        @Local ItemStack itemStack) {
        if (!itemStack.hasFoil() || radiance$mergedArmorGlint.get()) {
            original.call(layer, poseStack, buffers, light, model, color, texture);
            return;
        }

        MultiBufferSource mergedBuffers = renderType -> {
            VertexConsumer consumer = buffers.getBuffer(renderType);
            if (consumer instanceof PBRVertexConsumer pbrConsumer) {
                radiance$mergedArmorGlint.set(true);
                return new PBRVertexConsumer.GLint(pbrConsumer, RenderType.armorEntityGlint());
            }
            return consumer;
        };
        original.call(layer, poseStack, mergedBuffers, light, model, color, texture);
    }

    @Inject(method = "renderGlint(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
        + "Lnet/minecraft/client/model/Model;)V", at = @At("HEAD"), cancellable = true)
    private void radiance$skipCoincidentArmorGlint(PoseStack poseStack,
        MultiBufferSource buffers, int light, Model model, CallbackInfo ci) {
        if (radiance$mergedArmorGlint.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;"
        + "Lnet/minecraft/world/entity/LivingEntity;"
        + "Lnet/minecraft/world/entity/EquipmentSlot;I"
        + "Lnet/minecraft/client/model/HumanoidModel;FFFFFF)V", at = @At("RETURN"))
    private void radiance$endArmorGlintMerge(CallbackInfo ci) {
        radiance$mergedArmorGlint.remove();
    }
}
