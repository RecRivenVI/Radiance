package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.render.BoatWaterExclusion;
import com.radiance.client.render.RasterPreviewScope;
import com.radiance.client.vertex.PBRVertexConsumer;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BoatRenderer.class)
public abstract class BoatRendererWaterExclusionMixins {
    @WrapOperation(
            method =
                    "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void radiance$captureInterior(
            ModelPart patch,
            PoseStack pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            Operation<Void> original,
            @Local ListModel<Boat> model) {
        if (!RasterPreviewScope.active()
                && consumer instanceof PBRVertexConsumer
                && model instanceof BoatWaterExclusion.FloorSource floor)
            BoatWaterExclusion.render(
                    patch, floor.radiance$waterFloor(), pose, consumer, light, overlay);
        else original.call(patch, pose, consumer, light, overlay);
    }
}
