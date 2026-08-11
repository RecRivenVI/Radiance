package com.radiance.api.render;

import com.radiance.client.render.SectionRasterStorage;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;

/** Published source layers explicitly retained by their adapter, not a second world mesh cache. */
public final class SectionMeshes {
    private SectionMeshes() {}

    public record Mesh(
            SectionRenderDispatcher.RenderSection section,
            BlockPos origin,
            Object revision,
            RenderType layer,
            ByteBuffer vertices,
            int count) {}

    /**
     * Render thread only. Read-only BLOCK-format local quads, without received raster light.
     * Reconcile membership on every call; section identity alone does not validate a publication.
     */
    public static List<Mesh> retained() {
        SectionRasterStorage.drain();
        return SectionRasterStorage.sourceMeshes().stream()
                .map(
                        m ->
                                new Mesh(
                                        m.section(),
                                        m.origin(),
                                        m.revision(),
                                        m.layer(),
                                        m.vertices(),
                                        m.count()))
                .toList();
    }
}
