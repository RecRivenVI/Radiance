package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import com.radiance.render.contract.SurfaceMaterial;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/** Declared procedural definitions are expanded only when their local input changes. */
public final class LocalMeshTakeover {
    @FunctionalInterface
    public interface Definition {
        void emit(PoseStack.Pose pose, VertexConsumer consumer, int light, int color);
    }

    private record Key(
            Object shape,
            RenderType layer,
            TextureTasks.Owner texture,
            SurfaceMaterial.MaterialPolicy policy,
            PBRMaterialContext.State material,
            float emissionFloor,
            int light,
            int color) {}

    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static final List<Entry> RETIRED = new ArrayList<>();
    private static long frame, bytes;
    private static final long BYTE_CAPACITY = 16L * 1024 * 1024;

    private LocalMeshTakeover() {}

    static void beginFrame(long value) {
        if (value != frame) {
            RETIRED.forEach(Entry::close);
            RETIRED.clear();
        }
        frame = value;
        CACHE.values()
                .removeIf(
                        e -> {
                            if (frame - e.used <= 120) return false;
                            bytes -= e.bytes;
                            e.close();
                            return true;
                        });
    }

    static void invalidate() {
        RETIRED.forEach(Entry::close);
        RETIRED.clear();
        CACHE.values().forEach(Entry::close);
        CACHE.clear();
        bytes = 0;
    }

    public static boolean supported(PoseStack.Pose pose, VertexConsumer consumer) {
        return consumer instanceof PBRVertexConsumer pbr
                && pbr.rigidOwner != null
                && !com.radiance.client.render.RasterPreviewScope.active()
                && pbr.declaredModelEligible()
                && RigidModelCapture.compatiblePose(pose)
                && pbr.rigidOwner.rigidOrdinal < 1024
                && TextureProxy.TASKS.owner(pbr.rigidTexture()) != null;
    }

    public static void release(Object shape) {
        CACHE.values()
                .removeIf(
                        entry -> {
                            if (entry.key.shape != shape) return false;
                            bytes -= entry.bytes;
                            // Another view in this frame may still hold a Draw with these CPU
                            // bytes.
                            RETIRED.add(entry);
                            com.radiance.api.render.internal.RigidSceneNative.releaseGeometry(
                                    entry.id);
                            return true;
                        });
    }

    public static void request(
            Object shape,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int color,
            Definition definition) {
        request(
                shape,
                pose,
                consumer,
                light,
                color,
                com.radiance.api.render.LocalSurface.NONE,
                definition,
                true,
                false);
    }

    /**
     * The template supplies vertex variation; uniform attributes are applied after its source
     * fields.
     */
    public static void template(
            Object shape,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            com.radiance.api.render.LocalSurface surface,
            Definition definition) {
        request(shape, pose, consumer, 0, -1, surface, definition, true, false);
    }

    /** A source may keep its original execution when the actual consumer is not a PT template. */
    public static boolean tryTemplate(
            Object shape,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            com.radiance.api.render.LocalSurface surface,
            Definition definition) {
        return request(shape, pose, consumer, 0, -1, surface, definition, false, false);
    }

    static boolean glyph(
            Object shape,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            com.radiance.api.render.LocalSurface surface,
            Definition definition) {
        return request(shape, pose, consumer, 0, -1, surface, definition, false, true);
    }

