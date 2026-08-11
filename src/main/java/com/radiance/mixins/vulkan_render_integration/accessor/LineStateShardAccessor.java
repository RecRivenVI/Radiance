package com.radiance.mixins.vulkan_render_integration.accessor;

import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the explicit GL line width declared by a {@link RenderStateShard.LineStateShard}. */
@Mixin(RenderStateShard.LineStateShard.class)
public interface LineStateShardAccessor {

    @Accessor("width")
    OptionalDouble radiance$getWidth();
}
