package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.simulated_team.simulated.index.SimRenderTypes;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** The pinned Simulated 1.3.2 flexible-ribbon draw, before either output lowering. */
public final class SpringDrawContract {
    public static final ResourceLocation PROGRAM =
            ResourceLocation.fromNamespaceAndPath("simulated", "spring/spring");
    public static final float CUTOUT_THRESHOLD = 0.1F;
    public static final com.radiance.render.contract.SurfaceMaterial.MaterialPolicy WORLD_MATERIAL =
            new com.radiance.render.contract.SurfaceMaterial.MaterialPolicy(
                    com.radiance.render.contract.SourceAlpha.encode(
                            0, com.radiance.render.contract.RenderAbi.SOURCE_ALPHA_TEXEL_LOW),
                    com.radiance.render.contract.SurfaceMaterial.ColorSemantics.SURFACE_MIX,
                    false,
                    false,
                    false,
                    com.radiance.render.contract.SurfaceMaterial.NormalSemantics.VERTEX_BRDF);

    private SpringDrawContract() {}

    public record Draw(RenderType renderType, ResourceLocation bodyTexture) {}

    /**
     * Returns null for unrelated layers; refuses to lower an altered spring producer as vanilla.
     */
    public static Draw from(RenderType layer) {
        if (!"spring".equals(layer.name)) return null;
        if (!hasProducerClass(SpringDrawContract.class.getClassLoader())) return null;
        return fromFactory(layer, SimRenderTypes::spring);
    }

    static boolean hasProducerClass(ClassLoader loader) {
        try {
            Class.forName("dev.simulated_team.simulated.index.SimRenderTypes", false, loader);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError unavailable) {
            return false;
        }
    }

    static Draw fromFactory(RenderType layer, Function<ResourceLocation, RenderType> factory) {
        if (!(layer instanceof RenderType.CompositeRenderType composite)) return null;
        ResourceLocation texture = composite.state.textureState.cutoutTexture().orElse(null);
        if (texture == null) return null;
        // The pinned producer currently selects three size-specific Simulated PNGs. The
        // original memoized factory also accepts a mod/resource-pack texture override;
        // identity with that factory, not the texture path, establishes spring semantics.
        if (factory.apply(texture) != layer) return null;
        if (layer.mode() != VertexFormat.Mode.QUADS || !sameBlockBytes(layer.format()))
            throw new IllegalStateException(
                    "Simulated spring vertex layout changed: " + layer.format());
        return new Draw(layer, texture);
    }

    /** Stress and BLOCK Color have different names but the same 32-byte binary vertex layout. */
    public static boolean sameBlockBytes(VertexFormat format) {
        if (format.getVertexSize() != DefaultVertexFormat.BLOCK.getVertexSize()
                || format.getElementsMask() != DefaultVertexFormat.BLOCK.getElementsMask())
            return false;
        int[] actual = format.getOffsetsByElement();
        int[] expected = DefaultVertexFormat.BLOCK.getOffsetsByElement();
        for (int element = 0; element < expected.length; element++)
            if (actual[element] != expected[element]) return false;
        return true;
    }
}
