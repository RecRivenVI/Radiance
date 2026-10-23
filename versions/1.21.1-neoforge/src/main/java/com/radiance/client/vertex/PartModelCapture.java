package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.RasterPreviewScope;
import com.radiance.client.texture.TextureTasks;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Source-declared local ModelPart geometry. Pose traversal and unsupported consumers stay original.
 */
public final class PartModelCapture {
    public static final boolean ENABLED =
            RigidModelCapture.ENABLED
                    && Boolean.parseBoolean(System.getProperty("radiance.rigidParts", "true"));
    private static final long BYTE_CAPACITY = 16L * 1024 * 1024;
    private static final long SOURCE_BYTE_CAPACITY = 8L * 1024 * 1024;
    private static final IdentityHashMap<Object, Source> SOURCES = new IdentityHashMap<>();
    private static final List<Entry> CACHE = new ArrayList<>();
    private static final Map<VersionKey, Entry> VERSIONS = new HashMap<>();
    private static final IdentityHashMap<Object, Owner> OWNERS = new IdentityHashMap<>();
    private static Object level;
    private static long reload, frame, nextPart, nextOwner, bytes, sourceBytes;
    private static long registrations, builds, draws;

    /** Set only by the real PartDefinition baker; no renderer/class-name stability inference. */
    public interface SourceAccess {
        void radiance$declarePart();

        boolean radiance$declaredPart();

        List<ModelPart.Cube> radiance$cubes();

        Map<String, ModelPart> radiance$children();
    }

    public interface SpriteAccess {
        VertexConsumer radiance$delegate();

        TextureAtlasSprite radiance$sprite();
    }

    private record VersionKey(
            Snapshot source,
            RenderType layer,
            TextureTasks.Owner texture,
            TextureAtlasSprite sprite,
            com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy,
            int light,
            int overlay,
            int color,
            RenderType glint,
            TextureTasks.Owner glintTexture,
            PBRMaterialContext.State context) {}

    private record Binding(RenderType layer, TextureAtlasSprite sprite) {}

    private static final class Owner {
        final long id = ++nextOwner;
        long used;
    }

    public static void declare(ModelPart part) {
        if ((Object) part instanceof SourceAccess source) source.radiance$declarePart();
    }

    public static boolean sourceCompatible() {
        return com.radiance.mixin_related.SourceCompatibility.compatible(
                com.radiance.mixin_related.SourceCompatibility.Source.PARTS);
    }

    /** Separate history namespace for block entities; object hashes are not resource identities. */
    static long blockOwner(Object value) {
        Owner owner = OWNERS.get(value);
        if (owner == null) {
            if (OWNERS.size() >= 8192 || nextOwner >= 0xffff_ffffL) return -1;
            owner = new Owner();
            OWNERS.put(value, owner);
        }
        owner.used = frame;
        return owner.id;
    }

    private PartModelCapture() {}

    public static long[] counters() {
        return new long[] {
            registrations, builds, draws, SOURCES.size(), CACHE.size(), sourceBytes, bytes
        };
    }

    static void invalidate() {
        CACHE.forEach(Entry::close);
        CACHE.clear();
        VERSIONS.clear();
        SOURCES.clear();
        OWNERS.clear();
        bytes = sourceBytes = 0;
        level = null;
        BoundPartCapture.invalidate();
    }

    static void beginFrame(Object currentLevel, long epoch) {
        if (!ENABLED) return;
        if (level != currentLevel || reload != epoch) {
            invalidate();
            level = currentLevel;
            reload = epoch;
        }
        ++frame;
        BoundPartCapture.beginFrame(frame);
        CACHE.removeIf(
                e -> {
                    if (frame - e.used <= 120) return false;
                    VERSIONS.remove(e.key);
                    bytes -= (long) e.snapshot.vertices() * 128;
                    e.close();
                    return true;
                });
        SOURCES.values()
                .removeIf(
                        s -> {
                            if (frame - s.used <= 120) return false;
                            sourceBytes -= s.snapshot.data.length * 4L;
                            return true;
                        });
        OWNERS.values().removeIf(o -> frame - o.used > 120);
    }

