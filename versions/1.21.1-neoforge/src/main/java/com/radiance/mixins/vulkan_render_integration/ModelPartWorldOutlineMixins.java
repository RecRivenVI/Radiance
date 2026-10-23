package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PartModelCapture;
import com.radiance.client.vertex.StorageOutlineVertexConsumerProvider.WorldOutlineVertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartWorldOutlineMixins {

    @Inject(method = "compile", at = @At("HEAD"))
    private void radiance$emitMergedWorldOutline(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            int color,
            CallbackInfo ci) {
        if (!(consumer instanceof WorldOutlineVertexConsumer outlineConsumer)) {
            return;
        }

        PartModelCapture.outline((ModelPart) (Object) this, pose, outlineConsumer);
    }
}
