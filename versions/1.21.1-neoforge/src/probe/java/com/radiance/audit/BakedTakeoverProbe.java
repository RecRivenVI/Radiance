package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.nio.ByteOrder;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

/** Independent source expansion is enabled only for the functional comparison. */
public final class BakedTakeoverProbe {
    private static long calls, checks, vertices;
    private static double positionError, normalError;

    private BakedTakeoverProbe() {}

    public static int pending(VertexConsumer consumer) {
        if (consumer instanceof PBRVertexConsumer.GLint decorated) consumer = decorated.delegate();
        if (!(consumer instanceof PBRVertexConsumer)) return -1;
        try {
            var owner = RigidModelProbe.field(consumer, "rigidOwner");
            return owner == null
                    ? -1
                    : ((java.util.List<?>) RigidModelProbe.field(owner, "rigidDraws")).size();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static void compare(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            java.util.function.Consumer<VertexConsumer> source,
            int before) {
        if (!Boolean.getBoolean("radiance.audit.takeoverCompare")
                || !ExperimentAccess.permitted()
                || ++calls % 23 != 0
                || checks >= 1024) return;
        var glint =
                consumer instanceof PBRVertexConsumer.GLint decorated
                        ? decorated.sourceLayer()
                        : null;
        if (consumer instanceof PBRVertexConsumer.GLint decorated) consumer = decorated.delegate();
        try {
            var owner = RigidModelProbe.field(consumer, "rigidOwner");
            var draws = (java.util.List<?>) RigidModelProbe.field(owner, "rigidDraws");
            if (before < 0 || draws.size() == before)
                return; // Empty source or a fully emitted capacity fallback.
            if (draws.size() != before + 1)
                throw new AssertionError("Independent source submission ownership");
            var entry = RigidModelProbe.field(draws.getLast(), "entry");
            var local = (MeshData) RigidModelProbe.field(entry, "mesh");
            var layer = (RenderType) RigidModelProbe.field(entry, "layer");
            try (var storage = new ByteBufferBuilder(local.drawState().vertexCount() * 128)) {
                var reference = new PBRVertexConsumer(storage, layer);
                source.accept(
                        glint == null ? reference : new PBRVertexConsumer.GLint(reference, glint));
                try (var expected = reference.end()) {
                    if (local.drawState().vertexCount() != expected.drawState().vertexCount())
                        throw new AssertionError("actual baked-source topology");
                    var a = local.vertexBuffer().order(ByteOrder.nativeOrder());
                    var b = expected.vertexBuffer().order(ByteOrder.nativeOrder());
                    for (int v = 0; v < local.drawState().vertexCount(); v++) {
                        int at = v * 128;
                        var p =
                                new Vector3f(
                                        a.getFloat(at), a.getFloat(at + 4), a.getFloat(at + 8));
                        pose.pose().transformPosition(p);
                        double error =
                                p.distance(b.getFloat(at), b.getFloat(at + 4), b.getFloat(at + 8));
                        positionError = Math.max(positionError, error);
                        if (error > 3e-5)
                            throw new AssertionError("actual baked-source position " + error);
                        var n =
                                new Vector3f(
                                        a.getFloat(at + 16),
                                        a.getFloat(at + 20),
                                        a.getFloat(at + 24));
                        pose.normal().transform(n);
                        error =
                                n.distance(
                                        b.getFloat(at + 16),
                                        b.getFloat(at + 20),
                                        b.getFloat(at + 24));
                        normalError = Math.max(normalError, error);
                        if (error > 5e-5)
                            throw new AssertionError("actual baked-source normal " + error);
                        for (int i = 12; i < 128; i++) {
                            if (i >= 16 && i < 28) continue;
                            if (a.get(at + i) != b.get(at + i))
                                throw new AssertionError("actual baked-source material byte " + i);
                        }
                    }
                    vertices += local.drawState().vertexCount();
                    checks++;
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static java.util.Map<String, Object> results() {
        return java.util.Map.of(
                "calls",
                calls,
                "checks",
                checks,
                "vertices",
                vertices,
                "maxPositionError",
                positionError,
                "maxNormalError",
                normalError);
    }
}