    public static boolean capture(
            Object part,
            List<ModelPart.Cube> cubes,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            int color) {
        if (!ENABLED
                || cubes.isEmpty()
                || part.getClass() != ModelPart.class
                || !sourceCompatible()
                || !((Object) part instanceof SourceAccess sourceAccess)
                || !sourceAccess.radiance$declaredPart()
                || RasterPreviewScope.active()
                || !RigidModelCapture.uniformPose(pose)) return false;
        TextureAtlasSprite sprite = null;
        if (consumer.getClass() == SpriteCoordinateExpander.class
                && consumer instanceof SpriteAccess expanded) {
            sprite = expanded.radiance$sprite();
            consumer = expanded.radiance$delegate();
        }
        if (consumer.getClass() != PBRVertexConsumer.class) return false;
        PBRVertexConsumer pbr = (PBRVertexConsumer) consumer;
        if (pbr.rigidOwner == null || !pbr.rigidOwner.partCaptureEnabled || !pbr.rigidEligible())
            return false;
        var owner = pbr.rigidOwner;
        TextureTasks.Owner texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null) return false;

        Source source = localSource(part, cubes);
        if (source == null) return false;
        PartIdentity identity = source.identity(new Binding(pbr.rigidLayer, sprite), frame);
        if (identity == null) return false;
        var state = owner.partState();
        int occurrence = state.occurrences.getOrDefault(identity, 0);
        if (occurrence >= 32) return false;
        Entry found =
                localVersion(
                        source.snapshot,
                        pbr.rigidLayer,
                        texture,
                        sprite,
                        pbr.rigidPolicy(),
                        light,
                        overlay,
                        color);
        if (found == null) return false;
        state.occurrences.put(identity, occurrence + 1);
        owner.rigidDraws.add(
                new RigidModelCapture.Draw(
                                found,
                                new Matrix4f(pose.pose()),
                                historyIdentity(owner.rigidInstance, identity.id, occurrence)
                                        | owner.partHistoryDomain)
                        .capturedBy(pbr));
        ++draws;
        return true;
    }

    static Source localSource(Object part, List<ModelPart.Cube> cubes) {
        Source source = SOURCES.get(part);
        if (source == null || !source.snapshot.matchesStructure(cubes)) {
            long oldBytes = source == null ? 0 : source.snapshot.data.length * 4L;
            long requiredSource = Snapshot.requiredBytes(cubes);
            if (requiredSource <= 0
                    || sourceBytes - oldBytes + requiredSource > SOURCE_BYTE_CAPACITY) return null;
            Snapshot snapshot = Snapshot.read(cubes);
            if (snapshot == null || snapshot.vertices() == 0) return null;
            // A changed local topology gets fresh part identities, even at equal vertex count.
            source = new Source(snapshot);
            SOURCES.put(part, source);
            ++registrations;
            sourceBytes += snapshot.data.length * 4L - oldBytes;
        }
        source.used = frame;
        return source;
    }

    static Entry localVersion(
            Snapshot snapshot,
            RenderType layer,
            TextureTasks.Owner texture,
            TextureAtlasSprite sprite,
            com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy,
            int light,
            int overlay,
            int color) {
        return localVersion(
                snapshot, layer, texture, sprite, policy, light, overlay, color, null, null);
    }

    static Entry localVersion(
            Snapshot snapshot,
            RenderType layer,
            TextureTasks.Owner texture,
            TextureAtlasSprite sprite,
            com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy,
            int light,
            int overlay,
            int color,
            RenderType glint,
            TextureTasks.Owner glintTexture) {
        VersionKey key =
                new VersionKey(
                        snapshot,
                        layer,
                        texture,
                        sprite,
                        policy,
                        light,
                        overlay,
                        color,
                        glint,
                        glintTexture,
                        PBRMaterialContext.snapshot());
        Entry found = VERSIONS.get(key);
        if (found == null) {
            long required = (long) snapshot.vertices() * 128;
            if (required > BYTE_CAPACITY) return null;
            while (bytes + required > BYTE_CAPACITY) {
                Entry oldest =
                        CACHE.stream()
                                .filter(e -> e.used != frame)
                                .min(Comparator.comparingLong(e -> e.used))
                                .orElse(null);
                if (oldest == null) return null;
                CACHE.remove(oldest);
                VERSIONS.remove(oldest.key);
                oldest.close();
                bytes -= (long) oldest.snapshot.vertices() * 128;
            }
            found =
                    new Entry(
                            snapshot,
                            layer,
                            texture,
                            light,
                            overlay,
                            color,
                            sprite,
                            policy,
                            glint,
                            glintTexture);
            CACHE.add(found);
            ++builds;
            VERSIONS.put(key, found);
            bytes += required;
        }
        found.used = frame;
        return found;
    }

    // Separate from baked-model count/ordinal keys. Removing another part cannot transfer its
    // history.
    static long historyIdentity(long owner, long part, int occurrence) {
        if (owner < 0
                || owner > 0xffff_ffffL
                || part < 1
                || part > 0xff_ffffL
                || occurrence < 0
                || occurrence >= 32) throw new IllegalArgumentException("Part history identity");
        return Long.MIN_VALUE | (1L << 61) | (owner << 29) | (part << 5) | occurrence;
    }

    static final class PartIdentity {
        final long id;
        long used;

        PartIdentity(long id) {
            this.id = id;
        }
    }

    static final class Source {
        final Map<Object, PartIdentity> layers = new HashMap<>();
        final Snapshot snapshot;
        net.minecraft.world.phys.shapes.VoxelShape outline;
        long used;

        Source(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        PartIdentity identity(Object layer, long currentFrame) {
            PartIdentity identity = layers.get(layer);
            if (identity == null) {
                // Dynamic RenderTypes must not grow the history map indefinitely.
                layers.values().removeIf(id -> currentFrame - id.used > 120);
                if (layers.size() >= 64 || nextPart >= 0xff_ffffL) return null;
                identity = new PartIdentity(++nextPart);
                layers.put(layer, identity);
            }
            identity.used = currentFrame;
            return identity;
        }
    }

    public static void outline(
            ModelPart part,
            PoseStack.Pose pose,
            StorageOutlineVertexConsumerProvider.WorldOutlineVertexConsumer consumer) {
        var cubes = ((SourceAccess) (Object) part).radiance$cubes();
        if (cubes.isEmpty()) return;
        var source = ENABLED ? localSource(part, cubes) : null;
        var shape = source == null ? null : source.outline;
        if (shape == null) {
            shape = net.minecraft.world.phys.shapes.Shapes.empty();
            for (var cube : cubes)
                shape =
                        net.minecraft.world.phys.shapes.Shapes.or(
                                shape,
                                net.minecraft.world.phys.shapes.Shapes.create(
                                        new net.minecraft.world.phys.AABB(
                                                cube.minX / 16F,
                                                cube.minY / 16F,
                                                cube.minZ / 16F,
                                                cube.maxX / 16F,
                                                cube.maxY / 16F,
                                                cube.maxZ / 16F)));
            if (source != null) source.outline = shape;
        }
        com.radiance.client.render.WorldOutlineGeometry.emitShapeEdges(
                pose, consumer.outlineDelegate(), shape, consumer.outlineColor());
    }

    /** Local geometry declared by a qualified baker; only structural identities are checked hot. */
    static final class Snapshot {
        final float[] data; // per quad: normal xyz; four vertices each xyz (model units), uv
        final ModelPart.Cube[] cubeIdentities;

        Snapshot(float[] data, ModelPart.Cube[] cubeIdentities) {
            this.data = data;
            this.cubeIdentities = cubeIdentities;
        }

        int vertices() {
            return data.length / 23 * 4;
        }

        static long requiredBytes(List<ModelPart.Cube> cubes) {
            long count = 0;
            for (var cube : cubes) {
                if (cube == null || cube.getClass() != ModelPart.Cube.class) return -1;
                count += cube.polygons.length;
                if (count > 32768) return -1;
            }
            return count * 23 * 4;
        }

        boolean matchesStructure(List<ModelPart.Cube> cubes) {
            if (cubeIdentities == null || cubes.size() != cubeIdentities.length) return false;
            for (int i = 0; i < cubeIdentities.length; ++i)
                if (cubes.get(i) != cubeIdentities[i]) return false;
            return true;
        }

        static Snapshot read(List<ModelPart.Cube> cubes) {
            int count = 0;
            for (var cube : cubes) {
                if (cube == null || cube.getClass() != ModelPart.Cube.class) return null;
                count += cube.polygons.length;
                if (count > 32768) return null;
            }
            float[] data = new float[count * 23];
            int at = 0;
            for (var cube : cubes) {
                for (var polygon : cube.polygons) {
                    if (polygon == null || polygon.vertices.length != 4) return null;
                    data[at++] = polygon.normal.x;
                    data[at++] = polygon.normal.y;
                    data[at++] = polygon.normal.z;
                    for (var v : polygon.vertices) {
                        if (v == null) return null;
                        data[at++] = v.pos.x;
                        data[at++] = v.pos.y;
                        data[at++] = v.pos.z;
                        data[at++] = v.u;
                        data[at++] = v.v;
                    }
                }
            }
            for (float value : data) if (!Float.isFinite(value)) return null;
            return new Snapshot(data, cubes.toArray(ModelPart.Cube[]::new));
        }

        void emit(VertexConsumer consumer, PoseStack.Pose pose, int light, int overlay, int color) {
            Vector3f temporary = new Vector3f();
            for (int at = 0; at < data.length; ) {
                pose.transformNormal(data[at++], data[at++], data[at++], temporary);
                float nx = temporary.x, ny = temporary.y, nz = temporary.z;
                for (int v = 0; v < 4; ++v) {
                    pose.pose()
                            .transformPosition(
                                    data[at++] / 16, data[at++] / 16, data[at++] / 16, temporary);
                    float u = data[at++], uv = data[at++];
                    consumer.addVertex(
                            temporary.x,
                            temporary.y,
                            temporary.z,
                            color,
                            u,
                            uv,
                            overlay,
                            light,
                            nx,
                            ny,
                            nz);
                }
            }
        }
    }

    static final class Entry implements RigidModelCapture.Geometry, AutoCloseable {
        final long id = RigidModelCapture.nextResourceId();
        final Snapshot snapshot;
        final RenderType layer;
        final TextureTasks.Owner texture;
        final TextureTasks.Owner glintTexture;
        final int light, overlay, color;
        final TextureAtlasSprite sprite;
        final com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy;
        final VersionKey key;
        final ByteBufferBuilder allocator;
        final MeshData mesh;
        long used;

        Entry(
                Snapshot snapshot,
                RenderType layer,
                TextureTasks.Owner texture,
                int light,
                int overlay,
                int color) {
            this(snapshot, layer, texture, light, overlay, color, null);
        }

        Entry(
                Snapshot snapshot,
                RenderType layer,
                TextureTasks.Owner texture,
                int light,
                int overlay,
                int color,
                TextureAtlasSprite sprite) {
            this(
                    snapshot,
                    layer,
                    texture,
                    light,
                    overlay,
                    color,
                    sprite,
                    com.radiance.render.contract.SurfaceMaterial.ordinaryPolicy(
                            PBRVertexConsumer.getAlphaMode(layer)));
        }

        Entry(
                Snapshot snapshot,
                RenderType layer,
                TextureTasks.Owner texture,
                int light,
                int overlay,
                int color,
                TextureAtlasSprite sprite,
                com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy) {
            this(snapshot, layer, texture, light, overlay, color, sprite, policy, null, null);
        }

        Entry(
                Snapshot snapshot,
                RenderType layer,
                TextureTasks.Owner texture,
                int light,
                int overlay,
                int color,
                TextureAtlasSprite sprite,
                com.radiance.render.contract.SurfaceMaterial.MaterialPolicy policy,
                RenderType glint,
                TextureTasks.Owner glintTexture) {
            this.snapshot = snapshot;
            this.layer = layer;
            this.texture = texture;
            this.glintTexture = glintTexture;
            this.light = light;
            this.overlay = overlay;
            this.color = color;
            this.sprite = sprite;
            this.policy = policy;
            key =
                    new VersionKey(
                            snapshot,
                            layer,
                            texture,
                            sprite,
                            policy,
                            light,
                            overlay,
                            color,
                            glint,
                            glintTexture,
                            PBRMaterialContext.snapshot());
            allocator = new ByteBufferBuilder(snapshot.vertices() * 128);
            try {
                PBRVertexConsumer consumer =
                        PBRVertexConsumer.dynamicMaterial(allocator, layer, false, policy);
                VertexConsumer decorated =
                        glint == null ? consumer : new PBRVertexConsumer.GLint(consumer, glint);
                snapshot.emit(
                        sprite == null ? decorated : sprite.wrap(decorated),
                        new PoseStack().last(),
                        light,
                        overlay,
                        color);
                mesh = consumer.end();
            } catch (Throwable failure) {
                allocator.close();
                throw failure;
            }
        }

        public long id() {
            return id;
        }

        public RenderType layer() {
            return layer;
        }

        public TextureTasks.Owner texture() {
            return texture;
        }

        public List<TextureTasks.Owner> textures() {
            return glintTexture == null ? List.of(texture) : List.of(texture, glintTexture);
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
