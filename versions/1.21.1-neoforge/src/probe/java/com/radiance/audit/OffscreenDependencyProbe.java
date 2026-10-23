package com.radiance.audit;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.render.MaterialFaces;
import com.radiance.client.render.RenderCaptureContract;
import com.radiance.render.contract.DrawTargetContract;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/** Real attachment write -> shader sampling -> later alpha draw -> GPU readback. */
final class OffscreenDependencyProbe {
    private OffscreenDependencyProbe() {}

    static void verify(String boundary) {
        var saved = new GlStateBackup();
        RenderSystem.backupGlState(saved);
        var faces = MaterialFaces.state();
        var shader = RenderSystem.getShader();
        var colour = RenderSystem.getShaderColor().clone();
        int texture = RenderSystem.getShaderTexture(0);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        int[] routing = FramebufferProxy.captureMaterialRouting();
        int[] viewport = PipelineStateProxy.ViewportState.getViewport();
        TextureTarget a = null, b = null;
        var pixels = MemoryUtil.memAlloc(64 * 64 * 4);
        view.pushMatrix();
        try (var scope =
                RenderCaptureContract.enter(
                        DrawTargetContract.ScopeKind.GUI, "takeover-offscreen")) {
            a = new TextureTarget(64, 64, true, false);
            b = new TextureTarget(64, 64, true, false);
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(0, 64, 0, 64, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
            view.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableScissor();
            RenderSystem.disableBlend();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            a.setClearColor(0, 0, 0, 1);
            a.clear(false);
            a.bindWrite(true);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            colourQuad(0, 64, 0xffff0000);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            colourQuad(0, 32, 0x800000ff);
            b.setClearColor(0, 0, 0, 1);
            b.clear(false);
            b.bindWrite(true);
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, a.getColorTextureId());
            var q =
                    Tesselator.getInstance()
                            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            q.addVertex(0, 0, 0).setUv(0, 0).setColor(-1);
            q.addVertex(64, 0, 0).setUv(1, 0).setColor(-1);
            q.addVertex(64, 64, 0).setUv(1, 1).setColor(-1);
            q.addVertex(0, 64, 0).setUv(0, 1).setColor(-1);
            BufferUploader.drawWithShader(q.buildOrThrow());
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            colourQuad(32, 64, 0x4000ff00);
            b.bindRead();
            com.radiance.client.render.AttachmentOperations.readPixels(
                    0, 0, 64, 64, 0x1908, 0x1401, MemoryUtil.memAddress(pixels));
            check(pixels, 8, 32, 127, 0, 128);
            check(pixels, 48, 32, 191, 64, 0);
            verifyRasterState(b, pixels);
            verifyCoverageAndFont(b, pixels);
            com.mojang.logging.LogUtils.getLogger()
                    .info(
                            "TAKEOVER_OFFSCREEN boundary={} attachment-sample=PASS later-alpha=PASS gpu-readback=PASS",
                            boundary);
        } finally {
            PipelineStateProxy.DepthStencilState.setStencilTestEnable(false);
            if (a != null) a.destroyBuffers();
            if (b != null) b.destroyBuffers();
            FramebufferProxy.bindFramebufferPair(routing[0], routing[1]);
            RenderSystem.restoreGlState(saved);
            faces.apply();
            RenderSystem.setProjectionMatrix(projection, sorting);
            view.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(colour[0], colour[1], colour[2], colour[3]);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            MemoryUtil.memFree(pixels);
        }
    }

    private static void verifyRasterState(TextureTarget target, java.nio.ByteBuffer pixels) {
        target.setClearColor(0, 0, 0, 1);
        target.clear(false);
        target.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.viewport(8, 8, 48, 48);
        colourQuad(0, 64, 0xffffffff);
        RenderSystem.enableScissor(24, 24, 16, 16);
        colourQuad(0, 64, 0xffff0000);
        read(target, pixels);
        check(pixels, 2, 2, 0, 0, 0);
        check(pixels, 16, 16, 255, 255, 255);
        check(pixels, 32, 32, 255, 0, 0);

        RenderSystem.disableScissor();
        RenderSystem.viewport(0, 0, 64, 64);
        RenderSystem.depthMask(true);
        target.clear(false);
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(0x0201);
        RenderSystem.depthMask(true);
        colourQuad(0, 64, 0xff0000ff, -.5f);
        colourQuad(0, 64, 0xffff0000, .5f);
        colourQuad(0, 64, 0xff00ff00, -.5f);
        read(target, pixels);
        check(pixels, 32, 32, 255, 0, 0);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        target.enableStencil();
        // RenderTarget.resize re-enables depth testing; clear() does not clear stencil.
        RenderSystem.disableDepthTest();
        RenderSystem.stencilMask(255);
        RenderSystem.clearStencil(0);
        target.clear(false);
        target.bindWrite(true);
        RenderSystem.clear(0x400, false);
        PipelineStateProxy.DepthStencilState.setStencilTestEnable(true);
        RenderSystem.stencilMask(255);
        RenderSystem.stencilFunc(0x0207, 1, 255);
        RenderSystem.stencilOp(0x1e00, 0x1e00, 0x1e01);
        RenderSystem.colorMask(false, false, false, false);
        colourQuad(0, 32, 0xffffffff);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.stencilMask(0);
        RenderSystem.stencilFunc(0x0202, 1, 255);
        RenderSystem.stencilOp(0x1e00, 0x1e00, 0x1e00);
        colourQuad(0, 64, 0xff00ff00);
        read(target, pixels);
        check(pixels, 16, 32, 0, 255, 0);
        check(pixels, 48, 32, 0, 0, 0);
        PipelineStateProxy.DepthStencilState.setStencilTestEnable(false);
        com.mojang.logging.LogUtils.getLogger()
                .info(
                        "TAKEOVER_RASTER_STATE viewport=PASS scissor=PASS depth=PASS stencil=PASS color-mask=PASS");
    }

