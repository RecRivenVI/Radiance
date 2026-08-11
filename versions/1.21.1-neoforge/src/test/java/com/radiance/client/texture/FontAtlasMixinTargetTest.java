package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class FontAtlasMixinTargetTest {
    @Test
    void ownerHooksTrackAcquisitionAndOnlySuccessfulFlushes() throws Exception {
        var mixin =
                read("com/radiance/mixins/vulkan_render_integration/FontBatchBufferSourceMixins");
        for (var method : mixin.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals("com/radiance/client/texture/FontAtlasLifecycle")) {
                    assertNotEquals(
                            "register", call.name, "sources without glyphs must not register");
                }
            }
        }
        var release =
                mixin.methods.stream()
                        .filter(m -> m.name.equals("radiance$releaseFontPage"))
                        .findFirst()
                        .orElseThrow();
        var inject =
                release.visibleAnnotations.stream()
                        .filter(
                                a ->
                                        a.desc.equals(
                                                "Lorg/spongepowered/asm/mixin/injection/Inject;"))
                        .findFirst()
                        .orElseThrow();
        int at = inject.values.indexOf("at");
        var points = (List<?>) inject.values.get(at + 1);
        var point = (org.objectweb.asm.tree.AnnotationNode) points.getFirst();
        assertEquals("RETURN", point.values.get(point.values.indexOf("value") + 1));
    }

    @Test
    void actualMinecraftTargetsMatchEveryNewLifecycleHook() throws Exception {
        var fontSet = read("net/minecraft/client/gui/font/FontSet");
        var close =
                fontSet.methods.stream()
                        .filter(m -> m.name.equals("closeTextures") && m.desc.equals("()V"))
                        .findFirst()
                        .orElseThrow();
        boolean closesFontPage = false;
        for (var instruction : close.instructions)
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/client/gui/font/FontTexture")
                    && call.name.equals("close")
                    && call.desc.equals("()V")) closesFontPage = true;
        assertTrue(closesFontPage);
        for (var name : List.of("textureManager", "name", "textures"))
            assertTrue(fontSet.fields.stream().anyMatch(f -> f.name.equals(name)));
        var page = read("net/minecraft/client/gui/font/FontTexture");
        var manager = read("net/minecraft/client/gui/font/FontManager");
        for (var name : List.of("fontSets", "missingFontSet", "lastFontSetCache"))
            assertTrue(manager.fields.stream().anyMatch(f -> f.name.equals(name)), name);
        assertTrue(
                manager.methods.stream()
                        .anyMatch(
                                m ->
                                        (m.name + m.desc)
                                                .equals(
                                                        "getFontSetCached(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/FontSet;")));
        assertTrue(
                page.methods.stream()
                        .anyMatch(m -> m.name.equals("close") && m.desc.equals("()V")));
        var buffer = read("net/minecraft/client/renderer/MultiBufferSource$BufferSource");
        for (var target :
                List.of(
                        "getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                        "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V"))
            assertTrue(
                    buffer.methods.stream().anyMatch(m -> (m.name + m.desc).equals(target)),
                    target);
        var textureManager = read("net/minecraft/client/renderer/texture/TextureManager");
        assertTrue(
                textureManager.methods.stream()
                        .anyMatch(
                                m ->
                                        (m.name + m.desc)
                                                .equals(
                                                        "register(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V")));
        try (var config = getClass().getClassLoader().getResourceAsStream("radiance.mixins.json")) {
            assertNotNull(config);
            assertTrue(
                    new String(config.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .contains("vulkan_render_integration.FontBatchBufferSourceMixins"));
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream stream =
                FontAtlasMixinTargetTest.class
                        .getClassLoader()
                        .getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            var result = new ClassNode();
            new ClassReader(stream)
                    .accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
