package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.RasterPreviewScope;
import com.radiance.client.texture.TextureTasks;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Opt-in local ModelPart geometry. Animation, render callbacks and unsupported consumers remain original. */
public final class PartModelCapture {
    public static final boolean ENABLED = RigidModelCapture.ENABLED && Boolean.getBoolean("radiance.rigidParts");
    private static final int CAPACITY = 512;
    private static final long BYTE_CAPACITY = 16L * 1024 * 1024;
    private static final IdentityHashMap<Object, Source> SOURCES = new IdentityHashMap<>();
    private static final List<Entry> CACHE = new ArrayList<>();
    private static Object level;
    private static long reload, frame, nextPart;
    private PartModelCapture() {}

    static void invalidate() {
        CACHE.forEach(Entry::close); CACHE.clear(); SOURCES.clear(); level = null;
    }

    static void beginFrame(Object currentLevel, long epoch) {
        if (!ENABLED) return;
        if (level != currentLevel || reload != epoch) {
            invalidate();
            level = currentLevel; reload = epoch;
        }
        ++frame;
        CACHE.removeIf(e -> { if (frame - e.used <= 120) return false; e.close(); return true; });
        SOURCES.values().removeIf(s -> frame - s.used > 120);
    }

    public static boolean capture(Object part, List<ModelPart.Cube> cubes, PoseStack.Pose pose,
                                  VertexConsumer consumer, int light, int overlay, int color) {
        if (!ENABLED || cubes.isEmpty() || part.getClass() != ModelPart.class
            || consumer.getClass() != PBRVertexConsumer.class || RasterPreviewScope.active()
            || !RigidModelCapture.uniformPose(pose)) return false;
        PBRVertexConsumer pbr = (PBRVertexConsumer) consumer;
        if (pbr.rigidOwner == null || !pbr.rigidEligible()) return false;
        var owner = pbr.rigidOwner;
        TextureTasks.Owner texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null) return false;

