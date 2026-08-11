package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.radiance.client.vertex.PBRVertexFormats;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.lwjgl.system.MemoryUtil;

/** Owns copies required by sublevel offscreen consumers after the PT compiler releases MeshData. */
public final class SectionRasterStorage {
    private static final Map<SectionRenderDispatcher.RenderSection, Pending> PENDING =
            new ConcurrentHashMap<>();
    private static final Map<SectionRenderDispatcher.RenderSection, Ready> READY =
            new ConcurrentHashMap<>();

    private SectionRasterStorage() {}

    public static void publish(
            SectionRenderDispatcher.RenderSection section,
            Map<RenderType, MeshData> meshes,
            BooleanSupplier stillOwned) {
        // Ordinary sections without retained source layers need no extra publication. Keep an
        // empty replacement only when there is old source content to retire.
        if (meshes.isEmpty() && !PENDING.containsKey(section) && !READY.containsKey(section))
            return;
        Map<RenderType, Snapshot> copies = new LinkedHashMap<>();
        meshes.forEach((layer, mesh) -> copies.put(layer, Snapshot.copy(mesh)));
        PENDING.put(
                section,
                new Pending(
                        section.getOrigin().immutable(),
                        section.getCompiled(),
                        copies,
                        stillOwned));
    }

    public static void discard(SectionRenderDispatcher.RenderSection section) {
        PENDING.remove(section);
        READY.remove(section);
    }

    public static void clear() {
        PENDING.clear();
        READY.clear();
    }

    public static void drain() {
        RenderSystem.assertOnRenderThread();
        for (var entry : PENDING.entrySet()) {
            var section = entry.getKey();
            Pending pending = entry.getValue();
            if (!pending.stillOwned.getAsBoolean()
                    || section.getCompiled() != pending.compiled
                    || !section.getOrigin().equals(pending.origin)) {
                PENDING.remove(section, pending);
                continue;
            }
            pending.meshes.forEach((layer, snapshot) -> snapshot.upload(section.getBuffer(layer)));
            Map<RenderType, Snapshot> translated = new LinkedHashMap<>();
            pending.meshes.forEach(
                    (layer, snapshot) -> {
                        if (com.radiance.api.render.SourceHooks.retainOffscreenLayer(layer))
                            translated.put(layer, snapshot);
                    });
            READY.put(
                    section,
                    new Ready(
                            pending.origin,
                            pending.compiled,
                            pending.stillOwned,
                            Map.copyOf(translated)));
            // Leave the mailbox present until READY is visible, so a concurrent empty
            // publication cannot miss the content that it must replace.
            PENDING.remove(section, pending);
        }
    }

    public static java.util.List<SectionRenderDispatcher.RenderSection> worldSections() {
        READY.entrySet()
                .removeIf(
                        entry ->
                                !entry.getValue().owner.getAsBoolean()
                                        || entry.getKey().getCompiled()
                                                == SectionRenderDispatcher.CompiledSection
                                                        .UNCOMPILED
                                        || !entry.getKey()
                                                .getOrigin()
                                                .equals(entry.getValue().origin));
        return READY.keySet().stream().filter(section -> section.index >= 0).toList();
    }

    private record Ready(
            BlockPos origin,
            SectionRenderDispatcher.CompiledSection compiled,
            BooleanSupplier owner,
            Map<RenderType, Snapshot> translated) {}

    /** Immutable CPU copies, valid only while their section publication still owns them. */
    public record SourceMesh(
            SectionRenderDispatcher.RenderSection section,
            BlockPos origin,
            Object revision,
            RenderType layer,
            ByteBuffer vertices,
            int count) {}

