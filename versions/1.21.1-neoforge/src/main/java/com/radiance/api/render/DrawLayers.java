package com.radiance.api.render;

import java.util.Optional;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Current-target inspection boundary; source interpretation stays with the registering adapter. */
public final class DrawLayers {
    private DrawLayers() {}

    public static Optional<ResourceLocation> texture(RenderType layer) {
        return layer instanceof RenderType.CompositeRenderType composite
                ? composite.state.textureState.cutoutTexture()
                : Optional.empty();
    }
}
