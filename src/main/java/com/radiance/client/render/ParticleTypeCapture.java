package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixins.vulkan_render_integration.accessor.ParticleBufferBuilderAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.GlStateBackup;

/** Captures the real NeoForge begin() texture/state before replacing particle vertices with PT data. */
public final class ParticleTypeCapture implements AutoCloseable {
    private final GlStateBackup previous = new GlStateBackup();
    private final ShaderInstance previousShader = RenderSystem.getShader();
    private final int previousTexture = RenderSystem.getShaderTexture(0);
    private RenderType layer;
    private final MaterialFaces.State previousFaces = MaterialFaces.state();

    private ParticleTypeCapture() { RenderSystem.backupGlState(previous); }

    public static ParticleTypeCapture begin(ParticleRenderType type) {
        ParticleTypeCapture capture = new ParticleTypeCapture();
        try {
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.enableDepthTest();
            if (type == ParticleRenderType.CUSTOM) {
                // CUSTOM has no sheet binding; custom renderers normally acquire their own layers.
                // Its direct particle vertices retain the particle atlas material.
                RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            }
            BufferBuilder builder = type.begin(Tesselator.getInstance(), Minecraft.getInstance().getTextureManager());
            if (builder == null) return capture;
            ParticleBufferBuilderAccessor metadata = (ParticleBufferBuilderAccessor) builder;
            VertexFormat format = metadata.radiance$particleFormat();
            VertexFormat.Mode mode = metadata.radiance$particleMode();
            try (MeshData initial = builder.build()) {
                if (initial != null) throw new UnsupportedOperationException("Particle begin emitted geometry: " + type);
            }
            if (format != DefaultVertexFormat.PARTICLE || mode != VertexFormat.Mode.QUADS) {
                throw new UnsupportedOperationException("Custom particle layout has no PT adapter: " + type);
            }
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != GameRenderer.getParticleShader()) {
                throw new UnsupportedOperationException("Custom particle shader has no PT material adapter: " + type);
            }
            int textureId = RenderSystem.getShaderTexture(0);
            ResourceLocation texture = TextureTracker.textureID2GLID.entrySet().stream()
                .filter(entry -> entry.getValue() == textureId).map(java.util.Map.Entry::getKey)
                .findFirst().orElseThrow(() -> new IllegalStateException("Particle texture is not registered: " + textureId));
            GlStateBackup state = new GlStateBackup();
            RenderSystem.backupGlState(state);
            capture.layer = Layers.create(texture, state, MaterialFaces.state());
            return capture;
        } catch (RuntimeException | Error failure) {
            capture.close();
            throw failure;
        }
    }

    public RenderType layer() { return layer; }

    enum Blend { OPAQUE, SOURCE_OVER, ADDITIVE }

    static Blend blend(GlStateBackup state) {
        if (!state.blendEnabled) return Blend.OPAQUE;
        if (state.blendSrcRgb == 770 && state.blendDestRgb == 771) return Blend.SOURCE_OVER;
        if (state.blendSrcRgb == 1 && state.blendDestRgb == 1) return Blend.ADDITIVE;
        throw new UnsupportedOperationException("Particle blend has no PT adapter: "
            + state.blendSrcRgb + "/" + state.blendDestRgb);
    }

    @Override public void close() {
        RenderSystem.restoreGlState(previous);
        previousFaces.apply();
        RenderSystem.setShaderTexture(0, previousTexture);
        RenderSystem.setShader(() -> previousShader);
    }

    private static final class Layers extends RenderType {
        private Layers() { super("particle_capture", DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}); }

        private static RenderType create(ResourceLocation texture, GlStateBackup state, MaterialFaces.State faces) {
            var transparency = switch (blend(state)) {
                case OPAQUE -> NO_TRANSPARENCY;
                case SOURCE_OVER -> TRANSLUCENT_TRANSPARENCY;
                case ADDITIVE -> ADDITIVE_TRANSPARENCY;
            };
            if (state.depthEnabled && state.depthFunc != 515) {
                throw new UnsupportedOperationException("Particle depth comparison has no PT adapter: " + state.depthFunc);
            }
            return RenderType.create("radiance_particle_capture", DefaultVertexFormat.PARTICLE,
                VertexFormat.Mode.QUADS, 256, false, state.blendEnabled,
                CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getParticleShader))
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(transparency).setCullState(state.cullEnabled ? CULL : NO_CULL)
                    .setLayeringState(new LayeringStateShard("captured_particle_faces", faces::apply, () -> {}))
                    .setDepthTestState(state.depthEnabled ? LEQUAL_DEPTH_TEST : NO_DEPTH_TEST)
                    .setWriteMaskState(state.depthMask ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                    .setLightmapState(LIGHTMAP).createCompositeState(false));
        }
    }
}
