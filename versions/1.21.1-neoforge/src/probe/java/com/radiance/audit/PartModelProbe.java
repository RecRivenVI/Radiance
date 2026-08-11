package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

/** Optional real Cube.compile comparison, never enabled in formal performance samples. */
public final class PartModelProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.partVerify");
    private static long calls, checked, vertices;
    private static double maxPosition, maxNormal;
    private static final Set<Long> models = new HashSet<>();
    private static final Map<String, Long> producers = new TreeMap<>();
    private static long targetOwner = -1;
    private static boolean precise;
    private static long capturedVertices;

    @FunctionalInterface
    interface Verification {
        void run() throws ReflectiveOperationException;
    }

    static void precise(Verification work) throws ReflectiveOperationException {
        boolean previous = precise;
        precise = Boolean.getBoolean("radiance.audit.partVerifyAll");
        try {
            work.run();
        } finally {
            precise = previous;
        }
    }

    private PartModelProbe() {}

    public static void compile(
            List<ModelPart.Cube> cubes,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            int color,
            Runnable original) {
        VertexConsumer leaf = consumer;
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = null;
        if (consumer.getClass() == net.minecraft.client.renderer.SpriteCoordinateExpander.class
                && consumer
                        instanceof
                        com.radiance.client.vertex.PartModelCapture.SpriteAccess expanded) {
            leaf = expanded.radiance$delegate();
            sprite = expanded.radiance$sprite();
        }
        if (!ENABLED
                || leaf.getClass() != PBRVertexConsumer.class
                || (++calls % 89 != 0 && !precise)
                || checked >= (Boolean.getBoolean("radiance.audit.partVerifyAll") ? 30000 : 4096)) {
            original.run();
            return;
        }
        try {
            Object owner = RigidModelProbe.field(leaf, "rigidOwner");
            if (owner == null) {
                original.run();
                return;
            }
            if (targetOwner >= 0
                    && (long) RigidModelProbe.field(owner, "rigidInstance") != targetOwner) {
                original.run();
                return;
            }
            List<?> draws = (List<?>) RigidModelProbe.field(owner, "rigidDraws");
            int before = draws.size();
            original.run();
            if (draws.size() == before) return; // Explicit fallback, not a cache success.
            if (draws.size() != before + 1) throw new AssertionError("Part draw count");
            Object entry = RigidModelProbe.field(draws.getLast(), "entry");
            MeshData mesh = (MeshData) RigidModelProbe.field(entry, "mesh");
            RenderType layer = (RenderType) RigidModelProbe.field(entry, "layer");
            try (ByteBufferBuilder allocation =
                    new ByteBufferBuilder(mesh.drawState().vertexCount() * 128)) {
                PBRVertexConsumer reference =
                        PBRVertexConsumer.dynamicMaterial(
                                allocation,
                                layer,
                                false,
                                (com.radiance.render.contract.SurfaceMaterial.MaterialPolicy)
                                        RigidModelProbe.field(leaf, "materialPolicy"));
                VertexConsumer sourceConsumer = sprite == null ? reference : sprite.wrap(reference);
                for (ModelPart.Cube cube : cubes)
                    cube.compile(pose, sourceConsumer, light, overlay, color);
                try (MeshData expected = reference.end()) {
                    if (expected.drawState().vertexCount() != mesh.drawState().vertexCount())
                        throw new AssertionError("Part vertices");
                    ByteBuffer a = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                    ByteBuffer b = expected.vertexBuffer().order(ByteOrder.nativeOrder());
                    for (int i = 0; i < mesh.drawState().vertexCount(); i++) {
                        int at = i * 128;
                        Vector3f p =
                                new Vector3f(
                                        a.getFloat(at), a.getFloat(at + 4), a.getFloat(at + 8));
                        pose.pose().transformPosition(p);
                        double error =
                                p.distance(b.getFloat(at), b.getFloat(at + 4), b.getFloat(at + 8));
                        maxPosition = Math.max(maxPosition, error);
                        if (error > 3e-5) throw new AssertionError("Part position=" + error);
                        Vector3f n =
                                new Vector3f(
                                        a.getFloat(at + 16),
                                        a.getFloat(at + 20),
                                        a.getFloat(at + 24));
                        pose.transformNormal(n, n);
                        error =
                                n.distance(
                                        b.getFloat(at + 16),
                                        b.getFloat(at + 20),
                                        b.getFloat(at + 24));
                        maxNormal = Math.max(maxNormal, error);
                        if (error > 5e-5) throw new AssertionError("Part normal=" + error);
                        for (int j = 12; j < 128; j++)
                            if ((j < 16 || j >= 28) && a.get(at + j) != b.get(at + j))
                                throw new AssertionError("Part material byte=" + j);
                    }
                    checked++;
                    vertices += mesh.drawState().vertexCount();
                    models.add((Long) RigidModelProbe.field(entry, "id"));
                    Object producer = RigidModelProbe.field(owner, "partProducer");
                    producers.merge(String.valueOf(producer), 1L, Long::sum);
                }
            }
            if (checked % 32 == 0) write("PASS");
        } catch (Throwable failure) {
            write("FAIL " + failure);
            throw new IllegalStateException("Part geometry comparison", failure);
        }
    }

    public static long checked() {
        return checked;
    }

    public static Map<String, Object> results() {
        return Map.of(
                "checks",
                checked,
                "vertices",
                vertices,
                "resources",
                models.size(),
                "maxPositionError",
                maxPosition,
                "maxNormalError",
                maxNormal,
                "producers",
                Map.copyOf(producers));
    }

    public static void target(int entity) {
        targetOwner = Integer.toUnsignedLong(entity);
        checked = 0;
        vertices = 0;
        models.clear();
        producers.clear();
        maxPosition = maxNormal = 0;
        capturedVertices = 0;
    }

    public static void captured(
            int owner,
            com.radiance.client.proxy.world.EntityProxy.EntityRenderDataList data,
            int first) {
        if (!ENABLED || targetOwner < 0) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity((int) targetOwner);
        if (entity == null || owner != System.identityHashCode(entity)) return;
        for (int i = first; i < data.size(); i++)
            for (var layer : data.get(i))
                capturedVertices += layer.builtBuffer().drawState().vertexCount();
    }

    public static void capturedCheckpoint(String stage) {
        if (checked != 0 || capturedVertices == 0)
            throw new IllegalStateException(
                    "Expected complete-owner capture at "
                            + stage
                            + ": direct="
                            + checked
                            + ", capturedVertices="
                            + capturedVertices);
        write("PASS captured stage=" + stage + " capturedVertices=" + capturedVertices);
        try {
            Files.copy(
                    Path.of("radiance-audit/part-model-check.txt"),
                    Path.of("radiance-audit/part-model-" + stage + ".txt"));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        capturedVertices = 0;
    }

    public static void checkpoint(String stage) {
        if (checked == 0) throw new IllegalStateException("No part draw compared at " + stage);
        write("PASS stage=" + stage);
        try {
            Files.copy(
                    Path.of("radiance-audit/part-model-check.txt"),
                    Path.of("radiance-audit/part-model-" + stage + ".txt"));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        checked = 0;
        vertices = 0;
        models.clear();
        maxPosition = maxNormal = 0;
        capturedVertices = 0;
    }

    private static void write(String status) {
        try {
            Files.createDirectories(Path.of("radiance-audit"));
            Files.writeString(
                    Path.of("radiance-audit/part-model-check.txt"),
                    status
                            + "\nchecked="
                            + checked
                            + "\nvertices="
                            + vertices
                            + "\nmodels="
                            + models.size()
                            + "\nmaxPosition="
                            + maxPosition
                            + "\nmaxNormal="
                            + maxNormal
                            + "\nproducers="
                            + producers
                            + "\nReference: actual Cube.compile; not GPU hit or user visual parity.\n");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
