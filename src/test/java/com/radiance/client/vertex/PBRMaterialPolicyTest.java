package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_COLOR_LAYER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.compatibility.simulated.SpringDrawContract;
import com.radiance.compatibility.simulated.SpringShaderVerification;
import com.radiance.compatibility.simulated.SpringWorldLowering;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PBRMaterialPolicyTest {
    private static RenderType layer;
    private static final PBRVertexConsumer.MaterialPolicy SPRING =
        new PBRVertexConsumer.MaterialPolicy(PBRVertexConsumer.ALPHA_MODE_CUTOUT_LOW,
            PBRVertexConsumer.ColorSemantics.SURFACE_MIX, false, false, false,
            PBRVertexConsumer.NormalSemantics.VERTEX_BRDF);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
        layer = new RenderType("test_spring_contract", PBRVertexFormats.PBR_TRIANGLE,
            VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}) {};
    }

    @Test
    void allVertexConsumerColorEntriesUseSurfaceMixWithoutChangingCutoutOrEmission() {
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(1024);
             PBRMaterialContext.Scope transmission = PBRMaterialContext.pushEntityTransmission(true);
             PBRMaterialContext.Scope emission = PBRMaterialContext.pushAlbedoEmission(1.0F)) {
            PBRVertexConsumer captured = PBRVertexConsumer.dynamicMaterial(allocator, layer, false, SPRING);
            VertexConsumer vertices = captured;
            vertices.addVertex(0, 0, 0).setColor(10, 20, 30, 40).setNormal(0, 0, 1)
                .setUv1(4, 5); // SPRING_FORMAT has no UV1; the material ignores it.
            vertices.addVertex(1, 0, 0).setColor(0x50607080).setNormal(0, 0, 1);
            vertices.addVertex(1, 1, 0).setColor(0.2F, 0.4F, 0.6F, 0.8F).setNormal(0, 0, 1);
            vertices.addVertex(0, 1, 0, 0x900A141E, 0.25F, 0.75F, 0, 0, 0, 0, 1);
            assertFalse(captured.allowsRigidCapture());
            assertFalse(captured.rigidEligible());
            assertThrows(IllegalStateException.class, () -> captured.setDefaultAlbedoEmission(1));
            assertThrows(IllegalStateException.class, () -> captured.albedoEmission(1));
            try (MeshData mesh = captured.end()) {
                ByteBuffer data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                assertEquals(4 * 128, data.remaining());
                int colorMode = offset(PBR_USE_COLOR_LAYER);
                int color = offset(PBR_COLOR_LAYER);
                int alphaMode = offset(PBR_POST_BASE) + 12;
                int vertexEmission = offset(PBR_ALBEDO_EMISSION);
                int[] stress = {40, 0x50, 204, 0x90};
                for (int vertex = 0; vertex < 4; vertex++) {
                    int base = vertex * 128;
                    assertEquals(2, data.getInt(base + colorMode), "surface mix vertex " + vertex);
                    assertEquals(PBRVertexConsumer.ALPHA_MODE_CUTOUT_LOW,
                        data.getInt(base + alphaMode), "cutout vertex " + vertex);
                    assertEquals(stress[vertex] / 255.0F, data.getFloat(base + color + 12), 1.0e-6F);
                    assertEquals(0.0F, data.getFloat(base + vertexEmission));
                    assertEquals(2, data.getInt(base + offset(PBR_USE_NORM)));
                    assertEquals(0, data.getInt(base + offset(PBR_USE_OVERLAY)));
                }
                assertEquals(10 / 255.0F, data.getFloat(color), 1.0e-6F);
                assertEquals(20 / 255.0F, data.getFloat(color + 4), 1.0e-6F);
                assertEquals(30 / 255.0F, data.getFloat(color + 8), 1.0e-6F);
            }
        }
    }

    @Test
    void providerInheritedEmissionCannotCrashOrFreezeSpringFactoryDraws() {
        Object testOwner = new Object(), testModel = new Object();
        SpringShaderVerification.verified(testModel);
        SpringShaderVerification.attached(testOwner, testModel);
        try {
            float first = providerSpringPosition(1.0F);
            float next = providerSpringPosition(2.5F);
            assertEquals(1.0F, first);
            assertEquals(2.5F, next);
        } finally {
            SpringShaderVerification.released(testOwner, testModel);
        }
    }

    @Test
    void ordinaryProviderStillAppliesItsInheritedEmissionFloor() {
        StorageVertexConsumerProvider provider = new StorageVertexConsumerProvider(1024, 0.75F);
        try {
            PBRVertexConsumer ordinary = (PBRVertexConsumer) provider.getBuffer(layer);
            ordinary.addVertex(0, 0, 0).setColor(255, 255, 255, 255);
            ordinary.addVertex(1, 0, 0).setColor(255, 255, 255, 255);
            ordinary.addVertex(1, 1, 0).setColor(255, 255, 255, 255);
            ordinary.addVertex(0, 1, 0).setColor(255, 255, 255, 255);
            try (MeshData mesh = ordinary.end()) {
                ByteBuffer data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                assertEquals(0.75F, data.getFloat(offset(PBR_ALBEDO_EMISSION)));
            }
        } finally {
            provider.close();
        }
    }

    @Test
    void vertexNormalMaterialRejectsGlintWrapperBeforeEncoding() {
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(1024)) {
            PBRVertexConsumer spring = PBRVertexConsumer.dynamicMaterial(allocator, layer,
                false, SPRING);
            RenderType glintLayer = new RenderType("entity_glint", PBRVertexFormats.PBR_TRIANGLE,
                VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}) {};
            VertexConsumer wrapper = new PBRVertexConsumer.GLint(spring, glintLayer);
            wrapper.addVertex(0, 0, 0);
            assertThrows(IllegalStateException.class, () -> wrapper.setUv(0.25F, 0.75F));
        }
    }

    @Test
    void alternateVertexEntryAlsoSuppressesInheritedSpringEmission() {
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(1024);
             PBRMaterialContext.Scope emission = PBRMaterialContext.pushAlbedoEmission(1.0F)) {
            PBRVertexConsumer spring = PBRVertexConsumer.dynamicMaterial(allocator, layer,
                false, SPRING);
            spring.vertex(0, 0, 0, 17).setColor(20, 30, 40, 50);
            spring.addVertex(1, 0, 0).setColor(20, 30, 40, 50);
            spring.addVertex(1, 1, 0).setColor(20, 30, 40, 50);
            spring.addVertex(0, 1, 0).setColor(20, 30, 40, 50);
            try (MeshData mesh = spring.end()) {
                ByteBuffer data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                for (int i = 0; i < 4; i++)
                    assertEquals(0.0F, data.getFloat(i * 128 + offset(PBR_ALBEDO_EMISSION)));
            }
        }
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(1024);
             PBRMaterialContext.Scope emission = PBRMaterialContext.pushAlbedoEmission(0.75F)) {
            PBRVertexConsumer ordinary = PBRVertexConsumer.dynamic(allocator, layer, false);
            ordinary.vertex(0, 0, 0, 17).setColor(20, 30, 40, 50);
            ordinary.addVertex(1, 0, 0).setColor(20, 30, 40, 50);
            ordinary.addVertex(1, 1, 0).setColor(20, 30, 40, 50);
            ordinary.addVertex(0, 1, 0).setColor(20, 30, 40, 50);
            try (MeshData mesh = ordinary.end()) {
                ByteBuffer data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                assertEquals(0.75F, data.getFloat(offset(PBR_ALBEDO_EMISSION)));
            }
        }
    }

    @Test
    void sourceWarningStrengthAndLivingOverlayCoordinatesStayDistinct() {
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(1024)) {
            PBRVertexConsumer spring = PBRVertexConsumer.dynamicMaterial(allocator, layer,
                false, SPRING);
            int[] strengths = {0, 38, 76, 76};
            for (int i = 0; i < strengths.length; i++)
                spring.addVertex(i, 0, 0).setColor(235, 50, 48, strengths[i]);
            try (MeshData mesh = spring.end()) {
                ByteBuffer bytes = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                for (int i = 0; i < strengths.length; i++) {
                    int base = i * 128 + offset(PBR_COLOR_LAYER);
                    assertEquals(235 / 255.0F, bytes.getFloat(base), 1e-6F);
                    assertEquals(50 / 255.0F, bytes.getFloat(base + 4), 1e-6F);
                    assertEquals(48 / 255.0F, bytes.getFloat(base + 8), 1e-6F);
                    assertEquals(strengths[i] / 255.0F, bytes.getFloat(base + 12), 1e-6F);
                    assertEquals(2, bytes.getInt(i * 128 + offset(PBR_USE_COLOR_LAYER)));
                    assertEquals(0, bytes.getInt(i * 128 + offset(PBR_USE_OVERLAY)));
                    assertEquals(0.0F, bytes.getFloat(i * 128 + offset(PBR_ALBEDO_EMISSION)));
                }
            }
        }
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(1024)) {
            PBRVertexConsumer living = PBRVertexConsumer.dynamic(allocator, layer, false);
            int[][] overlayUv = {{0, 3}, {0, 10}, {15, 10}};
            for (int i = 0; i < overlayUv.length; i++)
                living.addVertex(i, 0, 0).setColor(255, 255, 255, 255)
                    .setUv1(overlayUv[i][0], overlayUv[i][1]);
            living.addVertex(3, 0, 0).setColor(255, 255, 255, 255);
            try (MeshData mesh = living.end()) {
                ByteBuffer bytes = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                for (int i = 0; i < overlayUv.length; i++) {
                    assertEquals(1, bytes.getInt(i * 128 + offset(PBR_USE_COLOR_LAYER)));
                    assertEquals(1, bytes.getInt(i * 128 + offset(PBR_USE_OVERLAY)));
                    assertEquals(overlayUv[i][0], bytes.getInt(i * 128 + offset(PBR_OVERLAY_UV)));
                    assertEquals(overlayUv[i][1], bytes.getInt(i * 128 + offset(PBR_OVERLAY_UV) + 4));
                }
                assertEquals(0, bytes.getInt(3 * 128 + offset(PBR_USE_OVERLAY)));
            }
        }
    }

    private static float providerSpringPosition(float x) {
        StorageVertexConsumerProvider provider = new StorageVertexConsumerProvider(1024, 1.0F) {
            @Override
            protected VertexConsumer createQuadConsumer(ByteBufferBuilder allocator,
                RenderType renderLayer) {
                return SpringWorldLowering.createConsumer(allocator,
                    new SpringDrawContract.Draw(renderLayer,
                        ResourceLocation.fromNamespaceAndPath("simulated",
                            "textures/block/spring/spring.png")));
            }
        };
        try {
            PBRVertexConsumer spring = (PBRVertexConsumer) provider.getBuffer(layer);
            assertFalse(spring.allowsRigidCapture());
            spring.addVertex(x, 0, 0).setColor(10, 20, 30, 40).setNormal(0, 0, 1);
            spring.addVertex(x + 1, 0, 0).setColor(10, 20, 30, 40).setNormal(0, 0, 1);
            spring.addVertex(x + 1, 1, 0).setColor(10, 20, 30, 40).setNormal(0, 0, 1);
            spring.addVertex(x, 1, 0).setColor(10, 20, 30, 40).setNormal(0, 0, 1);
            try (MeshData mesh = spring.end()) {
                ByteBuffer data = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
                assertEquals(0.0F, data.getFloat(offset(PBR_ALBEDO_EMISSION)));
                assertEquals(PBRVertexConsumer.ALPHA_MODE_CUTOUT_LOW,
                    data.getInt(offset(PBR_POST_BASE) + 12));
                assertEquals(2, data.getInt(offset(PBR_USE_NORM)));
                return data.getFloat(0);
            }
        } finally {
            provider.close();
        }
    }

    private static int offset(com.mojang.blaze3d.vertex.VertexFormatElement element) {
        return PBRVertexFormats.PBR_TRIANGLE.getOffsetsByElement()[element.id()];
    }
}
