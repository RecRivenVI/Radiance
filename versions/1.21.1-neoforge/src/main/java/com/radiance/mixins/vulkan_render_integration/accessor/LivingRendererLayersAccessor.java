package com.radiance.mixins.vulkan_render_integration.accessor;

import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntityRenderer.class)
public interface LivingRendererLayersAccessor {
    @Accessor("layers")
    List<?> radiance$getLayers();
}
