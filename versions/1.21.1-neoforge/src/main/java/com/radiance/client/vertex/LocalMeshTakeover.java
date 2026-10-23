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
            int light,
            int color) {}

    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static long frame;

    private LocalMeshTakeover() {}

    static void beginFrame(long value) {
        frame = value;
        CACHE.values()
                .removeIf(
                        e -> {
                            if (frame - e.used <= 120) return false;
                            e.close();
                            return true;
                        });
    }

    static void invalidate() {
        CACHE.values().forEach(Entry::close);
        CACHE.clear();
    }

    public static void request(
            Object shape,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int color,
            Definition definition) {
        if (!(consumer instanceof PBRVertexConsumer pbr)
                || pbr.rigidOwner == null
                || !pbr.declaredModelEligible()
                || !RigidModelCapture.uniformPose(pose)
                || pbr.rigidOwner.rigidOrdinal >= 1024) {
            definition.emit(pose, consumer, light, color);
            return;
        }
        var texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null) {
            definition.emit(pose, consumer, light, color);
            return;
        }
        var key =
                new Key(
                        shape,
                        pbr.rigidLayer,
                        texture,
                        pbr.rigidPolicy(),
                        PBRMaterialContext.snapshot(),
                        light,
                        color);
        var entry = CACHE.get(key);
        if (entry == null) {
            if (CACHE.size() >= 512) {
                var oldest =
                        CACHE.values().stream()
                                .filter(e -> e.used != frame)
                                .min(Comparator.comparingLong(e -> e.used))
                                .orElse(null);
                if (oldest == null) {
                    definition.emit(pose, consumer, light, color);
                    return;
                }
                CACHE.remove(oldest.key);
                oldest.close();
            }
            entry = new Entry(key, definition);
            CACHE.put(key, entry);
        }
        entry.used = frame;
        var owner = pbr.rigidOwner;
        owner.rigidDraws.add(
                new RigidModelCapture.Draw(
                                entry,
                                new Matrix4f(pose.pose()),
                                owner.rigidInstance,
                                owner.rigidOrdinal++)
                        .capturedBy(pbr));
    }

    private static final class Entry implements RigidModelCapture.Geometry, AutoCloseable {
        final Key key;
        final long id = RigidModelCapture.nextResourceId();
        final ByteBufferBuilder allocator = new ByteBufferBuilder(4096);
        final MeshData mesh;
        long used;

        Entry(Key key, Definition definition) {
            this.key = key;
            try (var scope = key.material.open()) {
                var out =
                        PBRVertexConsumer.dynamicMaterial(allocator, key.layer, false, key.policy);
                definition.emit(new PoseStack().last(), out, key.light, key.color);
                mesh = out.end();
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
            mesh.close();
            allocator.close();
        }
    }
}
