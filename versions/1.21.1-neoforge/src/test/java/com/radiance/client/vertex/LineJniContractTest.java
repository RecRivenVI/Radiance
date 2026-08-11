package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.render.contract.LineContract;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LineJniContractTest {
    @BeforeAll
    static void loadRealNativeDecoder() {
        Path runtime = Path.of(System.getProperty("radiance.test.nativeRuntime"));
        System.load(runtime.resolve("libxess.dll").toAbsolutePath().toString());
        System.load(runtime.resolve("core.dll").toAbsolutePath().toString());
    }

    private static ByteBuffer style(int source, float width) {
        var buffer =
                ByteBuffer.allocateDirect(LineContract.STYLE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        LineContract.writeStyle(
                buffer,
                0,
                new LineContract.SourceKey(source, "contract_fixture"),
                new LineContract.LineStyle(
                        LineContract.Encoding.MINECRAFT_LINES,
                        width,
                        LineContract.WidthPolicy.DEBUG.thickness(width),
                        new LineContract.Material(10, 1, 0)),
                LineContract.Basis3.IDENTITY);
        return buffer;
    }

    @Test
    void defaultWorldMaterialIsOpaqueWhileSourceAlphaStillReachesNativeWhenSelected() {
        try (var allocator = new ByteBufferBuilder(1)) {
            var consumer =
                    new CapturedLineVertexConsumer(
                            allocator,
                            VertexFormat.Mode.LINES,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL);
            consumer.addVertex(0, 0, 0).setColor(10, 20, 30, 102).setNormal(0, 1, 0);
            consumer.addVertex(0, 1, 0).setColor(20, 30, 40, 153).setNormal(0, 1, 0);
            try (var mesh = consumer.build()) {
                var vertices = mesh.vertexBuffer();
                assertEquals(102, Byte.toUnsignedInt(vertices.get(15)));
                assertEquals(
                        153, Byte.toUnsignedInt(vertices.get(2 * LineContract.VERTEX_BYTES + 15)));
                var opaque = style(1, 2.5f);
                var source = new LineContract.Material(10, 1, 0);
                var material =
                        com.radiance.client.render.LineDrawAdapter.worldMaterial(
                                source.alphaMode(), source.emission(), source.queryVisibility());
                assertEquals(0, material.alphaMode());
                LineContract.writeStyle(
                        opaque,
                        0,
                        new LineContract.SourceKey(1, "opacity_fixture"),
                        new LineContract.LineStyle(
                                LineContract.Encoding.MINECRAFT_LINES, 2.5f, .01875f, material),
                        LineContract.Basis3.IDENTITY);
                var a = EntityProxy.inspectLineContract(opaque, vertices, 0, 4);
                assertEquals(0, a[1]);
                var preserved =
                        LineContract.OpacityPolicy.SOURCE_ALPHA.material(
                                source.alphaMode(), source.emission(), source.queryVisibility());
                var transparent = style(1, 2.5f);
                assertEquals(10, preserved.alphaMode());
                LineContract.writeStyle(
                        transparent,
                        0,
                        new LineContract.SourceKey(1, "opacity_fixture"),
                        new LineContract.LineStyle(
                                LineContract.Encoding.MINECRAFT_LINES, 2.5f, .01875f, preserved),
                        LineContract.Basis3.IDENTITY);
                var b = EntityProxy.inspectLineContract(transparent, vertices, 0, 4);
                assertEquals(10, b[1]);
                for (int i = 2; i < a.length; i++) assertEquals(a[i], b[i], 1e-8);
            }
        }
    }

    @Test
    void drawColourAndRigidWireDataReachTheNativeDecoderWithoutLosingOwnership() {
        var state =
                new com.radiance.render.contract.DrawStateSnapshot(4, 3, .25f, .5f, .75f, .125f);
        var bytes = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        state.writeColour(bytes, 8);
        assertArrayEquals(
                new float[] {.25f, .5f, .75f, .125f}, EntityProxy.inspectDrawColour(bytes, 8));
        assertThrows(RuntimeException.class, () -> EntityProxy.inspectDrawColour(bytes, 17));
        bytes.putFloat(8, Float.NaN);
        assertThrows(RuntimeException.class, () -> EntityProxy.inspectDrawColour(bytes, 8));
        var owner = new com.radiance.client.texture.TextureTasks.Owner(9, 4);
        try (var batch = new RigidModelCapture.Batch(2)) {
            batch.add(
                    1L << 32,
                    Long.MIN_VALUE | 1,
                    1,
                    owner,
                    0xABCD,
                    4,
                    0,
                    0,
                    0,
                    1,
                    new Matrix4f(),
                    "solid",
                    state);
            batch.add(
                    2L << 32,
                    Long.MIN_VALUE | 2,
                    1,
                    owner,
                    0xDCBA,
                    4,
                    0,
                    0,
                    0,
                    1,
                    new Matrix4f(),
                    "solid");
            assertArrayEquals(
                    new float[] {.25f, .5f, .75f, .125f},
                    EntityProxy.inspectDrawColour(batch.records(), 136));
            assertArrayEquals(
                    new float[] {1, 1, 1, 1},
                    EntityProxy.inspectDrawColour(batch.records(), 152 + 136));
        }
    }

    @Test
    void generatedJavaRegistryMatchesNativeValuesAndActualDecodedStructSizes() {
        var values = EntityProxy.inspectRenderAbi();
        var expected = com.radiance.render.contract.RenderAbi.values();
        assertEquals(expected.length + 4, values.length);
        assertArrayEquals(expected, java.util.Arrays.copyOf(values, expected.length));
        assertArrayEquals(
                new int[] {128, 16, 80, 72},
                java.util.Arrays.copyOfRange(values, expected.length, values.length));
    }

    @Test
    void capturedBytesReachProductionNativeDecoderAndExtrusionAcrossSourcesAndWidths() {
        try (var allocator = new ByteBufferBuilder(1)) {
            var consumer =
                    new CapturedLineVertexConsumer(
                            allocator,
                            VertexFormat.Mode.LINES,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL);
            var pose = new Matrix4f().rotateY(.7f).rotateZ(.2f).scale(-1, 2, .5f);
            consumer.addVertex(pose, 0, 0, 0).setColor(10, 20, 30, 102).setNormal(0, 1, 0);
            consumer.addVertex(pose, 0, 1, 0).setColor(20, 30, 40, 153).setNormal(0, 1, 0);
            try (var mesh = consumer.build()) {
                var vertices = mesh.vertexBuffer();
                double[] reference = null;
                for (int source = 1; source <= 11; source++) {
                    var decoded =
                            EntityProxy.inspectLineContract(style(source, 2.5f), vertices, 0, 4);
                    assertEquals(30, decoded.length);
                    assertEquals(source, decoded[0]);
                    assertEquals(10, decoded[1]);
                    assertEquals(.01875, decoded[2], 1e-8);
                    assertEquals(1, decoded[4]);
                    for (double value : decoded) assertTrue(Double.isFinite(value));
                    if (reference != null)
                        for (int i = 6; i < decoded.length; i++)
                            assertEquals(
                                    reference[i],
                                    decoded[i],
                                    1e-8,
                                    "entry-point geometry equivalence");
                    reference = decoded;
                }
                var wider = EntityProxy.inspectLineContract(style(1, 5), vertices, 0, 4);
                assertEquals(.0375, wider[2], 1e-8);
                assertNotEquals(reference[6], wider[6]);
                var malformed = style(1, 2.5f);
                malformed.putInt(4, 99);
                assertThrows(
                        IllegalStateException.class,
                        () -> EntityProxy.inspectLineContract(malformed, vertices, 0, 4));
                assertThrows(
                        IllegalStateException.class,
                        () -> EntityProxy.inspectLineContract(style(1, 2.5f), vertices, 0, 5));
            }
        }
    }
}
