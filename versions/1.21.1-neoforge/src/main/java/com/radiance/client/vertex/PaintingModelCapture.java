package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.render.RasterPreviewScope;
import com.radiance.client.texture.TextureTasks;
import java.util.*;
import java.util.function.IntSupplier;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.BlockAndTintGetter;
import org.joml.Matrix4f;

/** Local painting meshes; the original renderer generates each immutable version on a miss. */
public final class PaintingModelCapture {
    public static final boolean ENABLED =
            RigidModelCapture.ENABLED
                    && Boolean.parseBoolean(System.getProperty("radiance.rigidPaintings", "true"));
    public static final int NOT_HANDLED = 0, CAPTURED = 1, FALLBACK_RENDERED = 2;
    private static final int CAPACITY = 512;
    private static final long BYTE_CAPACITY = 16L * 1024 * 1024;
    private static final List<Entry> CACHE = new ArrayList<>();
    private static final ThreadLocal<Replay> REPLAY = new ThreadLocal<>();
    private static long frame;

    private PaintingModelCapture() {}

    @FunctionalInterface
    public interface Renderer {
        void render(PoseStack pose, VertexConsumer consumer);
    }

    static void beginFrame(long currentFrame) {
        frame = currentFrame;
        CACHE.removeIf(
                e -> {
                    if (frame - e.used <= 120) return false;
                    e.close();
                    return true;
                });
    }

    static void invalidate() {
        CACHE.forEach(Entry::close);
        CACHE.clear();
    }

    public static int capture(
            PoseStack pose,
            VertexConsumer consumer,
            Painting painting,
            int width,
            int height,
            TextureAtlasSprite front,
            TextureAtlasSprite back,
            Renderer renderer) {
        if (!ENABLED
                || !sourceCompatible()
                || painting.getClass() != Painting.class
                || consumer.getClass() != PBRVertexConsumer.class
                || RasterPreviewScope.active()
                || !RigidModelCapture.uniformPose(pose.last())
                || width < 1
                || height < 1
                || (long) width * height > 4096
                || front.getClass() != TextureAtlasSprite.class
                || back.getClass() != TextureAtlasSprite.class) return NOT_HANDLED;
        PBRVertexConsumer pbr = (PBRVertexConsumer) consumer;
        if (pbr.rigidOwner == null || !pbr.rigidEligible() || pbr.rigidOwner.rigidOrdinal >= 1024)
            return NOT_HANDLED;
        var texture = TextureProxy.TASKS.owner(pbr.rigidTexture());
        if (texture == null) return NOT_HANDLED;
        var shape =
                new Shape(
                        width,
                        height,
                        front,
                        back,
                        Float.floatToIntBits(front.getU0()),
                        Float.floatToIntBits(front.getU1()),
                        Float.floatToIntBits(front.getV0()),
                        Float.floatToIntBits(front.getV1()),
                        Float.floatToIntBits(back.getU0()),
                        Float.floatToIntBits(back.getU1()),
                        Float.floatToIntBits(back.getV0()),
                        Float.floatToIntBits(back.getV1()));
        var lights =
                new LightSnapshot(
                        width,
                        height,
                        painting.getDirection(),
                        painting.getX(),
                        painting.getY(),
                        painting.getZ());
        // Preserve every source tile-light query, even when its local mesh is resident.
        lights.read(
                (x, y, z) -> LevelRenderer.getLightColor(painting.level(), new BlockPos(x, y, z)));
        Entry found = null;
        for (Entry e : CACHE) {
            if (e.shape.equals(shape)
                    && e.layer == pbr.rigidLayer
                    && e.texture.equals(texture)
                    && Arrays.equals(e.lights, lights.values)) {
                found = e;
                break;
            }
        }
        if (found == null) {
            long required = (long) width * height * 24 * 128;
            long bytes = CACHE.stream().mapToLong(e -> e.bytes).sum();
            while (CACHE.size() >= CAPACITY || bytes + required > BYTE_CAPACITY) {
                Entry oldest =
                        CACHE.stream()
                                .filter(e -> e.used != frame)
                                .min(Comparator.comparingLong(e -> e.used))
                                .orElse(null);
                if (oldest == null) {
                    renderLit(pose, consumer, lights, painting.level(), renderer);
                    return FALLBACK_RENDERED;
                }
                CACHE.remove(oldest);
                bytes -= oldest.bytes;
                oldest.close();
            }
            try {
                found =
                        new Entry(
                                shape, pbr.rigidLayer, texture, lights, painting.level(), renderer);
            } catch (SourceMismatch changedSource) {
                // An altered helper must retain its complete original rendering path.
                return NOT_HANDLED;
            }
            CACHE.add(found);
        }
        found.used = frame;
        var owner = pbr.rigidOwner;
        if (owner.rigidOrdinal >= 1024) {
            renderLit(pose, consumer, lights, painting.level(), renderer);
            return FALLBACK_RENDERED;
        }
        owner.rigidDraws.add(
                new RigidModelCapture.Draw(
                                found,
                                new Matrix4f(pose.last().pose()),
                                owner.rigidInstance,
                                owner.rigidOrdinal++)
                        .capturedBy(pbr));
        return CAPTURED;
    }

