package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.PaintingModelCapture;
import java.nio.*;
import java.util.*;
import org.joml.Vector3f;

/** Bounded actual-renderer comparison, explicitly excluded from performance windows. */
public final class PaintingModelProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.paintingVerify");
    private static final Set<Long> models = new HashSet<>();
    private static Set<Long> previousModels = Set.of();
    private static long calls, checked, vertices;
    private static double maxPosition, maxNormal;

    private PaintingModelProbe() {}

    public static void verify(
            PoseStack pose, VertexConsumer consumer, PaintingModelCapture.Renderer renderer) {
        if (!ENABLED || !ExperimentAccess.permitted() || ++calls % 97 != 0 || checked >= 512)
            return;
        try {
            Object owner = RigidModelProbe.field(consumer, "rigidOwner");
            List<?> draws = (List<?>) RigidModelProbe.field(owner, "rigidDraws");
            Object draw = draws.getLast();
            Object entry = RigidModelProbe.field(draw, "entry");
            MeshData local = (MeshData) RigidModelProbe.field(entry, "mesh");
            var layer =
                    (net.minecraft.client.renderer.RenderType)
                            RigidModelProbe.field(entry, "layer");
            try (var allocation = new ByteBufferBuilder(local.drawState().vertexCount() * 128)) {
                var target = new PBRVertexConsumer(allocation, layer);
                renderer.render(pose, target);
                try (MeshData expected = target.end()) {
                    if (expected.drawState().vertexCount() != local.drawState().vertexCount())
                        throw new AssertionError("painting vertex count");
                    var a = local.vertexBuffer().order(ByteOrder.nativeOrder());
                    var b = expected.vertexBuffer().order(ByteOrder.nativeOrder());
                    for (int v = 0; v < local.drawState().vertexCount(); v++) {
                        int at = v * 128;
                        var p =
                                new Vector3f(
                                        a.getFloat(at), a.getFloat(at + 4), a.getFloat(at + 8));
                        pose.last().pose().transformPosition(p);
                        double error =
                                p.distance(b.getFloat(at), b.getFloat(at + 4), b.getFloat(at + 8));
                        maxPosition = Math.max(maxPosition, error);
                        if (error > 3e-5) throw new AssertionError("painting position " + error);
                        var n =
                                new Vector3f(
                                        a.getFloat(at + 16),
                                        a.getFloat(at + 20),
                                        a.getFloat(at + 24));
                        pose.last().normal().transform(n);
                        error =
                                n.distance(
                                        b.getFloat(at + 16),
                                        b.getFloat(at + 20),
                                        b.getFloat(at + 24));
                        maxNormal = Math.max(maxNormal, error);
                        if (error > 5e-5) throw new AssertionError("painting normal " + error);
                        for (int i = 12; i < 128; i++)
                            if (!(i >= 16 && i < 28) && a.get(at + i) != b.get(at + i))
                                throw new AssertionError("painting material byte " + i);
                    }
                    vertices += local.drawState().vertexCount();
                }
            }
            checked++;
            models.add((Long) RigidModelProbe.field(entry, "id"));
        } catch (Throwable failure) {
            throw new IllegalStateException("Painting actual-renderer comparison failed", failure);
        }
    }

    public static void checkpoint(String boundary) {
        if (!ExperimentAccess.permitted()) return;
        com.mojang.logging.LogUtils.getLogger()
                .info(
                        "PAINTING_SOURCE enabled={} compatible={} targetSelection={}",
                        PaintingModelCapture.ENABLED,
                        PaintingModelCapture.sourceCompatible(),
                        System.getProperty(
                                com.radiance.mixin_related.MixinPlugin.PAINTING_SOURCE_PROPERTY));
        if (!ENABLED) return;
        if (checked == 0) throw new IllegalStateException("No painting comparison at " + boundary);
        if (boundary.equals("reload-complete") && !Collections.disjoint(previousModels, models))
            throw new IllegalStateException("Painting model survived resource epoch");
        com.mojang.logging.LogUtils.getLogger()
                .info(
                        "PAINTING_MODEL_PROBE boundary={} checked={} vertices={} models={} maxPosition={} maxNormal={} result=PASS",
                        boundary,
                        checked,
                        vertices,
                        models.size(),
                        maxPosition,
                        maxNormal);
        previousModels = Set.copyOf(models);
        calls = checked = vertices = 0;
        maxPosition = maxNormal = 0;
        models.clear();
    }

    public static long checkedCount() {
        return checked;
    }
}