        Source source = SOURCES.get(part);
        if (source == null || !source.snapshot.matches(cubes)) {
            Snapshot snapshot = Snapshot.read(cubes);
            if (snapshot == null || snapshot.vertices() == 0) return false;
            if (source == null) {
                if (SOURCES.size() >= CAPACITY) return false;
                source = new Source(snapshot); SOURCES.put(part, source);
            } else source.snapshot = snapshot;
        }
        source.used = frame;
        PartIdentity identity = source.identity(pbr.rigidLayer, frame);
        if (identity == null) return false;
        var state = owner.partState();
        int occurrence = state.occurrences.getOrDefault(identity, 0);
        if (occurrence >= 32) return false;
        Entry found = null;
        for (Entry entry : CACHE) {
            if (entry.snapshot == source.snapshot && entry.layer == pbr.rigidLayer
                && entry.texture.equals(texture) && entry.light == light && entry.overlay == overlay
                && entry.color == color) { found = entry; break; }
        }
        if (found == null) {
            long required = (long) source.snapshot.vertices() * 128;
            if (required > BYTE_CAPACITY) return false;
            long bytes = CACHE.stream().mapToLong(e -> (long) e.snapshot.vertices() * 128).sum();
            while (CACHE.size() >= CAPACITY || bytes + required > BYTE_CAPACITY) {
                Entry oldest = CACHE.stream().filter(e -> e.used != frame)
                    .min(Comparator.comparingLong(e -> e.used)).orElse(null);
                if (oldest == null) return false;
                CACHE.remove(oldest); oldest.close(); bytes -= (long) oldest.snapshot.vertices() * 128;
            }
            found = new Entry(source.snapshot, pbr.rigidLayer, texture, light, overlay, color);
            CACHE.add(found);
        }
        found.used = frame;
        state.occurrences.put(identity, occurrence + 1);
        MaterialBatch batch = state.materials.computeIfAbsent(pbr.rigidLayer, ignored -> new MaterialBatch());
        owner.rigidDraws.add(new RigidModelCapture.Draw(found, new Matrix4f(pose.pose()),
            historyIdentity(owner.rigidInstance, identity.id, occurrence), batch));
        return true;
    }

    // Separate from baked-model count/ordinal keys. Removing another part cannot transfer its history.
    static long historyIdentity(long owner, long part, int occurrence) {
        if (owner < 0 || owner > 0xffff_ffffL || part < 1 || part > 0xff_ffffL
            || occurrence < 0 || occurrence >= 32) throw new IllegalArgumentException("Part history identity");
        return Long.MIN_VALUE | (1L << 61) | (owner << 29) | (part << 5) | occurrence;
    }

    static final class MaterialBatch { boolean captured; int faces; }
    static final class PartIdentity {
        final long id;
        long used;
        PartIdentity(long id) { this.id = id; }
    }
    static final class Source {
        final Map<Object, PartIdentity> layers = new IdentityHashMap<>();
        Snapshot snapshot;
        long used;
        Source(Snapshot snapshot) { this.snapshot = snapshot; }
        PartIdentity identity(Object layer, long currentFrame) {
            PartIdentity identity = layers.get(layer);
            if (identity == null) {
                // Dynamic RenderTypes must not grow the history map indefinitely.
                layers.values().removeIf(id -> currentFrame - id.used > 120);
                if (layers.size() >= 64 || nextPart >= 0xff_ffffL) return null;
                identity = new PartIdentity(++nextPart); layers.put(layer, identity);
            }
            identity.used = currentFrame;
            return identity;
        }
    }

    /** Immutable input snapshot, compared before use; no transformed output or per-frame full mesh hash. */
    static final class Snapshot {
        final float[] data; // per quad: normal xyz; four vertices each xyz (model units), uv
        final int[] polygonCounts;
        Snapshot(float[] data, int[] polygonCounts) { this.data = data; this.polygonCounts = polygonCounts; }
        int vertices() { return data.length / 23 * 4; }
        static Snapshot read(List<ModelPart.Cube> cubes) {
            int count = 0;
            for (var cube : cubes) {
                if (cube == null || cube.getClass() != ModelPart.Cube.class) return null;
                count += cube.polygons.length;
                if (count > 32768) return null;
            }
            float[] data = new float[count * 23]; int[] counts = new int[cubes.size()]; int at = 0, ci = 0;
            for (var cube : cubes) {
                counts[ci++] = cube.polygons.length;
                for (var polygon : cube.polygons) {
                    if (polygon == null || polygon.vertices.length != 4) return null;
                    data[at++] = polygon.normal.x; data[at++] = polygon.normal.y; data[at++] = polygon.normal.z;
                    for (var v : polygon.vertices) {
                        if (v == null) return null;
                        data[at++] = v.pos.x; data[at++] = v.pos.y; data[at++] = v.pos.z;
                        data[at++] = v.u; data[at++] = v.v;
                    }
                }
            }
            for (float value : data) if (!Float.isFinite(value)) return null;
            return new Snapshot(data, counts);
        }
        boolean matches(List<ModelPart.Cube> cubes) {
            if (cubes.size() != polygonCounts.length) return false;
            int at = 0, ci = 0;
            for (var cube : cubes) {
                if (cube == null || cube.getClass() != ModelPart.Cube.class || cube.polygons.length != polygonCounts[ci++]) return false;
                for (var p : cube.polygons) {
                    if (p == null || p.vertices.length != 4 || p.normal.x != data[at++]
                        || p.normal.y != data[at++] || p.normal.z != data[at++]) return false;
                    for (var v : p.vertices) {
                        if (v == null || v.pos.x != data[at++] || v.pos.y != data[at++] || v.pos.z != data[at++]
                            || v.u != data[at++] || v.v != data[at++]) return false;
                    }
                }
            }
            return true;
        }
        void emit(VertexConsumer consumer, PoseStack.Pose pose, int light, int overlay, int color) {
            Vector3f temporary = new Vector3f();
            for (int at = 0; at < data.length;) {
                pose.transformNormal(data[at++], data[at++], data[at++], temporary);
                float nx = temporary.x, ny = temporary.y, nz = temporary.z;
                for (int v = 0; v < 4; ++v) {
                    pose.pose().transformPosition(data[at++] / 16, data[at++] / 16, data[at++] / 16, temporary);
                    float u = data[at++], uv = data[at++];
                    consumer.addVertex(temporary.x, temporary.y, temporary.z, color, u, uv, overlay, light, nx, ny, nz);
                }
            }
        }
    }

    static final class Entry implements RigidModelCapture.Geometry, AutoCloseable {
        final long id = RigidModelCapture.nextResourceId();
        final Snapshot snapshot;
        final RenderType layer;
        final TextureTasks.Owner texture;
        final int light, overlay, color;
        final ByteBufferBuilder allocator;
        final MeshData mesh;
        long used;
        Entry(Snapshot snapshot, RenderType layer, TextureTasks.Owner texture, int light, int overlay, int color) {
            this.snapshot = snapshot; this.layer = layer; this.texture = texture;
            this.light = light; this.overlay = overlay; this.color = color;
            allocator = new ByteBufferBuilder(snapshot.vertices() * 128);
            try {
                PBRVertexConsumer consumer = new PBRVertexConsumer(allocator, layer);
                snapshot.emit(consumer, new PoseStack().last(), light, overlay, color);
                mesh = consumer.end();
            } catch (Throwable failure) { allocator.close(); throw failure; }
        }
        public long id() { return id; }
        public RenderType layer() { return layer; }
        public TextureTasks.Owner texture() { return texture; }
        public MeshData mesh() { return mesh; }
        public void close() { mesh.close(); allocator.close(); }
    }
}
