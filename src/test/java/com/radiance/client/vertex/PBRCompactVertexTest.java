package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COORDINATE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_PACKED_MODES;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_GLINT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_LIGHT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_TEXTURE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PBRCompactVertexTest {
    private static final int JAVA_FIXTURE_MAGIC = 0x31524250; // "PBR1" in little-endian bytes.
    private static final int JAVA_FIXTURE_VERSION = 1;
    private static RenderType TEST_LAYER;
    private static RenderType ENTITY_GLINT_LAYER;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
        TEST_LAYER = layer("radiance_pbr_compact_test");
        ENTITY_GLINT_LAYER = layer("entity_glint_direct");
    }

    @Test
    void compactV1UsesTheSpecifiedHundredByteLayout() {
        VertexFormat compact = PBRVertexFormats.PBR_COMPACT_V1;
        assertEquals(128, PBRVertexFormats.PBR_TRIANGLE.getVertexSize());
        assertEquals(100, compact.getVertexSize());
        assertEquals(0, offset(compact, PBR_POS));
        assertEquals(12, offset(compact, PBR_NORM));
        assertEquals(24, offset(compact, PBR_COLOR_LAYER));
        assertEquals(40, offset(compact, PBR_TEXTURE_UV));
        assertEquals(48, offset(compact, PBR_OVERLAY_UV));
        assertEquals(56, offset(compact, PBR_TEXTURE_ID));
        assertEquals(60, offset(compact, PBR_GLINT_UV));
        assertEquals(68, offset(compact, PBR_GLINT_TEXTURE));
        assertEquals(72, offset(compact, PBR_LIGHT_UV));
        assertEquals(80, offset(compact, PBR_ALBEDO_EMISSION));
        assertEquals(84, offset(compact, PBR_POST_BASE));
        assertEquals(96, offset(compact, PBR_PACKED_MODES));
        assertTrue(compact.contains(PBR_PACKED_MODES));
        assertEquals("PackedModes", compact.getElementName(PBR_PACKED_MODES));
        assertFalse(compact.contains(PBR_USE_COLOR_LAYER));
        assertFalse(compact.contains(PBR_USE_TEXTURE));
        assertFalse(compact.contains(PBR_USE_OVERLAY));
        assertFalse(compact.contains(PBR_USE_GLINT));
        assertFalse(compact.contains(PBR_USE_LIGHT));
        assertFalse(compact.contains(PBR_COORDINATE));
    }

    @Test
    void defaultConstructorStillEmitsLegacyFormat() {
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(256)) {
            PBRVertexConsumer consumer = new PBRVertexConsumer(allocator, TEST_LAYER);
            assertEquals(PBRVertexFormats.PBR_TRIANGLE, consumer.getFormat());
        }
    }

    @Test
    void defaultAndMissingFieldsReconstructExactly() {
        byte[] legacy = capture(false, 0, ignored -> {}, consumer ->
            consumer.addVertex(-0.0F, 2.5F, -4.0F));
        byte[] compact = capture(true, 0, ignored -> {}, consumer ->
            consumer.addVertex(-0.0F, 2.5F, -4.0F));

        assertEquals(128, legacy.length);
        assertEquals(100, compact.length);
        assertReconstructsLegacy(legacy, compact);
        assertEquals(Float.floatToRawIntBits(-0.0F), intAt(compact, offset(
            PBRVertexFormats.PBR_COMPACT_V1, PBR_POS)));
    }

    @Test
    void settersKeepFirstWriteOrderAndPreserveRawFieldBits() {
        float emission = Float.intBitsToFloat(0x3F812345);
        Consumer<PBRVertexConsumer> prepare = consumer -> {
            consumer.setBase(-0.0F, 18.25F, -31.5F);
            consumer.setDefaultAlbedoEmission(Float.intBitsToFloat(0x3DCCCCCD));
        };
        Consumer<PBRVertexConsumer> vertex = consumer -> {
            consumer.addVertex(-0.0F, 1.25F, -9.0F);
            consumer.setColor(700, -20, 128, 512);
            consumer.setColorMix(1, 2, 3, 4);
            consumer.setUv(-0.0F, 1.75F);
            consumer.setUv(99.0F, 101.0F);
            consumer.setUv1(Integer.MIN_VALUE, -123456789);
            consumer.setUv1(1, 2);
            consumer.setUv2(Integer.MIN_VALUE, Integer.MAX_VALUE);
            consumer.setUv2(1, 2);
            consumer.setNormal(-0.0F, 0.25F, -2.0F);
            consumer.setNormal(1.0F, 2.0F, 3.0F);
            consumer.albedoEmission(emission);
            consumer.albedoEmission(0.1F);
        };

        byte[] legacy = capture(false, 22, prepare, vertex);
        byte[] compact = capture(true, 22, prepare, vertex);
        assertReconstructsLegacy(legacy, compact);

        assertEquals(Float.floatToRawIntBits(emission),
            intAt(compact, offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_ALBEDO_EMISSION)));
        assertEquals(Float.floatToRawIntBits(-0.0F),
            intAt(compact, offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_POS)));
        assertEquals(Float.floatToRawIntBits(-0.0F), intAt(compact,
            offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_POST_BASE)));
        assertEquals(700 / 255.0F, floatAt(compact,
            offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_COLOR_LAYER)));
        assertEquals(-20 / 255.0F, floatAt(compact,
            offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_COLOR_LAYER) + 4));
        assertEquals(Integer.MIN_VALUE, intAt(compact,
            offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_OVERLAY_UV)));
        assertEquals(Integer.MAX_VALUE, intAt(compact,
            offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_LIGHT_UV) + 4));
    }

    @Test
    void entityTransmissionAndBothGlintWrappersKeepTheirPackedModes() {
        byte[] transmissionLegacy;
        byte[] transmissionCompact;
        try (var ignored = PBRMaterialContext.pushEntityTransmission(true)) {
            transmissionLegacy = capture(false, 22, ignoredConsumer -> {}, consumer ->
                consumer.addVertex(1.0F, 2.0F, 3.0F));
            transmissionCompact = capture(true, 22, ignoredConsumer -> {}, consumer ->
                consumer.addVertex(1.0F, 2.0F, 3.0F));
        }
        assertReconstructsLegacy(transmissionLegacy, transmissionCompact);
        assertEquals(2, (intAt(transmissionCompact, offset(
            PBRVertexFormats.PBR_COMPACT_V1, PBR_PACKED_MODES)) >>> 16) & 0xFF);

        byte[] itemLegacy = capture(false, 1, ignored -> {}, consumer -> {
            var glint = new PBRVertexConsumer.GLint(consumer, ENTITY_GLINT_LAYER);
            glint.addVertex(2.0F, 3.0F, 4.0F);
            glint.setUv(0.25F, 0.75F);
        });
        byte[] itemCompact = capture(true, 1, ignored -> {}, consumer -> {
            var glint = new PBRVertexConsumer.GLint(consumer, ENTITY_GLINT_LAYER);
            glint.addVertex(2.0F, 3.0F, 4.0F);
            glint.setUv(0.25F, 0.75F);
        });
        assertReconstructsLegacy(itemLegacy, itemCompact);
        assertEquals(PBRVertexConsumer.GLINT_MODE_ENTITY, packed(itemCompact) >>> 5 & 3);
        assertEquals(0.25F, floatAt(itemCompact,
            offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_GLINT_UV)));

        Consumer<PBRVertexConsumer> overlayDraw = consumer -> {
            var overlay = new PBRVertexConsumer.GLintOverlay(consumer, ENTITY_GLINT_LAYER,
                new PoseStack().last(), 2.0F);
            overlay.addVertex(2.0F, 3.0F, 4.0F);
            overlay.setUv(0.25F, 0.75F);
            overlay.setNormal(0.0F, 1.0F, 0.0F);
        };
        byte[] overlayLegacy = capture(false, 1, ignored -> {}, overlayDraw);
        byte[] overlayCompact = capture(true, 1, ignored -> {}, overlayDraw);
        assertReconstructsLegacy(overlayLegacy, overlayCompact);
        assertEquals(PBRVertexConsumer.GLINT_MODE_ITEM, packed(overlayCompact) >>> 5 & 3);
    }

    @Test
    void colorMixCoatingUsesModeTwoWhenItIsTheFirstColorSetter() {
        Consumer<PBRVertexConsumer> draw = consumer -> {
            consumer.addVertex(1.0F, 2.0F, 3.0F);
            consumer.setColorMix(640, -12, 260, 300);
            consumer.setColor(1, 2, 3, 4);
        };
        byte[][] pair = pair(0, ignored -> {}, draw);
        assertReconstructsLegacy(pair[0], pair[1]);
        assertEquals(2, packed(pair[1]) >>> 1 & 3);
    }

    @Test
    void compactAlphaDomainIsCheckedWithoutChangingLegacyCustomModes() {
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(256)) {
            assertThrows(IllegalArgumentException.class,
                () -> PBRVertexConsumer.dynamic(allocator, TEST_LAYER, true, 256));
            assertThrows(IllegalArgumentException.class,
                () -> PBRVertexConsumer.dynamic(allocator, TEST_LAYER, true, -1));
            assertThrows(IllegalArgumentException.class,
                () -> PBRVertexConsumer.dynamic(allocator, TEST_LAYER, true, 23));
            PBRVertexConsumer legacy = new PBRVertexConsumer(allocator, TEST_LAYER, 256);
            assertEquals(PBRVertexFormats.PBR_TRIANGLE, legacy.getFormat());
        }
    }

    @Test
    void writesNativeCrossCheckFixtureFromActualConsumers() throws IOException {
        List<byte[][]> records = new ArrayList<>();
        records.add(pair(0, ignored -> {}, consumer -> consumer.addVertex(-0.0F, 2.0F, 3.0F)));
        records.add(pair(22, consumer -> consumer.setBase(4.25F, -0.0F, -8.5F), consumer -> {
                consumer.addVertex(1.0F, 2.0F, 3.0F);
                consumer.setNormal(-0.0F, 0.5F, -1.0F);
                consumer.setColor(700, -20, 128, 512);
                consumer.setColorMix(1, 2, 3, 4);
                consumer.setUv(-0.0F, 1.25F);
                consumer.setUv1(Integer.MIN_VALUE, -123456789);
                consumer.setUv2(Integer.MIN_VALUE, Integer.MAX_VALUE);
                consumer.albedoEmission(Float.intBitsToFloat(0x3F812345));
            }));
        records.add(pair(0, ignored -> {}, consumer -> {
            consumer.addVertex(1.0F, 2.0F, 3.0F);
            consumer.setColorMix(640, -12, 260, 300);
        }));
        records.add(pair(1, ignored -> {}, consumer -> {
                var glint = new PBRVertexConsumer.GLint(consumer, ENTITY_GLINT_LAYER);
                glint.addVertex(2.0F, 3.0F, 4.0F);
                glint.setUv(0.25F, 0.75F);
            }));
        records.add(pair(1, ignored -> {}, consumer -> {
                var overlay = new PBRVertexConsumer.GLintOverlay(consumer, ENTITY_GLINT_LAYER,
                    new PoseStack().last(), 2.0F);
                overlay.addVertex(2.0F, 3.0F, 4.0F);
                overlay.setUv(0.25F, 0.75F);
                overlay.setNormal(0.0F, 1.0F, 0.0F);
            }));
        try (var ignored = PBRMaterialContext.pushEntityTransmission(true)) {
            records.add(pair(22, ignoredConsumer -> {}, consumer ->
                consumer.addVertex(6.0F, 7.0F, 8.0F)));
        }

        ByteBuffer fixture = ByteBuffer.allocate(12 + records.size() * (128 + 100))
            .order(ByteOrder.LITTLE_ENDIAN);
        fixture.putInt(JAVA_FIXTURE_MAGIC).putInt(JAVA_FIXTURE_VERSION)
            .putInt(records.size());
        for (byte[][] record : records) {
            assertReconstructsLegacy(record[0], record[1]);
            assertEquals(128, record[0].length);
            assertEquals(100, record[1].length);
            fixture.put(record[0]).put(record[1]);
        }

        Path destination = Path.of("build", "test-fixtures", "compact-pbr-java.bin");
        Files.createDirectories(destination.getParent());
        Files.write(destination, fixture.array());
    }

    private static byte[] capture(boolean compact, int alphaMode,
        Consumer<PBRVertexConsumer> prepare, Consumer<PBRVertexConsumer> draw) {
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(256)) {
            PBRVertexConsumer consumer = PBRVertexConsumer.dynamic(allocator, TEST_LAYER,
                compact, alphaMode);
            prepare.accept(consumer);
            draw.accept(consumer);
            try (MeshData mesh = consumer.end()) {
                assertEquals(compact ? PBRVertexFormats.PBR_COMPACT_V1
                    : PBRVertexFormats.PBR_TRIANGLE, mesh.drawState().format());
                ByteBuffer data = mesh.vertexBuffer().duplicate();
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                return bytes;
            }
        }
    }

    private static byte[][] pair(int alphaMode, Consumer<PBRVertexConsumer> prepare,
        Consumer<PBRVertexConsumer> draw) {
        return new byte[][]{
            capture(false, alphaMode, prepare, draw),
            capture(true, alphaMode, prepare, draw)
        };
    }

    private static void assertReconstructsLegacy(byte[] legacy, byte[] compact) {
        VertexFormat legacyFormat = PBRVertexFormats.PBR_TRIANGLE;
        VertexFormat compactFormat = PBRVertexFormats.PBR_COMPACT_V1;
        for (VertexFormatElement element : new VertexFormatElement[]{PBR_POS, PBR_NORM,
            PBR_COLOR_LAYER, PBR_TEXTURE_UV, PBR_OVERLAY_UV, PBR_TEXTURE_ID, PBR_GLINT_UV,
            PBR_GLINT_TEXTURE, PBR_LIGHT_UV, PBR_ALBEDO_EMISSION, PBR_POST_BASE}) {
            int legacyOffset = offset(legacyFormat, element);
            int compactOffset = offset(compactFormat, element);
            int length = element.byteSize();
            assertArrayEquals(Arrays.copyOfRange(legacy, legacyOffset, legacyOffset + length),
                Arrays.copyOfRange(compact, compactOffset, compactOffset + length),
                "raw field " + element);
        }

        int legacyPacked = 0;
        int compactPacked = packed(compact);
        legacyPacked |= intAt(legacy, offset(legacyFormat, PBR_USE_NORM)) & 1;
        legacyPacked |= (intAt(legacy, offset(legacyFormat, PBR_USE_COLOR_LAYER)) & 3) << 1;
        legacyPacked |= (intAt(legacy, offset(legacyFormat, PBR_USE_TEXTURE)) & 1) << 3;
        legacyPacked |= (intAt(legacy, offset(legacyFormat, PBR_USE_OVERLAY)) & 1) << 4;
        legacyPacked |= (intAt(legacy, offset(legacyFormat, PBR_USE_GLINT)) & 3) << 5;
        legacyPacked |= (intAt(legacy, offset(legacyFormat, PBR_USE_LIGHT)) & 1) << 7;
        legacyPacked |= (intAt(legacy, offset(legacyFormat, PBR_COORDINATE)) & 0xFF) << 8;
        legacyPacked |= (intAt(legacy, offset(legacyFormat, PBR_POST_BASE) + 12) & 0xFF)
            << 16;
        assertEquals(legacyPacked, compactPacked);
        assertEquals(0, compactPacked >>> 24);
    }

    private static RenderType layer(String name) {
        return new RenderType(name, PBRVertexFormats.PBR_TRIANGLE, VertexFormat.Mode.QUADS,
            256, false, false, () -> {}, () -> {}) {};
    }

    private static int offset(VertexFormat format, VertexFormatElement element) {
        return format.getOffsetsByElement()[element.id()];
    }

    private static int intAt(byte[] data, int offset) {
        return ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).getInt(offset);
    }

    private static float floatAt(byte[] data, int offset) {
        return ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).getFloat(offset);
    }

    private static int packed(byte[] compact) {
        return intAt(compact, offset(PBRVertexFormats.PBR_COMPACT_V1, PBR_PACKED_MODES));
    }
}
