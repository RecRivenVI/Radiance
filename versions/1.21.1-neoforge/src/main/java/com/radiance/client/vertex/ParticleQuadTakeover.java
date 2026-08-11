package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTasks;
import com.radiance.mixin_related.SourceCompatibility;
import java.util.*;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Local billboard resources shared by particles; source parameter math supplies pose, UV and light.
 */
public final class ParticleQuadTakeover {
    public interface Source {
        void radiance$submitParameters(
                VertexConsumer consumer, net.minecraft.client.Camera camera, float partial);

        void radiance$billboardParameters(
                VertexConsumer consumer, net.minecraft.client.Camera camera, float partial);

        void radiance$rotatedParameters(
                VertexConsumer consumer,
                net.minecraft.client.Camera camera,
                Quaternionf rotation,
                float partial);
    }

    private static final ClassValue<Boolean> STANDARD_RENDER =
            new ClassValue<>() {
                protected Boolean computeValue(Class<?> type) {
                    try {
                        var owner =
                                type.getMethod(
                                                "render",
                                                VertexConsumer.class,
                                                net.minecraft.client.Camera.class,
                                                float.class)
                                        .getDeclaringClass();
                        return owner == net.minecraft.client.particle.SingleQuadParticle.class
                                || owner
                                        == net.minecraft.client.particle.VibrationSignalParticle
                                                .class
                                || owner == net.minecraft.client.particle.ShriekParticle.class
                                || owner
                                        == net.minecraft.client.particle.DustColorTransitionParticle
                                                .class
                                || owner
                                        == net.minecraft.client.particle.FlyTowardsPositionParticle
                                                .class
                                || owner
                                        == net.minecraft.client.particle.FireworkParticles
                                                .OverlayParticle.class
                                || owner.getName()
                                        .equals(
                                                "net.minecraft.client.particle.FireworkParticles$SparkParticle");
                    } catch (NoSuchMethodException failure) {
                        throw new IllegalStateException(failure);
                    }
                }
            };

    public static boolean replacementEligible(Particle particle, VertexConsumer consumer) {
        return particle instanceof Source
                && STANDARD_RENDER.get(particle.getClass())
                && eligible(particle, consumer);
    }

    public static void request(
            Particle particle,
            VertexConsumer consumer,
            net.minecraft.client.Camera camera,
            float partial) {
        ((Source) particle).radiance$submitParameters(consumer, camera, partial);
    }

    private record Key(
            RenderType layer,
            TextureTasks.Owner texture,
            com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy,
            float u0,
            float u1,
            float v0,
            float v1,
            int light,
            float emission) {}

    private static final Map<Key, Base> BASES = new HashMap<>();
    private static final int BASE_CAPACITY = 256;
    private static final WeakHashMap<Particle, Owner> OWNERS = new WeakHashMap<>();
    private static long frame, nextOwner, submissions, builds, dynamicQuads;

    private static final class Owner {
        final long id = ++nextOwner;
        long frame;
        int ordinal;
    }

    private ParticleQuadTakeover() {}

    static void beginFrame(long value) {
        frame = value;
        BASES.values()
                .removeIf(
                        base -> {
                            if (frame - base.used <= 120) return false;
                            base.close();
                            return true;
                        });
    }

    static void invalidate() {
        BASES.values().forEach(Base::close);
        BASES.clear();
        OWNERS.clear();
    }

    public static boolean eligible(Particle particle, VertexConsumer consumer) {
        return RigidModelCapture.ENABLED
                && consumer instanceof PBRVertexConsumer pbr
                && pbr.particleOwner != null
                && SourceCompatibility.compatible(SourceCompatibility.Source.STATIC_STATE)
                && SourceCompatibility.producer(particle.getClass());
    }

