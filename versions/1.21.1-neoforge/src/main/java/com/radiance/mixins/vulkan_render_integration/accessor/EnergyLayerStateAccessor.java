package com.radiance.mixins.vulkan_render_integration.accessor;

import com.radiance.adapter.vanilla.VanillaLayerReplacement;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EnergySwirlLayer.class)
public interface EnergyLayerStateAccessor extends VanillaLayerReplacement.EnergyAccess {
    @Invoker("model")
    EntityModel<?> radiance$energyModel();

    @Invoker("getTextureLocation")
    ResourceLocation radiance$energyTexture();

    @Invoker("xOffset")
    float radiance$xOffset(float time);
}