    public static java.util.List<SourceMesh> sourceMeshes() {
        RenderSystem.assertOnRenderThread();
        // A newer compile request does not retire the displayed publication. drain() replaces
        // it only after a complete snapshot, including an empty one, reaches the render thread.
        worldSections();
        var result = new java.util.ArrayList<SourceMesh>();
        READY.forEach(
                (section, ready) ->
                        ready.translated.forEach(
                                (layer, mesh) ->
                                        result.add(
                                                new SourceMesh(
                                                        section,
                                                        ready.origin,
                                                        ready.compiled,
                                                        layer,
                                                        ByteBuffer.wrap(mesh.vertices)
                                                                .asReadOnlyBuffer()
                                                                .order(ByteOrder.nativeOrder()),
                                                        mesh.state.vertexCount()))));
        return result;
    }

    private record Pending(
            BlockPos origin,
            SectionRenderDispatcher.CompiledSection compiled,
            Map<RenderType, Snapshot> meshes,
            BooleanSupplier stillOwned) {}

    record Snapshot(MeshData.DrawState state, byte[] vertices, byte[] indices) {
        static Snapshot copy(MeshData mesh) {
            if (mesh.drawState().format() == PBRVertexFormats.PBR_TRIANGLE) {
                var state = mesh.drawState();
                byte[] block = blockVertices(mesh.vertexBuffer(), state.vertexCount());
                return new Snapshot(
                        new MeshData.DrawState(
                                DefaultVertexFormat.BLOCK,
                                state.vertexCount(),
                                state.indexCount(),
                                state.mode(),
                                state.indexType()),
                        block,
                        mesh.indexBuffer() == null ? null : copyBytes(mesh.indexBuffer()));
            }
            return new Snapshot(
                    mesh.drawState(),
                    copyBytes(mesh.vertexBuffer()),
                    mesh.indexBuffer() == null ? null : copyBytes(mesh.indexBuffer()));
        }

        private static byte[] blockVertices(ByteBuffer source, int count) {
            if (PBRVertexFormats.PBR_TRIANGLE.getVertexSize() != 128
                    || source.remaining() != count * 128)
                throw new IllegalArgumentException("Unexpected PBR section vertex ABI");
            ByteBuffer input = source.duplicate().order(ByteOrder.nativeOrder());
            ByteBuffer output = ByteBuffer.allocate(count * 32).order(ByteOrder.nativeOrder());
            int start = input.position();
            for (int i = 0; i < count; ++i) {
                int p = start + i * 128;
                output.putFloat(input.getFloat(p))
                        .putFloat(input.getFloat(p + 4))
                        .putFloat(input.getFloat(p + 8));
                for (int component = 0; component < 4; ++component)
                    output.put(
                            (byte)
                                    Math.round(
                                            Math.clamp(input.getFloat(p + 32 + component * 4), 0, 1)
                                                    * 255));
                output.putFloat(input.getFloat(p + 56)).putFloat(input.getFloat(p + 60));
                output.putShort((short) input.getInt(p + 96))
                        .putShort((short) input.getInt(p + 100));
                for (int component = 0; component < 3; ++component)
                    output.put(
                            (byte)
                                    (Math.clamp(input.getFloat(p + 16 + component * 4), -1, 1)
                                            * 127));
                output.put((byte) 0);
            }
            return output.array();
        }

        private static byte[] copyBytes(ByteBuffer input) {
            ByteBuffer source = input.duplicate();
            byte[] bytes = new byte[source.remaining()];
            source.get(bytes);
            return bytes;
        }

        void upload(VertexBuffer destination) {
            if (vertices.length == 0) return;
            try (ByteBufferBuilder vertexOwner = new ByteBufferBuilder(vertices.length)) {
                MemoryUtil.memByteBuffer(vertexOwner.reserve(vertices.length), vertices.length)
                        .put(vertices);
                // VertexBuffer.upload owns and closes this MeshData, including on failure.
                destination.upload(new MeshData(vertexOwner.build(), state));
            }
            if (indices != null && indices.length != 0) {
                try (ByteBufferBuilder indexOwner = new ByteBufferBuilder(indices.length)) {
                    MemoryUtil.memByteBuffer(indexOwner.reserve(indices.length), indices.length)
                            .put(indices);
                    destination.uploadIndexBuffer(indexOwner.build());
                }
            }
        }
    }
}
