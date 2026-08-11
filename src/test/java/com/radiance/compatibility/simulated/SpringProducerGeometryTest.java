package com.radiance.compatibility.simulated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import com.radiance.client.vertex.PBRVertexFormats;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.PBRVertexFormatElements;
import com.radiance.compatibility.veil.SpringRasterLowering;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

/** Invokes the pinned Simulated 1.3.2 renderSegment bytecode, not a transcription. */
class SpringProducerGeometryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
    }
    private record Vertex(float x, float y, float z, float u, float v,
                          byte nx, byte ny, byte nz, int stress) {
        String corner() {
            return Math.round(x * 100000) + ":" + Math.round(y * 100000) + ":"
                + Math.round(z * 100000);
        }
    }

    @Test
    void sourcePreservesBothWoundSurfacesTheirUvsAndTwistedDiagonal() throws Exception {
        List<Vertex> straight = producer(false, false);
        List<Vertex> twisted = producer(true, false);
        assertEquals(32, straight.size());
        assertEquals(32, twisted.size());
        assertEquals(0, straight.size() % 4);
        for (List<Vertex> emitted : List.of(straight, twisted)) {
            for (int primary = 0; primary < 16; primary += 4) {
                List<Vertex> quad = emitted.subList(primary, primary + 4);
                List<Vertex> reversed = matchingSecondary(emitted, quad);
                assertEquals(quad.get(3).corner(), reversed.get(0).corner());
                assertEquals(quad.get(2).corner(), reversed.get(1).corner());
                assertEquals(quad.get(1).corner(), reversed.get(2).corner());
                assertEquals(quad.get(0).corner(), reversed.get(3).corner());
                for (int corner = 0; corner < 4; corner++) {
                    Vertex front = quad.get(corner), back = reversed.get(3 - corner);
                    assertEquals(front.nx, back.nx);
                    assertEquals(front.ny, back.ny);
                    assertEquals(front.nz, back.nz);
                    // BLOCK's packed COLOR bytes are RGBA (little-endian int ABGR).
                    assertEquals(0x7f504030, front.stress);
                    assertEquals(front.stress, back.stress);
                }
                assertFalse(quad.get(0).u == reversed.get(3).u
                    && quad.get(0).v == reversed.get(3).v);
            }
        }
        assertTrue(planeDeviation(straight.subList(0, 4)) < 1e-6);
        assertTrue(planeDeviation(twisted.subList(0, 4)) > 0.01);
    }

    @Test
    void sourceMirrorChangesPositionWindingWithoutReauthoringStressOrUv() throws Exception {
        List<Vertex> ordinary = producer(true, false);
        List<Vertex> mirrored = producer(true, true);
        for (int i = 0; i < ordinary.size(); i++) {
            assertEquals(-ordinary.get(i).x, mirrored.get(i).x, 1e-5);
            assertEquals(ordinary.get(i).y, mirrored.get(i).y, 1e-5);
            assertEquals(ordinary.get(i).z, mirrored.get(i).z, 1e-5);
            assertEquals(ordinary.get(i).u, mirrored.get(i).u);
            assertEquals(ordinary.get(i).v, mirrored.get(i).v);
            assertEquals(ordinary.get(i).stress, mirrored.get(i).stress);
        }
        assertTrue(triangleZ(ordinary.subList(0, 3)) * triangleZ(mirrored.subList(0, 3)) <= 0);
    }

    @Test
    void sourceSegmentCarriesExactQuantizedWarningColorForChangingInputs() throws Exception {
        int[] sourceArgb = {0x00EB3230, 0x26EB3230, 0x4CEB3230};
        for (int color : sourceArgb) {
            for (Vertex vertex : producer(false, false, color)) {
                // The original spring's RGB is #EB3230. Its alpha is the
                // truncated 8-bit warning strength, not surface opacity.
                int packedAbgr = (color & 0xFF00FF00)
                    | ((color >>> 16) & 0xFF) | ((color & 0xFF) << 16);
                assertEquals(packedAbgr, vertex.stress());
            }
        }
    }

    @Test
    void originalRasterBufferSourceBuildsThirtyTwoByteSpringVertices() throws Exception {
        Class<?> types = Class.forName("dev.simulated_team.simulated.index.SimRenderTypes");
        RenderType spring = (RenderType) types.getMethod("spring", ResourceLocation.class).invoke(null,
            ResourceLocation.fromNamespaceAndPath("simulated", "textures/block/spring/spring.png"));
        assertEquals(32, spring.format().getVertexSize());
        assertEquals(VertexFormat.Mode.QUADS, spring.mode());
        assertEquals(spring, SpringDrawContract.from(spring).renderType());
        assertFalse(SpringDrawContract.hasProducerClass(new ClassLoader(null) {}));
        assertNull(SpringDrawContract.fromFactory(spring, ignored -> RenderType.solid()));
        RenderType unrelatedNamedSpring = new RenderType("spring", DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS, 128, false, false, () -> {}, () -> {}) {};
        assertNull(SpringDrawContract.from(unrelatedNamedSpring));
        ResourceLocation alternate = ResourceLocation.fromNamespaceAndPath("example",
            "textures/block/spring_body.png");
        RenderType alternateLayer = (RenderType) types.getMethod("spring", ResourceLocation.class)
            .invoke(null, alternate);
        assertEquals(alternate, SpringDrawContract.from(alternateLayer).bodyTexture());
        assertNull(SpringDrawContract.from(RenderType.solid())); // torsion's baked-model layer
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(2048)) {
            MultiBufferSource.BufferSource provider = MultiBufferSource.immediate(allocator);
            VertexConsumer consumer = provider.getBuffer(spring);
            assertFalse(consumer instanceof com.radiance.client.vertex.PBRVertexConsumer);
            var builders = MultiBufferSource.BufferSource.class.getDeclaredField("startedBuilders");
            builders.setAccessible(true);
            @SuppressWarnings("unchecked")
            var started = (java.util.Map<RenderType, BufferBuilder>) builders.get(provider);
            assertEquals(consumer, started.get(spring));
            Class<?> source = Class.forName("dev.simulated_team.simulated.content.blocks.spring.SpringRenderer");
            Object renderer = source.getConstructor(
                net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context.class)
                .newInstance((Object) null);
            Method segment = source.getDeclaredMethod("renderSegment", PoseStack.class,
                Vector3dc.class, Vector3dc.class, Vector3dc.class, Vector3dc.class,
                Vector3dc.class, Vector3dc.class, boolean.class, float.class, float.class,
                int.class, int.class, VertexConsumer.class, float.class, float.class);
            segment.setAccessible(true);
            segment.invoke(renderer, new PoseStack(), new Vector3d(0, 1, 0),
                new Vector3d(0, 1, 0), new Vector3d(0, 0, -1),
                new Vector3d(0, 0, -1), new Vector3d(0, 0, 0), new Vector3d(0, 1, 0),
                false, 0.25F, 0.75F, 0x00F000F0, 0x7f304050, consumer, 8.0F, 16.0F);
            try (MeshData mesh = started.get(spring).buildOrThrow()) {
                assertEquals(16, mesh.drawState().vertexCount());
                assertEquals(32, mesh.drawState().format().getVertexSize());
                assertEquals(16 * 32, mesh.vertexBuffer().remaining());
                assertTrue(SpringDrawContract.sameBlockBytes(mesh.drawState().format()));
                SpringRasterLowering.requireDraw(SpringDrawContract.PROGRAM,
                    mesh.drawState().format(), mesh.drawState().mode(),
                    mesh.drawState().vertexCount(), mesh.drawState().indexCount());
                assertThrows(IllegalStateException.class, () -> SpringRasterLowering.requireDraw(
                    SpringDrawContract.PROGRAM, PBRVertexFormats.PBR_TRIANGLE,
                    mesh.drawState().mode(), mesh.drawState().vertexCount(),
                    mesh.drawState().indexCount()));
            }
        }
    }

    @Test
    void originalTwistedMirroredProducerFeedsWorldMaterialWithoutChangingGeometry() throws Exception {
        List<Vertex> raster = producer(true, true);
        RenderType worldLayer = new RenderType("test_world_spring",
            PBRVertexFormats.PBR_TRIANGLE, VertexFormat.Mode.QUADS, 4096,
            false, false, () -> {}, () -> {}) {};
        Object testOwner = new Object(), testModel = new Object();
        SpringShaderVerification.verified(testModel);
        SpringShaderVerification.attached(testOwner, testModel);
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(8192)) {
            PBRVertexConsumer world = SpringWorldLowering.createConsumer(allocator,
                new SpringDrawContract.Draw(worldLayer, ResourceLocation.fromNamespaceAndPath(
                    "simulated", "textures/block/spring/spring.png")));
            Class<?> source = Class.forName("dev.simulated_team.simulated.content.blocks.spring.SpringRenderer");
            Object renderer = source.getConstructor(
                net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context.class)
                .newInstance((Object) null);
            Method segment = source.getDeclaredMethod("renderSegment", PoseStack.class,
                Vector3dc.class, Vector3dc.class, Vector3dc.class, Vector3dc.class,
                Vector3dc.class, Vector3dc.class, boolean.class, float.class, float.class,
                int.class, int.class, VertexConsumer.class, float.class, float.class);
            segment.setAccessible(true);
            PoseStack pose = new PoseStack();
            pose.scale(-1, 1, 1);
            Vector3d direction = new Vector3d(0, 1, 0), up = new Vector3d(0, 0, -1);
            Vector3d endUp = new Vector3d(up).rotateY(Math.toRadians(7.4));
            Vector3d start = new Vector3d(0, 0, 0), end = new Vector3d(0, 1, 0);
            segment.invoke(renderer, pose, direction, direction, up, endUp, start, end,
                false, 0.25F, 0.75F, 0x00F000F0, 0x7f304050, world, 8.0F, 16.0F);
            segment.invoke(renderer, pose, new Vector3d(direction).negate(),
                new Vector3d(direction).negate(), new Vector3d(up).negate(),
                new Vector3d(endUp).negate(), start, end,
                true, -0.25F, -0.75F, 0x00F000F0, 0x7f304050, world, 8.0F, 16.0F);
            try (MeshData mesh = world.end()) {
                assertEquals(32, mesh.drawState().vertexCount());
                assertEquals(128, mesh.drawState().format().getVertexSize());
                ByteBuffer bytes = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                int[] offsets = PBRVertexFormats.PBR_TRIANGLE.getOffsetsByElement();
                int uv = offsets[PBRVertexFormatElements.PBR_TEXTURE_UV.id()];
                int color = offsets[PBRVertexFormatElements.PBR_COLOR_LAYER.id()];
                int norm = offsets[PBRVertexFormatElements.PBR_NORM.id()];
                int normalMode = offsets[PBRVertexFormatElements.PBR_USE_NORM.id()];
                int alphaMode = offsets[PBRVertexFormatElements.PBR_POST_BASE.id()] + 12;
                for (int i = 0; i < raster.size(); i++) {
                    int base = i * 128;
                    Vertex original = raster.get(i);
                    assertEquals(original.x, bytes.getFloat(base), 1e-6);
                    assertEquals(original.y, bytes.getFloat(base + 4), 1e-6);
                    assertEquals(original.z, bytes.getFloat(base + 8), 1e-6);
                    assertEquals(original.u, bytes.getFloat(base + uv), 1e-6);
                    assertEquals(original.v, bytes.getFloat(base + uv + 4), 1e-6);
                    assertEquals(original.nx / 127.0F, bytes.getFloat(base + norm), 0.01F);
                    assertEquals(original.ny / 127.0F, bytes.getFloat(base + norm + 4), 0.01F);
                    assertEquals(original.nz / 127.0F, bytes.getFloat(base + norm + 8), 0.01F);
                    assertEquals(2, bytes.getInt(base + normalMode));
                    assertEquals(PBRVertexConsumer.ALPHA_MODE_CUTOUT_LOW,
                        bytes.getInt(base + alphaMode));
                    assertEquals(0x7f / 255.0F, bytes.getFloat(base + color + 12), 1e-6);
                }
            }
        } finally {
            SpringShaderVerification.released(testOwner, testModel);
        }
    }

    private static List<Vertex> producer(boolean twisted, boolean mirror) throws Exception {
        return producer(twisted, mirror, 0x7f304050);
    }

    private static List<Vertex> producer(boolean twisted, boolean mirror, int stressColor) throws Exception {
        Class<?> source = Class.forName("dev.simulated_team.simulated.content.blocks.spring.SpringRenderer");
        Object renderer = source.getConstructor(
            net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context.class)
            .newInstance((Object) null);
        Method segment = source.getDeclaredMethod("renderSegment", PoseStack.class,
            Vector3dc.class, Vector3dc.class, Vector3dc.class, Vector3dc.class,
            Vector3dc.class, Vector3dc.class, boolean.class, float.class, float.class,
            int.class, int.class, VertexConsumer.class, float.class, float.class);
        segment.setAccessible(true);
        PoseStack pose = new PoseStack();
        if (mirror) pose.scale(-1, 1, 1);
        Vector3d direction = new Vector3d(0, 1, 0);
        Vector3d up = new Vector3d(0, 0, -1);
        Vector3d endUp = new Vector3d(up);
        if (twisted) endUp.rotateY(Math.toRadians(7.4));
        Vector3d start = new Vector3d(0, 0, 0), end = new Vector3d(0, 1, 0);
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(2048)) {
            BufferBuilder out = new BufferBuilder(allocator, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK);
            segment.invoke(renderer, pose, direction, direction, up, endUp, start, end,
                false, 0.25F, 0.75F, 0x00F000F0, stressColor, out, 8.0F, 16.0F);
            segment.invoke(renderer, pose, new Vector3d(direction).negate(),
                new Vector3d(direction).negate(), new Vector3d(up).negate(),
                new Vector3d(endUp).negate(), start, end,
                true, -0.25F, -0.75F, 0x00F000F0, stressColor, out, 8.0F, 16.0F);
            try (MeshData mesh = out.buildOrThrow()) {
                ByteBuffer data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                int[] offsets = DefaultVertexFormat.BLOCK.getOffsetsByElement();
                int color = offsets[VertexFormatElement.COLOR.id()];
                int uv = offsets[VertexFormatElement.UV0.id()];
                int normal = offsets[VertexFormatElement.NORMAL.id()];
                List<Vertex> result = new ArrayList<>();
                for (int i = 0; i < mesh.drawState().vertexCount(); i++) {
                    int base = i * DefaultVertexFormat.BLOCK.getVertexSize();
                    result.add(new Vertex(data.getFloat(base), data.getFloat(base + 4),
                        data.getFloat(base + 8), data.getFloat(base + uv),
                        data.getFloat(base + uv + 4), data.get(base + normal),
                        data.get(base + normal + 1), data.get(base + normal + 2),
                        data.getInt(base + color)));
                }
                return result;
            }
        }
    }

    private static List<Vertex> matchingSecondary(List<Vertex> all, List<Vertex> primary) {
        var corners = primary.stream().map(Vertex::corner).sorted().toList();
        for (int offset = 16; offset < 32; offset += 4) {
            List<Vertex> secondary = all.subList(offset, offset + 4);
            if (corners.equals(secondary.stream().map(Vertex::corner).sorted().toList()))
                return secondary;
        }
        throw new AssertionError("Original reverse-wound spring face missing");
    }

    private static double planeDeviation(List<Vertex> quad) {
        Vertex a = quad.get(0), b = quad.get(1), c = quad.get(2), d = quad.get(3);
        double ax = b.x-a.x, ay = b.y-a.y, az = b.z-a.z;
        double bx = c.x-a.x, by = c.y-a.y, bz = c.z-a.z;
        double nx = ay*bz-az*by, ny = az*bx-ax*bz, nz = ax*by-ay*bx;
        return Math.abs(nx*(d.x-a.x)+ny*(d.y-a.y)+nz*(d.z-a.z))
            / Math.sqrt(nx*nx+ny*ny+nz*nz);
    }

    private static double triangleZ(List<Vertex> triangle) {
        Vertex a = triangle.get(0), b = triangle.get(1), c = triangle.get(2);
        return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
    }
}