    public static void submit(
            Particle particle,
            VertexConsumer consumer,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float partialTick,
            float size,
            float u0,
            float u1,
            float v0,
            float v1,
            int light,
            float red,
            float green,
            float blue,
            float alpha) {
        PBRVertexConsumer pbr = (PBRVertexConsumer) consumer;
        light = com.radiance.adapter.vanilla.VanillaMaterialAdapter.lightmap(light);
        var provider = pbr.particleOwner;
        // Source callbacks above have executed even when the source quad has zero area.
        if (size == 0) return;
        Matrix4f pose = transform(rotation, x, y, z, size);
        // Vanilla vibration reaches lifetime with a zero remaining-tick divisor. Its final
        // render can contain NaNs. Preserve PBRVertexConsumer's existing degenerate-vertex
        // handling rather than introducing a non-finite AS instance transform.
        if (!instancePose(pose)) {
            emit(consumer, rotation, x, y, z, size, u0, u1, v0, v1, light, red, green, blue, alpha);
            dynamicQuads++;
            return;
        }
        var key =
                new Key(
                        pbr.rigidLayer,
                        TextureProxy.TASKS.owner(pbr.rigidTexture()),
                        pbr.rigidPolicy(),
                        u0,
                        u1,
                        v0,
                        v1,
                        light,
                        provider.particleEmission());
        Base base = BASES.get(key);
        if (base == null) {
            if (BASES.size() == BASE_CAPACITY) {
                var retired =
                        BASES.entrySet().stream()
                                .filter(e -> e.getValue().used != frame)
                                .min(Comparator.comparingLong(e -> e.getValue().used))
                                .orElse(null);
                if (retired == null) {
                    emit(
                            consumer, rotation, x, y, z, size, u0, u1, v0, v1, light, red, green,
                            blue, alpha);
                    dynamicQuads++;
                    return;
                }
                BASES.remove(retired.getKey());
                retired.getValue().close();
            }
            base = new Base(key);
            BASES.put(key, base);
            builds++;
        }
        base.used = frame;
        Owner owner = OWNERS.computeIfAbsent(particle, k -> new Owner());
        if (owner.frame != frame) {
            owner.frame = frame;
            owner.ordinal = 0;
        }
        long history = historyIdentity(owner.id, owner.ordinal++);
        provider.rigidDraws.add(
                new RigidModelCapture.Draw(base, pose, history)
                        .source(particle)
                        .capturedBy(consumer)
                        .particleColour(
                                quantize(red), quantize(green), quantize(blue), quantize(alpha))
                        .origin(provider.particleX, provider.particleY, provider.particleZ));
        submissions++;
    }

    private static float quantize(float value) {
        return com.radiance.render.contract.SourceColor.channel(value);
    }

    static boolean instancePose(Matrix4f pose) {
        return pose.isFinite() && pose.determinant() != 0;
    }

    static long historyIdentity(long owner, int ordinal) {
        return Long.MIN_VALUE | (1L << 61) | (owner << 16) | ordinal;
    }

    private static void emit(
            VertexConsumer out,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float size,
            float u0,
            float u1,
            float v0,
            float v1,
            int light,
            float r,
            float g,
            float b,
            float a) {
        int[][] corners = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int i = 0; i < 4; i++) {
            var p =
                    new Vector3f(corners[i][0], corners[i][1], 0)
                            .rotate(rotation)
                            .mul(size)
                            .add(x, y, z);
            out.addVertex(p.x, p.y, p.z)
                    .setUv(i < 2 ? u1 : u0, i == 0 || i == 3 ? v1 : v0)
                    .setColor(r, g, b, a)
                    .setLight(light);
        }
    }

    static Matrix4f transform(Quaternionf rotation, float x, float y, float z, float size) {
        // LOOKAT_Y deliberately supplies a non-unit quaternion. Use the source vector rotation,
        // not a normalized quaternion or a matrix API with a different normalization contract.
        Vector3f a = new Vector3f(1, 0, 0).rotate(rotation);
        Vector3f b = new Vector3f(0, 1, 0).rotate(rotation);
        Vector3f n = new Vector3f(a).cross(b).normalize();
        a.mul(size);
        b.mul(size);
        return new Matrix4f(a.x, a.y, a.z, 0, b.x, b.y, b.z, 0, n.x, n.y, n.z, 0, x, y, z, 1);
    }

    public static long[] counters() {
        return new long[] {submissions, builds, BASES.size(), OWNERS.size(), dynamicQuads};
    }

    private static final class Base implements RigidModelCapture.Geometry, AutoCloseable {
        final long id = RigidModelCapture.nextResourceId();
        final Key key;
        final ByteBufferBuilder allocator = new ByteBufferBuilder(512);
        final MeshData mesh;
        long used;

        Base(Key key) {
            this.key = key;
            try {
                var out =
                        PBRVertexConsumer.dynamicMaterial(allocator, key.layer, false, key.policy);
                out.setDefaultAlbedoEmission(key.emission);
                vertex(out, 1, -1, key.u1, key.v1, key.light);
                vertex(out, 1, 1, key.u1, key.v0, key.light);
                vertex(out, -1, 1, key.u0, key.v0, key.light);
                vertex(out, -1, -1, key.u0, key.v1, key.light);
                mesh = out.end();
            } catch (Throwable failure) {
                allocator.close();
                throw failure;
            }
        }

        private static void vertex(
                VertexConsumer out, float x, float y, float u, float v, int light) {
            out.addVertex(x, y, 0).setUv(u, v).setColor(-1).setLight(light);
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