    private static boolean request(
            Object shape,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int color,
            com.radiance.api.render.LocalSurface surface,
            Definition definition,
            boolean fallback,
            boolean glyph) {
        surface = com.radiance.adapter.vanilla.VanillaMaterialAdapter.surface(surface);
        if (!(consumer instanceof PBRVertexConsumer pbr)
                || pbr.rigidOwner == null
                || com.radiance.client.render.RasterPreviewScope.active()
                || !(glyph ? pbr.glyphTemplateEligible() : pbr.declaredModelEligible())
                || !RigidModelCapture.compatiblePose(pose)
                || pbr.rigidOwner.rigidOrdinal >= 1024) {
            if (fallback) emit(definition, pose, consumer, light, color, surface);
            return false;
        }
        var texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null) {
            if (fallback) emit(definition, pose, consumer, light, color, surface);
            return false;
        }
        light = com.radiance.adapter.vanilla.VanillaMaterialAdapter.lightmap(light);
        var key =
                new Key(
                        shape,
                        pbr.rigidLayer,
                        texture,
                        pbr.rigidPolicy(),
                        PBRMaterialContext.snapshot(),
                        pbr.emissionFloor(),
                        light,
                        color);
        var entry = CACHE.get(key);
        if (entry == null) {
            entry = new Entry(key, definition);
            while (bytes + entry.bytes > BYTE_CAPACITY) {
                var oldest =
                        CACHE.values().stream()
                                .filter(e -> e.used != frame)
                                .min(Comparator.comparingLong(e -> e.used))
                                .orElse(null);
                if (oldest == null) {
                    entry.close();
                    if (fallback) emit(definition, pose, consumer, light, color, surface);
                    return false;
                }
                CACHE.remove(oldest.key);
                bytes -= oldest.bytes;
                oldest.close();
            }
            CACHE.put(key, entry);
            bytes += entry.bytes;
        }
        entry.used = frame;
        if (entry.mesh == null) return true;
        var owner = pbr.rigidOwner;
        owner.rigidDraws.add(
                new RigidModelCapture.Draw(
                                entry,
                                new Matrix4f(pose.pose()),
                                owner.rigidInstance,
                                owner.rigidOrdinal++)
                        .capturedBy(pbr)
                        .surface(surface));
        return true;
    }

    private static void emit(
            Definition definition,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int color,
            com.radiance.api.render.LocalSurface surface) {
        if (surface == com.radiance.api.render.LocalSurface.NONE)
            definition.emit(pose, consumer, light, color);
        else definition.emit(pose, new SurfaceConsumer(consumer, surface), light, color);
    }

    private record SurfaceConsumer(
            VertexConsumer delegate, com.radiance.api.render.LocalSurface surface)
            implements VertexConsumer {
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        public VertexConsumer setColor(int r, int g, int b, int a) {
            if ((surface.flags() & com.radiance.render.contract.RenderAbi.APPEARANCE_COLOR_MULTIPLY)
                    != 0)
                delegate.setColor(
                        Math.round(r * surface.red()),
                        Math.round(g * surface.green()),
                        Math.round(b * surface.blue()),
                        Math.round(a * surface.alpha()));
            else delegate.setColor(r, g, b, a);
            return this;
        }

        public VertexConsumer setUv(float u, float v) {
            if ((surface.flags() & com.radiance.render.contract.RenderAbi.APPEARANCE_UV_AFFINE)
                    != 0) delegate.setUv(surface.uv().u(u, v), surface.uv().v(u, v));
            else delegate.setUv(u, v);
            return this;
        }

        public VertexConsumer setUv1(int u, int v) {
            if ((surface.flags()
                            & com.radiance.render.contract.RenderAbi.APPEARANCE_OVERRIDE_OVERLAY)
                    != 0) delegate.setUv1(surface.overlay() & 65535, surface.overlay() >>> 16);
            else delegate.setUv1(u, v);
            return this;
        }

        public VertexConsumer setUv2(int u, int v) {
            if ((surface.flags() & com.radiance.render.contract.RenderAbi.APPEARANCE_OVERRIDE_LIGHT)
                    != 0) delegate.setUv2(surface.light() & 65535, surface.light() >>> 16);
            else delegate.setUv2(u, v);
            return this;
        }

        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    private static final class Entry implements RigidModelCapture.Geometry, AutoCloseable {
        final Key key;
        final long id = RigidModelCapture.nextResourceId();
        final ByteBufferBuilder allocator = new ByteBufferBuilder(4096);
        final MeshData mesh;
        final long bytes;
        long used;

        Entry(Key key, Definition definition) {
            this.key = key;
            try (var scope = key.material.open()) {
                var out =
                        PBRVertexConsumer.dynamicMaterial(allocator, key.layer, false, key.policy);
                out.setDefaultAlbedoEmission(key.emissionFloor);
                definition.emit(new PoseStack().last(), out, key.light, key.color);
                // A fully occluded world model is a valid empty definition, not a build failure.
                mesh = out.endNullable();
                bytes = mesh == null ? 0 : mesh.vertexBuffer().remaining();
            } catch (Throwable failure) {
                allocator.close();
                throw failure;
            }
        }

        public long id() {
            return id;
        }

        public RenderType layer() {
            return key.layer;
        }

        public TextureTasks.Owner texture() {
            return key.texture;
        }

        public MeshData mesh() {
            return mesh;
        }

        public void close() {
            if (mesh != null) mesh.close();
            allocator.close();
        }
    }
}
