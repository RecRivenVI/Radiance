package com.radiance.mixins.vulkan_render_integration.accessor;

import com.radiance.client.vertex.BakedModelTakeover;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemOverrides.BakedOverride.class)
public interface BakedOverrideTakeoverAccess extends BakedModelTakeover.OverrideSource {
    @Accessor("model")
    BakedModel radiance$overrideModel();
}
