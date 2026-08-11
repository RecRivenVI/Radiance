package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.GlStateBackup;

/**
 * Requested OpenGL face state, independent of texture, alpha and the observing
 * camera.
 */
public final class MaterialFaces {
    public static final int BACK = 1 << 2, FRONT = 1 << 7, CLOCKWISE = 1 << 22;
    private static boolean enabled;
    private static int mode = 1029; // GL_BACK
    private static int winding = 2305; // GL_CCW
    private MaterialFaces() {}
    public static int flags(boolean enabled, int mode, int winding) {
        if (mode != 1028 && mode != 1029 && mode != 1032)
            throw new IllegalArgumentException("Cull face " + mode);
        if (winding != 2304 && winding != 2305)
            throw new IllegalArgumentException("Front face " + winding);
        if (!enabled)
            return 0;
        return (mode != 1028 ? BACK : 0) | (mode != 1029 ? FRONT : 0)
            | (winding == 2304 ? CLOCKWISE : 0);
    }
    public record State(boolean enabled, int mode, int winding) {
        public int flags() {
            return MaterialFaces.flags(enabled, mode, winding);
        }
        public void apply() {
            cullFace(mode);
            frontFace(winding);
            MaterialFaces.enabled(enabled);
        }
    }
    public static State state() {
        return new State(enabled, mode, winding);
    }
    public static int withEnabled(boolean value) {
        return flags(value, mode, winding);
    }
    public static int current() {
        return flags(enabled, mode, winding);
    }
    public static void enabled(boolean value) {
        enabled = value;
        PipelineStateProxy.RasterizationState.vkSetCullMode(value ? (mode == 1028          ? 1
                                                                            : mode == 1029 ? 2
                                                                                           : 3)
                                                                  : 0);
    }
    public static void cullFace(int value) {
        flags(enabled, value, winding);
        mode = value;
        enabled(enabled);
    }
    public static void frontFace(int value) {
        flags(enabled, mode, value);
        winding = value;
        PipelineStateProxy.RasterizationState.vkSetFrontFace(value == 2304 ? 1 : 0);
    }
    public static int encode(int type, int faces) {
        return (type & 255) | (faces << 8);
    }
    /**
     * Execute the real layer callbacks on the render thread; do not query GL or
     * guess from shard identity.
     */
    public static int capture(RenderType layer) {
        RenderSystem.assertOnRenderThread();
        GlStateBackup backup = new GlStateBackup();
        RenderSystem.backupGlState(backup);
        var shader = RenderSystem.getShader();
        var textureMatrix = new org.joml.Matrix4f(RenderSystem.getTextureMatrix());
        var projectionMatrix = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        int previousMode = mode, previousWinding = winding;
        boolean previousEnabled = enabled;
        int[] textures = new int[12];
        for (int i = 0; i < textures.length; i++) textures[i] = RenderSystem.getShaderTexture(i);
        int read = com.radiance.client.proxy.vulkan.FramebufferProxy.boundFramebuffer(36008);
        int draw = com.radiance.client.proxy.vulkan.FramebufferProxy.boundFramebuffer(36009);
        int[] viewport = PipelineStateProxy.ViewportState.getViewport();
        Throwable primary = null;
        try {
            layer.setupRenderState();
            // GL face culling does not apply to original line/point primitives.
            return switch (layer.mode()) {
                case LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP -> 0;
                default -> current();
            };
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                try {
                    layer.clearRenderState();
                } finally {
                    cullFace(previousMode);
                    frontFace(previousWinding);
                    RenderSystem.restoreGlState(backup);
                    enabled(previousEnabled);
                    RenderSystem.setShader(() -> shader);
                    RenderSystem.setTextureMatrix(textureMatrix);
                    RenderSystem.setProjectionMatrix(projectionMatrix, sorting);
                    for (int i = 0; i < textures.length; i++)
                        RenderSystem.setShaderTexture(i, textures[i]);
                    com.radiance.client.proxy.vulkan.FramebufferProxy.bindFramebuffer(36008, read);
                    com.radiance.client.proxy.vulkan.FramebufferProxy.bindFramebuffer(36009, draw);
                    RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                }
            } catch (RuntimeException | Error cleanup) {
                if (primary != null)
                    primary.addSuppressed(cleanup);
                else
                    throw cleanup;
            }
        }
    }
}
