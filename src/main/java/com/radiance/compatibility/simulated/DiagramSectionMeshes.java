package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.radiance.mixins.compatibility.simulated.SimulatedDiagramSectionAccessor;
import com.radiance.client.render.RasterPreviewScope;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.neoforged.neoforge.client.ClientHooks;

/** Raster copies are rebuilt on section publication, not from PT-normalized vertex attributes. */
public final class DiagramSectionMeshes implements AutoCloseable {
    private static final ThreadLocal<Boolean> COMPILING = ThreadLocal.withInitial(() -> false);
    private static volatile long generation;
    private long builtGeneration = generation;
    public static void invalidateResources() { generation++; }
    private final Map<SectionRenderDispatcher.RenderSection, Entry> entries = new IdentityHashMap<>();
    public static boolean compiling() { return COMPILING.get(); }

    public VertexBuffer buffer(SectionRenderDispatcher.RenderSection section, RenderType layer,
        ClientLevel level) {
        if (builtGeneration != generation) { close(); builtGeneration = generation; }
        var compiled = section.getCompiled();
        BlockPos origin = section.getOrigin().immutable();
        Entry entry = entries.get(section);
        if (entry == null || entry.compiled != compiled || !entry.origin.equals(origin)) {
            Entry replacement = build(section, level);
            entries.put(section, replacement);
            if (entry != null) entry.close();
            entry = replacement;
        }
        return entry.buffers.get(layer);
    }

    private Entry build(SectionRenderDispatcher.RenderSection section, ClientLevel level) {
        var minecraft = Minecraft.getInstance();
        var origin = section.getOrigin().immutable();
        var renderers = ClientHooks.gatherAdditionalRenderers(origin, level);
        var region = new RenderRegionCache().createRegion(level, SectionPos.of(origin), renderers.isEmpty());
        if (region == null) throw new IllegalStateException("Diagram section snapshot unavailable: " + origin);
        var cache = (dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension)
            com.radiance.mixins.compatibility.simulated.SimulatedDiagramModelCacheAccessor.radiance$cache().get();
        boolean previousSubLevel = cache.sable$getOnSubLevel();
        boolean previousCompiling = COMPILING.get();
        var pack = new SectionBufferBuilderPack();
        var result = new Entry(section.getCompiled(), origin);
        var recovery = new SimulatedRenderRecovery();
        recovery.add(() -> { if (previousCompiling) COMPILING.set(true); else COMPILING.remove(); });
        recovery.add(net.minecraft.client.renderer.block.ModelBlockRenderer::clearCache);
        recovery.add(() -> cache.sable$setOnSubLevel(previousSubLevel));
        recovery.add(pack::close);
        try {
            recovery.run(() -> {
                COMPILING.set(true);
                try (var raster = RasterPreviewScope.enter()) {
                    var compiler = new SectionCompiler(minecraft.getBlockRenderer(), minecraft.getBlockEntityRenderDispatcher());
                    // Sable draws the compiled section indices unchanged, including in a diagram.
                    // Preserve the compile-time policy rather than re-sorting from the diagram camera.
                    var sorting = result.compiled instanceof com.radiance.client.render.RasterCompiledSection published
                        ? published.rasterSorting()
                        : ((SimulatedDiagramSectionAccessor) section).radiance$createVertexSorting();
                    var data = compiler.compile(SectionPos.of(origin), region, sorting, pack, renderers);
                    var meshCleanup = new SimulatedRenderRecovery();
                    meshCleanup.add(data::release);
                    meshCleanup.run(() -> {
                        var meshes = data.renderedLayers.entrySet().iterator();
                        while (meshes.hasNext()) {
                            var mesh = meshes.next();
                            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                            result.buffers.put(mesh.getKey(), buffer);
                            meshes.remove(); // upload owns and closes MeshData even on failure.
                            buffer.upload(mesh.getValue());
                        }
                    });
                }
            });
            return result;
        } catch (RuntimeException | Error failure) {
            try { result.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public void retain(Set<SectionRenderDispatcher.RenderSection> selected) {
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!selected.contains(entry.getKey())) { iterator.remove(); entry.getValue().close(); }
        }
    }

    @Override public void close() {
        SimulatedRenderRecovery recovery = new SimulatedRenderRecovery();
        entries.values().forEach(entry -> recovery.add(entry::close));
        entries.clear(); recovery.run(() -> {});
    }

    private static final class Entry implements AutoCloseable {
        final SectionRenderDispatcher.CompiledSection compiled;
        final BlockPos origin;
        final Map<RenderType, VertexBuffer> buffers = new IdentityHashMap<>();
        Entry(SectionRenderDispatcher.CompiledSection compiled, BlockPos origin) {
            this.compiled = compiled; this.origin = origin;
        }
        @Override public void close() {
            SimulatedRenderRecovery cleanup = new SimulatedRenderRecovery();
            buffers.values().forEach(buffer -> cleanup.add(buffer::close));
            buffers.clear(); cleanup.run(() -> {});
        }
    }
}
