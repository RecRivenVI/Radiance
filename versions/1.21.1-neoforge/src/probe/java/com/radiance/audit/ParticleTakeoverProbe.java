package com.radiance.audit;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.nio.*;
import java.util.*;
import net.minecraft.client.particle.SingleQuadParticle;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Actual original SingleQuadParticle generator versus the shared-base instance input. */
public final class ParticleTakeoverProbe {
    private static final Map<String, Long> sources = new TreeMap<>();
    private static long checked, vertices;
    private static double maxPosition;
    private static net.minecraft.client.Camera rendererCamera;
    private static int fullRendered, fullExpected;

    public static void fullRender(net.minecraft.client.Camera camera, Runnable submit) {
        rendererCamera = camera;
        fullRendered = fullExpected = 0;
        try {
            submit.run();
            if (fullRendered != fullExpected)
                throw new AssertionError(
                        "Particle full-render quad count " + fullRendered + "/" + fullExpected);
        } finally {
            rendererCamera = null;
        }
    }

    private ParticleTakeoverProbe() {}

    public static void compare(
            SingleQuadParticle particle,
            VertexConsumer consumer,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float tick,
            Runnable submit) {
        try {
            var owner = RigidModelProbe.field(consumer, "particleOwner");
            var draws = (List<?>) RigidModelProbe.field(owner, "rigidDraws");
            int before = draws.size();
            submit.run();
            if (draws.size() == before) return; // Source zero-area quad.
            String source = particle.getClass().getName();
            var draw = draws.getLast();
            var matrix = (Matrix4f) RigidModelProbe.field(draw, "transform");
            if (!matrix.isFinite())
                throw new AssertionError(
                        "Particle transform source="
                                + source
                                + " rotation="
                                + rotation
                                + " matrix="
                                + matrix);
            if (sources.getOrDefault(source, 0L) >= 128) return;
            int quadIndex = rendererCamera == null ? 0 : fullRendered++;
            var entry = RigidModelProbe.field(draw, "entry");
            var key = RigidModelProbe.field(entry, "key");
            var layer =
                    (net.minecraft.client.renderer.RenderType) RigidModelProbe.field(key, "layer");
            MeshData mesh = (MeshData) RigidModelProbe.field(entry, "mesh");
            float[] colour = (float[]) RigidModelProbe.field(draw, "particleColour");
            try (var allocation = new ByteBufferBuilder(512)) {
                var reference =
                        PBRVertexConsumer.dynamicMaterial(
                                allocation,
                                layer,
                                false,
                                (com.radiance.render.contract.SurfaceMaterial.MaterialPolicy)
                                        RigidModelProbe.field(consumer, "materialPolicy"));
                reference.setDefaultAlbedoEmission(
                        (float) RigidModelProbe.field(owner, "defaultAlbedoEmission"));
                var original =
                        SingleQuadParticle.class.getDeclaredMethod(
                                "renderRotatedQuad",
                                VertexConsumer.class,
                                Quaternionf.class,
                                float.class,
                                float.class,
                                float.class,
                                float.class);
                original.setAccessible(true);
                if (rendererCamera != null) particle.render(reference, rendererCamera, tick);
                else original.invoke(particle, reference, rotation, x, y, z, tick);
                try (MeshData expected = reference.end()) {
                    var a = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                    var b = expected.vertexBuffer().order(ByteOrder.nativeOrder());
                    if (mesh.drawState().vertexCount() != 4
                            || expected.drawState().vertexCount() % 4 != 0
                            || expected.drawState().vertexCount() < 4 * (quadIndex + 1))
                        throw new AssertionError("Particle quad vertex count");
                    if (rendererCamera != null)
                        fullExpected = expected.drawState().vertexCount() / 4;
                    int colourOffset =
                            com.radiance.client.vertex.PBRVertexFormats.PBR_TRIANGLE
                                    .getOffsetsByElement()[
                                    com.radiance.client.vertex.PBRVertexFormatElements
                                            .PBR_COLOR_LAYER
                                            .id()];
                    for (int i = 0; i < 4; i++) {
                        int at = i * 128;
                        int bt = (quadIndex * 4 + i) * 128;
                        var p =
                                matrix.transformPosition(
                                        new Vector3f(
                                                a.getFloat(at),
                                                a.getFloat(at + 4),
                                                a.getFloat(at + 8)));
                        double error =
                                p.distance(b.getFloat(bt), b.getFloat(bt + 4), b.getFloat(bt + 8));
                        maxPosition = Math.max(maxPosition, error);
                        if (error > 3e-5) throw new AssertionError("Particle position " + error);
                        for (int channel = 0; channel < 4; channel++)
                            if (Math.abs(
                                            colour[channel]
                                                    - b.getFloat(bt + colourOffset + channel * 4))
                                    > 1e-6) throw new AssertionError("Particle instance colour");
                        for (int j = 12; j < 128; j++) {
                            if (j >= colourOffset && j < colourOffset + 16) continue;
                            if (a.get(at + j) != b.get(bt + j))
                                throw new AssertionError("Particle material byte " + j);
                        }
                    }
                }
            }
            sources.merge(source, 1L, Long::sum);
            checked++;
            vertices += 4;
        } catch (Throwable failure) {
            throw new IllegalStateException("Actual particle source comparison", failure);
        }
    }

    static Map<String, Object> results() {
        return Map.of(
                "checks",
                checked,
                "vertices",
                vertices,
                "maxPositionError",
                maxPosition,
                "sources",
                Map.copyOf(sources));
    }
}
