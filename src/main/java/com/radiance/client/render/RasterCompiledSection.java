package com.radiance.client.render;

import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.CompiledSection;

/** Carries the original compile-time sorting policy with the published section generation. */
public class RasterCompiledSection extends CompiledSection {
    private final VertexSorting sorting;

    public RasterCompiledSection(VertexSorting sorting) {
        this.sorting = java.util.Objects.requireNonNull(sorting);
    }

    public VertexSorting rasterSorting() { return sorting; }
}
