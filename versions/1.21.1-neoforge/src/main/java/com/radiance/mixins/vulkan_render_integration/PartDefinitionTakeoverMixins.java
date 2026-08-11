package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.vertex.PartModelCapture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PartDefinition.class)
public abstract class PartDefinitionTakeoverMixins {
    @Inject(method = "bake", at = @At("RETURN"))
    private void radiance$declarePart(
            int width, int height, CallbackInfoReturnable<ModelPart> result) {
        PartModelCapture.declare(result.getReturnValue());
    }
}
