package com.radiance.client.render;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.client.proxy.vulkan.FramebufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** Executes fixed raster world effects against the resolved path-traced color and depth. */
public final class WorldRasterPass {
    private WorldRasterPass() {}

    public static void defer(String label, Matrix4f view, Matrix4f projection, Runnable draw) {
        defer(0, label, view, projection, draw);
    }

    public static void defer(int order, String label, Matrix4f view, Matrix4f projection, Runnable draw) {
        Matrix4f savedView = new Matrix4f(view);
        Matrix4f savedProjection = new Matrix4f(projection);
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        if (!AfterWorldRender.defer(order, () -> render(label, savedView, savedProjection,
            fogStart, fogEnd, fogColor, fogShape, draw))) {
            throw new IllegalStateException("World raster effect requires an active world frame: " + label);
        }
    }

    private static void render(String label, Matrix4f view, Matrix4f projection,
        float fogStart, float fogEnd, float[] fogColor, FogShape fogShape, Runnable draw) {
        GlStateBackup state = new GlStateBackup();
        RenderSystem.backupGlState(state);
        int read = FramebufferProxy.boundFramebuffer(FramebufferProxy.READ_FRAMEBUFFER);
        int target = FramebufferProxy.boundFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER);
        int[] viewport = PipelineStateProxy.ViewportState.getViewport();
        float previousStart = RenderSystem.getShaderFogStart();
        float previousEnd = RenderSystem.getShaderFogEnd();
        float[] previousColor = RenderSystem.getShaderFogColor().clone();
        FogShape previousShape = RenderSystem.getShaderFogShape();
        float[] previousShaderColor = RenderSystem.getShaderColor().clone();
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try (RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.WORLD_RASTER, label)) {
            SectionRasterStorage.drain();
            FramebufferProxy.bindFramebuffer(FramebufferProxy.FRAMEBUFFER, 0);
            int[] size = FramebufferProxy.dimensions(FramebufferProxy.DRAW_FRAMEBUFFER);
            RenderSystem.viewport(0, 0, size[0], size[1]);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.set(view);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            RenderSystem.setShaderFogShape(fogShape);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            draw.run();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderFogStart(previousStart);
            RenderSystem.setShaderFogEnd(previousEnd);
            RenderSystem.setShaderFogColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.setShaderFogShape(previousShape);
            RenderSystem.setShaderColor(previousShaderColor[0], previousShaderColor[1],
                previousShaderColor[2], previousShaderColor[3]);
            FramebufferProxy.bindFramebuffer(FramebufferProxy.READ_FRAMEBUFFER, read);
            FramebufferProxy.bindFramebuffer(FramebufferProxy.DRAW_FRAMEBUFFER, target);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.restoreGlState(state);
        }
    }
}
