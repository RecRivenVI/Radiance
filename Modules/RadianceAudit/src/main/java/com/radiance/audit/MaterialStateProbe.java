package com.radiance.audit;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.client.render.RenderCaptureContract;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/** Opt-in real raster/state regression in the marked isolated client, never in timed profiles. */
public final class MaterialStateProbe {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private MaterialStateProbe() {}

    public static void run(Minecraft mc, String label) {
        if (!"1".equals(ExperimentAccess.getenv("RADIANCE_MATERIAL_STATE_PROBE"))) return;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Material state probe requires isolated instance");
        var saved = new GlStateBackup(); RenderSystem.backupGlState(saved);
        var face = MaterialFaces.state();
        var shader = RenderSystem.getShader();
        var color = RenderSystem.getShaderColor().clone();
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack(); view.pushMatrix();
        int read = FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER);
        int draw = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
        int[] viewport = PipelineStateProxy.ViewportState.getViewport();
        TextureTarget target = null;
        var pixels = MemoryUtil.memAlloc(64 * 64 * 4);
        try (var scope = RenderCaptureContract.enter(RenderCaptureContract.ScopeKind.GUI, "audit-material-state")) {
            target = new TextureTarget(64, 64, true, false);
            target.setClearColor(0, 0, 0, 1); target.clear(false); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 64, 0, 64, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
            view.identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
            RenderSystem.disableCull(); RenderSystem.disableBlend(); RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, true);
            // bindWrite ends the previous pass. These final values must survive idle deferral.
            target.bindWrite(true);
            RenderSystem.enableScissor(0, 0, 64, 16);
            quad(0, 16, 0xFFFF0000);
            // Changes while a pass is active must take effect on the very next draw.
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            quad(0, 16, 0x800000FF);
            RenderSystem.disableBlend();
            target.bindWrite(true);
            AtomicInteger setup = new AtomicInteger(), clear = new AtomicInteger();
            RenderType layer = new RenderType("audit_state", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 256, false, false, () -> {
                    setup.incrementAndGet();
                    quad(16, 32, 0xFF00FF00); // A real draw inside the callback is not suppressed.
                    MaterialFaces.cullFace(1028); MaterialFaces.frontFace(2304);
                    RenderSystem.enableCull();
                    RenderSystem.colorMask(false, false, false, false);
                }, () -> clear.incrementAndGet()) {};
            int flags = MaterialFaces.capture(layer);
            require(flags == (MaterialFaces.FRONT | MaterialFaces.CLOCKWISE), "captured face rule");
            require(setup.get() == 1 && clear.get() == 1, "callbacks must run once");
            require(MaterialFaces.current() == 0, "culling restored");
            RuntimeException injected = new IllegalStateException("expected audit callback failure");
            RenderType throwing = new RenderType("audit_failure", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 256, false, false, () -> {
                    RenderSystem.colorMask(false, false, false, false);
                    RenderSystem.enableDepthTest(); RenderSystem.depthFunc(512); // GL_NEVER
                    throw injected;
                }, () -> clear.incrementAndGet()) {};
            try { MaterialFaces.capture(throwing); throw new IllegalStateException("Expected callback failure"); }
            catch (RuntimeException failure) { if (failure != injected) throw failure; }
            require(clear.get() == 2, "failed callback cleaned up");
            quad(32, 48, 0xFFFFFF00);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(512);
            quad(48, 64, 0xFFFF0000); // Must not reach the target.
            RenderSystem.disableDepthTest();
            RenderSystem.colorMask(false, true, false, true);
            quad(48, 64, 0xFFFFFFFF); // Only green reaches the last region.
            target.bindRead();
            FramebufferProxy.readPixels(0, 0, 64, 64, 0x1908, 0x1401, MemoryUtil.memAddress(pixels));
            check(pixels, 8, 8, 127, 0, 128);
            check(pixels, 24, 8, 0, 255, 0);
            check(pixels, 40, 8, 255, 255, 0);
            check(pixels, 56, 8, 0, 255, 0);
            check(pixels, 8, 32, 0, 0, 0); // Scissor retained over all transitions.
            var output = mc.gameDirectory.toPath().resolve("radiance-audit/material-state");
            Files.createDirectories(output);
            byte[] bytes = new byte[pixels.remaining()]; pixels.get(bytes);
            Files.write(output.resolve(label + ".rgba"), bytes);
            LOG.info("MATERIAL_STATE_PROBE PASS {}: idle/active state, callback draw, face flags, exception restoration, blend/depth/scissor/color-mask", label);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot record material state evidence", failure);
        } finally {
            if (target != null) target.destroyBuffers();
            FramebufferProxy.bindFramebuffer(FramebufferProxy.READ_FRAMEBUFFER, read);
            FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, draw);
            RenderSystem.restoreGlState(saved); face.apply();
            RenderSystem.setProjectionMatrix(projection, sorting);
            view.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            MemoryUtil.memFree(pixels);
        }
    }

    private static void quad(float x0, float x1, int color) {
        var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vertices.addVertex(x0, 0, 0).setColor(color);
        vertices.addVertex(x1, 0, 0).setColor(color);
        vertices.addVertex(x1, 64, 0).setColor(color);
        vertices.addVertex(x0, 64, 0).setColor(color);
        BufferUploader.drawWithShader(vertices.buildOrThrow());
    }

    private static void check(java.nio.ByteBuffer pixels, int x, int y, int r, int g, int b) {
        int offset = (y * 64 + x) * 4;
        int[] expected = {r, g, b};
        for (int c = 0; c < 3; ++c)
            require(Math.abs((pixels.get(offset+c) & 255) - expected[c]) <= 2,
                "pixel " + x + "," + y + " channel " + c + " got " + (pixels.get(offset+c) & 255) + " expected " + expected[c]);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Material state: " + message);
    }
}