    public static boolean sourceCompatible() {
        return SourceCapability.COMPATIBLE;
    }

    private static final class SourceCapability {
        private static final boolean COMPATIBLE =
                "independent"
                        .equals(
                                System.getProperty(
                                        com.radiance.mixin_related.MixinPlugin
                                                .PAINTING_SOURCE_PROPERTY));
    }

    static void renderLit(
            PoseStack pose,
            VertexConsumer consumer,
            LightSnapshot lights,
            BlockAndTintGetter world,
            Renderer renderer) {
        Replay previous = REPLAY.get(), replay = new Replay(lights, world);
        try {
            REPLAY.set(replay);
            renderer.render(pose, consumer);
            if (replay.index != lights.values.length)
                throw new SourceMismatch("Incomplete painting tile-light capture");
        } finally {
            if (previous == null) REPLAY.remove();
            else REPLAY.set(previous);
        }
    }

    // Scoped only to original painting generation. Nested callers retain their own replay.
    public static int light(BlockAndTintGetter world, BlockPos position, IntSupplier original) {
        Replay replay = REPLAY.get();
        if (replay == null) return original.getAsInt();
        return replay.next(world, position);
    }

    record Shape(
            int width,
            int height,
            TextureAtlasSprite front,
            TextureAtlasSprite back,
            int fu0,
            int fu1,
            int fv0,
            int fv1,
            int bu0,
            int bu1,
            int bv0,
            int bv1) {}

    @FunctionalInterface
    interface LightReader {
        int read(int x, int y, int z);
    }

    static final class LightSnapshot {
        final int width, height;
        final Direction direction;
        final double x, y, z;
        final int[] values;

        LightSnapshot(int width, int height, Direction direction, double x, double y, double z) {
            this.width = width;
            this.height = height;
            this.direction = direction;
            this.x = x;
            this.y = y;
            this.z = z;
            values = new int[Math.multiplyExact(width, height)];
        }

        int coordinate(int index, int axis) {
            int i = index / height, j = index % height;
            float f = -width / 2.0f, f1 = -height / 2.0f;
            float a = f + (i + 1), b = f + i;
            float c = f1 + (j + 1), d = f1 + j;
            double px = x, pz = z;
            if (direction == Direction.NORTH) px = x + (a + b) / 2.0f;
            if (direction == Direction.SOUTH) px = x - (a + b) / 2.0f;
            if (direction == Direction.WEST) pz = z - (a + b) / 2.0f;
            if (direction == Direction.EAST) pz = z + (a + b) / 2.0f;
            return (int) Math.floor(axis == 0 ? px : axis == 1 ? y + (c + d) / 2.0f : pz);
        }

        void read(LightReader reader) {
            for (int i = 0; i < values.length; i++)
                values[i] = reader.read(coordinate(i, 0), coordinate(i, 1), coordinate(i, 2));
        }
    }

    static final class Replay {
        final LightSnapshot lights;
        final BlockAndTintGetter world;
        int index;

        Replay(LightSnapshot lights, BlockAndTintGetter world) {
            this.lights = lights;
            this.world = world;
        }

        int next(BlockAndTintGetter actualWorld, BlockPos pos) {
            if (world != actualWorld
                    || index >= lights.values.length
                    || pos.getX() != lights.coordinate(index, 0)
                    || pos.getY() != lights.coordinate(index, 1)
                    || pos.getZ() != lights.coordinate(index, 2))
                throw new SourceMismatch("Painting source light-query contract changed");
            return lights.values[index++];
        }
    }

    private static final class SourceMismatch extends IllegalStateException {
        SourceMismatch(String message) {
            super(message);
        }
    }

    private static final class Entry implements AutoCloseable, RigidModelCapture.Geometry {
        final long id = RigidModelCapture.nextResourceId();
        final Shape shape;
        final RenderType layer;
        final TextureTasks.Owner texture;
        final int[] lights;
        final ByteBufferBuilder allocator;
        final MeshData mesh;
        final long bytes;
        long used;

        Entry(
                Shape shape,
                RenderType layer,
                TextureTasks.Owner texture,
                LightSnapshot lights,
                BlockAndTintGetter world,
                Renderer renderer) {
            this.shape = shape;
            this.layer = layer;
            this.texture = texture;
            this.lights = lights.values.clone();
            bytes = (long) shape.width * shape.height * 24 * 128;
            allocator = new ByteBufferBuilder((int) bytes);
            try {
                PBRVertexConsumer target = new PBRVertexConsumer(allocator, layer);
                renderLit(new PoseStack(), target, lights, world, renderer);
                mesh = target.end();
                if (mesh.drawState().vertexCount() != shape.width * shape.height * 24) {
                    mesh.close();
                    throw new SourceMismatch("Painting source topology changed");
                }
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

        public MeshData mesh() {
            return mesh;
        }

        public void close() {
            mesh.close();
            allocator.close();
        }
    }
}
