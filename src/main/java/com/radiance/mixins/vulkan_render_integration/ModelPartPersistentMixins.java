package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PartModelCapture;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartPersistentMixins {
    @Shadow @Final private List<ModelPart.Cube> cubes;
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void radiance$persistentPart(PoseStack.Pose pose, VertexConsumer consumer,
                                        int light, int overlay, int color, CallbackInfo ci) {
        if (PartModelCapture.capture(this, cubes, pose, consumer, light, overlay, color)) ci.cancel();
    }
}
