package com.radiance.mixins.compatibility.simulated;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.Map;
import java.util.SequencedMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiBufferSource.BufferSource.class)
public interface SimulatedGroupBufferSourceAccessor {
    @Accessor("startedBuilders") Map<RenderType, BufferBuilder> radiance$getStartedBuilders();
    @Accessor("sharedBuffer") ByteBufferBuilder radiance$getSharedBuffer();
    @Accessor("fixedBuffers") SequencedMap<RenderType, ByteBufferBuilder> radiance$getFixedBuffers();
    @Accessor("lastSharedType") void radiance$setLastSharedType(RenderType type);
}
