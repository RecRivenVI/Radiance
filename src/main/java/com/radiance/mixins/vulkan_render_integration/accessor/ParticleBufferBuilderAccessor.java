package com.radiance.mixins.vulkan_render_integration.accessor;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BufferBuilder.class)
public interface ParticleBufferBuilderAccessor {
    @Accessor("format") VertexFormat radiance$particleFormat();
    @Accessor("mode") VertexFormat.Mode radiance$particleMode();
}
