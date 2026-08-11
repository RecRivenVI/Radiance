package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderRegistry;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.ShaderInstance;
import org.junit.jupiter.api.Test;

class VeilVanillaShaderReloadBridgeTest {

    @Test
    void publishesOnlyACompleteProcessedStagePair() {
        VeilVanillaShaderReloadBridge.PendingSources pending =
            new VeilVanillaShaderReloadBridge.PendingSources(3);

        assertNull(VeilVanillaShaderReloadBridge.collectForTest(
            pending, true, "processed vertex", 3));
        VeilVanillaShaderReloadBridge.ProcessedSources sources =
            VeilVanillaShaderReloadBridge.collectForTest(
                pending, false, "processed fragment", 3);

        assertEquals("processed vertex", sources.vertexSource());
        assertEquals("processed fragment", sources.fragmentSource());
        assertEquals(3, sources.activeBuffers());
    }

    @Test
    void changingDynamicBuffersDiscardsAnOlderHalfPair() {
        VeilVanillaShaderReloadBridge.PendingSources pending =
            new VeilVanillaShaderReloadBridge.PendingSources(1);

        assertNull(VeilVanillaShaderReloadBridge.collectForTest(
            pending, true, "stale vertex", 1));
        assertNull(VeilVanillaShaderReloadBridge.collectForTest(
            pending, false, "fresh fragment", 2));
        VeilVanillaShaderReloadBridge.ProcessedSources sources =
            VeilVanillaShaderReloadBridge.collectForTest(
                pending, true, "fresh vertex", 2);

        assertEquals("fresh vertex", sources.vertexSource());
        assertEquals("fresh fragment", sources.fragmentSource());
        assertEquals(2, sources.activeBuffers());
    }

    @Test
    void overlappingReloadsWithTheSameMaskCannotMixStages() {
        VeilVanillaShaderReloadBridge.ShaderReloads reloads =
            new VeilVanillaShaderReloadBridge.ShaderReloads();
        reloads.begin(10, 7);
        reloads.begin(11, 7);

        assertNull(reloads.capture(10, true, "old vertex", 7));
        assertNull(reloads.capture(11, false, "new fragment", 7));
        VeilVanillaShaderReloadBridge.ProcessedSources oldSources =
            reloads.capture(10, false, "old fragment", 7);
        VeilVanillaShaderReloadBridge.ProcessedSources newSources =
            reloads.capture(11, true, "new vertex", 7);

        assertEquals("old vertex", oldSources.vertexSource());
        assertEquals("old fragment", oldSources.fragmentSource());
        assertEquals("new vertex", newSources.vertexSource());
        assertEquals("new fragment", newSources.fragmentSource());
        assertEquals(false, reloads.isLatest(10));
        assertEquals(true, reloads.isLatest(11));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shaderRegistryDropsEveryDrawModeBeforeSourceReplacement() throws Exception {
        Field cacheField = ShaderRegistry.class.getDeclaredField("CACHE");
        Field liveField = ShaderRegistry.class.getDeclaredField("LIVE_SHADERS");
        cacheField.setAccessible(true);
        liveField.setAccessible(true);
        Map<ShaderInstance, Map<VertexFormat.Mode, ShaderDefinition>> cache =
            (Map<ShaderInstance, Map<VertexFormat.Mode, ShaderDefinition>>) cacheField.get(null);
        Map<ShaderInstance, Long> live = (Map<ShaderInstance, Long>) liveField.get(null);
        Map<VertexFormat.Mode, ShaderDefinition> variants =
            new EnumMap<>(VertexFormat.Mode.class);
        variants.put(VertexFormat.Mode.QUADS,
            new ShaderDefinition("old-quads", "test", 1, 0, List.of()));
        variants.put(VertexFormat.Mode.TRIANGLES,
            new ShaderDefinition("old-triangles", "test", 2, 0, List.of()));
        cache.put(null, variants);
        live.put(null, 1L);

        ShaderRegistry.unregisterLiveShader(null);

        assertFalse(cache.containsKey(null));
        assertFalse(live.containsKey(null));
    }

    @Test
    void discoversCpuUniformsAddedByFixedSablePreprocessorsInSourceOrder() {
        List<VeilVanillaShaderReloadBridge.UniformSpec> uniforms =
            VeilVanillaShaderReloadBridge.collectUniforms("""
                uniform mat3 NormalMat;
                uniform float SableEnableNormalLighting;
                uniform float SableSkyLightScale;
                """, """
                uniform vec2 ScreenSize;
                uniform sampler2D SableCloseSampler;
                uniform sampler2D SableFarSampler;
                uniform float SableWaterOcclusionEnabled;
                """, Set.of("NormalMat", "ScreenSize"));

        assertEquals(List.of(
            new VeilVanillaShaderReloadBridge.UniformSpec(
                "SableEnableNormalLighting", 4, 1),
            new VeilVanillaShaderReloadBridge.UniformSpec("SableSkyLightScale", 4, 1),
            new VeilVanillaShaderReloadBridge.UniformSpec(
                "SableWaterOcclusionEnabled", 4, 1)), uniforms);
    }

    @Test
    void oneElementArrayKeepsItsElementName() {
        assertEquals(List.of(new VeilVanillaShaderReloadBridge.UniformSpec("Brightness[0]", 4, 1)),
            VeilVanillaShaderReloadBridge.collectUniforms(
                "uniform float[1] Brightness;", "", Set.of()));
    }

    @Test
    void actualVeilLightIncludeCreatesSixIndividuallySettableCpuUniforms() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(
            "assets/veil/pinwheel/shaders/include/light.glsl")) {
            if (input == null) {
                throw new IllegalStateException("Missing actual Veil 4.3.2 light include");
            }
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List<VeilVanillaShaderReloadBridge.UniformSpec> uniforms =
                VeilVanillaShaderReloadBridge.collectUniforms(source, "", Set.of());

            assertEquals(6, uniforms.size());
            for (int i = 0; i < 6; i++) {
                assertEquals(new VeilVanillaShaderReloadBridge.UniformSpec(
                    "VeilBlockFaceBrightness[" + i + ']', 4, 1), uniforms.get(i));
            }
        }
    }
}
