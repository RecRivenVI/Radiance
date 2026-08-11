package com.radiance.mixins.compatibility.simulated;

import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderSection.class)
public interface SimulatedDiagramSectionAccessor {
    @Invoker("createVertexSorting")
    VertexSorting radiance$createVertexSorting();
}