    private static void read(TextureTarget target, java.nio.ByteBuffer pixels) {
        target.bindRead();
        com.radiance.client.render.AttachmentOperations.readPixels(
                0, 0, 64, 64, 0x1908, 0x1401, MemoryUtil.memAddress(pixels));
    }

    private static void verifyCoverageAndFont(TextureTarget target, java.nio.ByteBuffer pixels) {
        RenderSystem.disableScissor();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.viewport(0, 0, 64, 64);
        var image = new com.mojang.blaze3d.platform.NativeImage(3, 1, false);
        image.setPixelRGBA(0, 0, 0x000000ff);
        image.setPixelRGBA(1, 0, 0x800000ff);
        image.setPixelRGBA(2, 0, 0xff0000ff);
        var mc = net.minecraft.client.Minecraft.getInstance();
        var id =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "radiance_probe", "coverage");
        mc.getTextureManager()
                .register(id, new net.minecraft.client.renderer.texture.DynamicTexture(image));
        try {
            target.setClearColor(0, 0, 1, 1);
            target.clear(false);
            target.bindWrite(true);
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, id);
            textureQuad();
            read(target, pixels);
            check(pixels, 8, 32, 0, 0, 255);
            check(pixels, 32, 32, 255, 0, 0);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            target.clear(false);
            target.bindWrite(true);
            textureQuad();
            read(target, pixels);
            check(pixels, 32, 32, 128, 0, 127);
        } finally {
            mc.getTextureManager().release(id);
        }

        target.setClearColor(0, 0, 0, 1);
        RenderSystem.depthMask(true);
        target.clear(false);
        target.bindWrite(true);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // Font quads use the GUI's downward Y axis and its corresponding front winding.
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0, 64, 64, 0, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
        var buffers = mc.renderBuffers().bufferSource();
        mc.font.drawInBatch(
                "Ab",
                1,
                10,
                0xffffffff,
                false,
                new Matrix4f(),
                buffers,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0,
                15728880);
        buffers.endBatch();
        read(target, pixels);
        byte[] full = new byte[pixels.capacity()];
        for (int i = 0; i < full.length; i++) full[i] = pixels.get(i);
        RenderSystem.depthMask(true);
        target.clear(false);
        target.bindWrite(true);
        mc.font.drawInBatch(
                "Ab",
                1,
                10,
                0x80ffffff,
                false,
                new Matrix4f(),
                buffers,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0,
                15728880);
        buffers.endBatch();
        read(target, pixels);
        int visible = 0;
        for (int i = 0; i < full.length; i += 4) {
            int a = full[i] & 255, b = pixels.get(i) & 255;
            if (a > 0) visible++;
            if (Math.abs(b - a * 128.0 / 255) > 2)
                throw new IllegalStateException("Font alpha/fade GPU mismatch");
        }
        if (visible < 10) throw new IllegalStateException("Font coverage probe was empty");
        com.mojang.logging.LogUtils.getLogger()
                .info("TAKEOVER_COVERAGE cutout=PASS alpha=PASS font-fade=PASS order=PASS");
    }

    private static void textureQuad() {
        var q =
                Tesselator.getInstance()
                        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        q.addVertex(0, 0, 0).setUv(0, 0).setColor(-1);
        q.addVertex(64, 0, 0).setUv(1, 0).setColor(-1);
        q.addVertex(64, 64, 0).setUv(1, 1).setColor(-1);
        q.addVertex(0, 64, 0).setUv(0, 1).setColor(-1);
        BufferUploader.drawWithShader(q.buildOrThrow());
    }

    private static void colourQuad(float x0, float x1, int color) {
        colourQuad(x0, x1, color, 0);
    }

    private static void colourQuad(float x0, float x1, int color, float z) {
        var q =
                Tesselator.getInstance()
                        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        q.addVertex(x0, 0, z).setColor(color);
        q.addVertex(x1, 0, z).setColor(color);
        q.addVertex(x1, 64, z).setColor(color);
        q.addVertex(x0, 64, z).setColor(color);
        BufferUploader.drawWithShader(q.buildOrThrow());
    }

    private static void check(java.nio.ByteBuffer pixels, int x, int y, int r, int g, int b) {
        int at = (y * 64 + x) * 4;
        int[] expected = {r, g, b};
        for (int i = 0; i < 3; i++)
            if (Math.abs((pixels.get(at + i) & 255) - expected[i]) > 2)
                throw new IllegalStateException(
                        "Offscreen GPU dependency pixel "
                                + x
                                + ","
                                + y
                                + " channel="
                                + i
                                + " actual="
                                + (pixels.get(at + i) & 255)
                                + " expected="
                                + expected[i]);
    }
}
