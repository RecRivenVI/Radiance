package com.radiance.client.vertex;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CapturedLineVertexConsumerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
    }

    private static void emit(VertexConsumer out, Matrix4f pose, float x, float y, float z) {
        out.addVertex(pose, x, y, z).setColor(10, 20, 30, 102).setNormal(0, 127 / 127.0F, 0);
    }

    @Test
    void actualStorageCaptureSnapshotsFullLocalPoseAndExportsNativeFixture() throws Exception {
        try (var owner = new ProviderLease()) {
            var storage = owner.storage;
            var out = storage.getBuffer(RenderType.lines());
            assertInstanceOf(CapturedLineVertexConsumer.class, out);
            PoseStack pose = new PoseStack();
            // Owner rotation followed by animated part rotation, including a mirror.
            pose.mulPose(new Matrix4f().rotateZ(.35F).rotateX(.7F).scale(-1, 1, 1));
            Matrix4f expected = new Matrix4f(pose.last().pose());
            out.addVertex(pose.last(), .2F, .3F, .4F)
                    .setColor(10, 20, 30, 102)
                    .setNormal(pose.last(), 0, 1, 0);
            out.addVertex(pose.last(), .4F, 1.2F, .7F)
                    .setColor(10, 20, 30, 102)
                    .setNormal(pose.last(), 0, 1, 0);
            pose.setIdentity();
            try (var mesh = ((CapturedLineVertexConsumer) out).build()) {
                assertEquals(4, mesh.drawState().vertexCount());
                assertEquals(14, Constants.VertexFormats.getValue(mesh.drawState().format()));
                assertEquals(72, mesh.drawState().format().getVertexSize());
                ByteBuffer data = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                Vector3f p = expected.transformPosition(.2F, .3F, .4F, new Vector3f());
                assertEquals(p.x, data.getFloat(0));
                assertEquals(p.y, data.getFloat(4));
                assertEquals(p.z, data.getFloat(8));
                assertEquals(102, Byte.toUnsignedInt(data.get(15)));
                float[] frame = {
                    expected.m00(),
                    expected.m01(),
                    expected.m02(),
                    expected.m10(),
                    expected.m11(),
                    expected.m12(),
                    expected.m20(),
                    expected.m21(),
                    expected.m22()
                };
                for (int vertex = 0; vertex < 4; ++vertex) {
                    for (int i = 0; i < 9; ++i)
                        assertEquals(frame[i], data.getFloat(vertex * 72 + 20 + i * 4));
                }
                byte[] vertices = new byte[data.remaining()];
                data.get(vertices);
                ByteBuffer fixture =
                        ByteBuffer.allocate(16 + vertices.length).order(ByteOrder.LITTLE_ENDIAN);
                fixture.putInt(0x31454E4C).putInt(14).putInt(72).putInt(4).put(vertices);
                Path file = Path.of("build/test-fixtures/captured-lines-java.bin");
                Files.createDirectories(file.getParent());
                Files.write(file, fixture.array());
            }
        }
    }

    @Test
    void multipleTransformsAndScalarFallbackDoNotLeakThePreviousPose() {
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1)) {
            var out =
                    new CapturedLineVertexConsumer(
                            bytes,
                            VertexFormat.Mode.DEBUG_LINE_STRIP,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL);
            emit(out, new Matrix4f().rotateY(.2F), 0, 0, 0);
            emit(out, new Matrix4f().rotateX(.7F), 1, 1, 1);
            out.addVertex(2, 2, 2).setColor(1, 2, 3, 4).setNormal(0, 1, 0);
            try (var mesh = out.build()) {
                var data = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                assertEquals(3, mesh.drawState().vertexCount());
                assertNotEquals(data.getFloat(28), data.getFloat(72 + 28));
                for (int i = 0; i < 9; ++i) assertEquals(0, data.getFloat(144 + 20 + i * 4));
            }
            assertThrows(IllegalStateException.class, () -> out.addVertex(0, 0, 0));
        }
    }

    @Test
    void rawPositionWithAnExplicitNormalPoseStillRetainsItsDrawingFrame() {
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1)) {
            var out =
                    new CapturedLineVertexConsumer(
                            bytes,
                            VertexFormat.Mode.DEBUG_LINES,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL);
            PoseStack pose = new PoseStack();
            pose.mulPose(new Matrix4f().rotateY(.6F));
            out.addVertex(0, 0, 0).setColor(1, 2, 3, 4).setNormal(pose.last(), 0, 1, 0);
            out.addVertex(0, 1, 0).setColor(1, 2, 3, 4).setNormal(pose.last(), 0, 1, 0);
            try (var mesh = out.build()) {
                assertEquals(
                        pose.last().pose().m02(),
                        mesh.vertexBuffer().order(ByteOrder.nativeOrder()).getFloat(28));
            }
        }
    }

    @Test
    void allFourSourceModesRetainTheirOwnDuplicationAndOwnership() {
        for (var mode :
                List.of(
                        VertexFormat.Mode.LINES,
                        VertexFormat.Mode.LINE_STRIP,
                        VertexFormat.Mode.DEBUG_LINES,
                        VertexFormat.Mode.DEBUG_LINE_STRIP)) {
            try (ByteBufferBuilder bytes = new ByteBufferBuilder(1)) {
                var out =
                        new CapturedLineVertexConsumer(
                                bytes, mode, DefaultVertexFormat.POSITION_COLOR);
                out.addVertex(0, 0, 0).setColor(1, 2, 3, 4);
                out.addVertex(1, 0, 0).setColor(5, 6, 7, 8);
                try (var mesh = out.build()) {
                    assertEquals(
                            mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP
                                    ? 4
                                    : 2,
                            mesh.drawState().vertexCount());
                }
                assertThrows(IllegalStateException.class, out::build);
            }
        }
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1)) {
            var incomplete =
                    new CapturedLineVertexConsumer(
                            bytes,
                            VertexFormat.Mode.LINES,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL);
            incomplete.addVertex(0, 0, 0).setColor(1, 2, 3, 4);
            assertThrows(IllegalStateException.class, incomplete::build);
        }
        assertFalse(
                CapturedLineVertexConsumer.supports(
                        VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR));
    }

    @Test
    void tangentAndExplicitReferenceRemainDistinctIncludingMinecraftDuplicates() {
        try (var allocation = new ByteBufferBuilder(1)) {
            var out =
                    new CapturedLineVertexConsumer(
                            allocation,
                            VertexFormat.Mode.LINES,
                            DefaultVertexFormat.POSITION_COLOR_NORMAL);
            PoseStack pose = new PoseStack();
            pose.mulPose(new Matrix4f().rotateY(.5f).scale(-1, 2, .5f));
            out.addVertex(pose.last(), 0, 0, 0).setColor(1, 2, 3, 102).setNormal(0, 1, 0);
            out.sectionReference(pose.last(), 1, 0, 0);
            out.addVertex(pose.last(), 0, 1, 0).setColor(4, 5, 6, 153).setNormal(0, 1, 0);
            out.sectionReference(pose.last(), 1, 0, 0);
            var reference = pose.last().pose().transformDirection(1, 0, 0, new Vector3f());
            pose.setIdentity();
            try (var mesh = out.build()) {
                var data = mesh.vertexBuffer().order(ByteOrder.LITTLE_ENDIAN);
                for (int vertex = 0; vertex < 4; vertex++) {
                    int at = vertex * CapturedLineVertexConsumer.STRIDE;
                    assertEquals(127, Byte.toUnsignedInt(data.get(at + 17)));
                    assertEquals(reference.x, data.getFloat(at + 56));
                    assertEquals(reference.z, data.getFloat(at + 64));
                    assertEquals(1, data.getInt(at + 68));
                    assertEquals(vertex < 2 ? 102 : 153, Byte.toUnsignedInt(data.get(at + 15)));
                }
            }
        }
    }

    private static final class ProviderLease implements AutoCloseable {
        final StorageVertexConsumerProvider storage = new StorageVertexConsumerProvider(1);

        @Override
        public void close() {
            storage.close();
        }
    }
}
