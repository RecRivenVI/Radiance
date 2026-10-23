package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.vertex.BakedModelTakeover;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public abstract class ModelManagerTakeoverMixins {
    @Shadow private Map<ModelResourceLocation, BakedModel> bakedRegistry;

    @Inject(method = "apply", at = @At("TAIL"))
    private void radiance$publishBakedResources(CallbackInfo ci) {
        BakedModelTakeover.publishModels(bakedRegistry.values());
    }
}
