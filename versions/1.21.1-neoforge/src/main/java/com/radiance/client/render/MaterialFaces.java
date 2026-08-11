package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.render.contract.RenderAbi;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.GlStateBackup;

/** Requested OpenGL face state, independent of texture, alpha and the observing camera. */
public final class MaterialFaces {
    public static final int BACK = RenderAbi.FACE_BACK_BIT,
            FRONT = RenderAbi.FACE_FRONT_BIT,
            CLOCKWISE = RenderAbi.FACE_CLOCKWISE_BIT;
    private static boolean enabled;
    private static int mode = 1029; // GL_BACK
    private static int winding = 2305; // GL_CCW

    private MaterialFaces() {}

    public static int flags(boolean enabled, int mode, int winding) {
        if (mode != 1028 && mode != 1029 && mode != 1032)
            throw new IllegalArgumentException("Cull face " + mode);
        if (winding != 2304 && winding != 2305)
            throw new IllegalArgumentException("Front face " + winding);
        if (!enabled) return winding == 2304 ? CLOCKWISE : 0;
        return (mode != 1028 ? BACK : 0)
                | (mode != 1029 ? FRONT : 0)
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
        PipelineStateProxy.RasterizationState.vkSetCullMode(
                value ? (mode == 1028 ? 1 : mode == 1029 ? 2 : 3) : 0);
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
        return (type & 255) | (faces << RenderAbi.FACE_GEOMETRY_SHIFT);
    }

    /**
     * Execute the real layer callbacks on the render thread; do not query GL or guess from shard
     * identity.
     */
    public static int capture(RenderType layer) {
        return capture(layer, null);
    }

    public static int capture(RenderType layer, java.util.function.DoubleConsumer lineWidth) {
        var snapshot = captureSnapshot(layer);
        if (lineWidth != null) lineWidth.accept(snapshot.lineWidth());
        return snapshot.faces();
    }

    public static com.radiance.render.contract.DrawStateSnapshot captureSnapshot(RenderType layer) {
        RenderSystem.assertOnRenderThread();
        GlStateBackup backup = new GlStateBackup();
        RenderSystem.backupGlState(backup);
        var shader = RenderSystem.getShader();
        var shaderColour = RenderSystem.getShaderColor().clone();
        var textureMatrix = new org.joml.Matrix4f(RenderSystem.getTextureMatrix());
        var projectionMatrix = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        int previousMode = mode, previousWinding = winding;
        boolean previousEnabled = enabled;
        float previousLineWidth = RenderSystem.getShaderLineWidth();
        int[] textures = new int[12];
        for (int i = 0; i < textures.length; i++) textures[i] = RenderSystem.getShaderTexture(i);
        int[] routing =
                PipelineStateProxy.BATCH_MATERIAL_STATE
                        ? com.radiance.client.proxy.vulkan.FramebufferProxy.captureMaterialRouting()
                        : null;
        int read =
                routing != null
                        ? routing[0]
                        : com.radiance.client.proxy.vulkan.FramebufferProxy.boundFramebuffer(36008);
        int draw =
                routing != null
                        ? routing[1]
                        : com.radiance.client.proxy.vulkan.FramebufferProxy.boundFramebuffer(36009);
        int[] viewport = routing != null ? routing : PipelineStateProxy.ViewportState.getViewport();
        int viewportOffset = routing != null ? 2 : 0;
        Throwable primary = null;
        try {
            layer.setupRenderState();
            var colour = RenderSystem.getShaderColor();
            // GL face culling does not apply to original line/point primitives.
            int faces =
                    switch (layer.mode()) {
                        case LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP -> 0;
                        default -> current();
                    };
            return new com.radiance.render.contract.DrawStateSnapshot(
                    faces,
                    RenderSystem.getShaderLineWidth(),
                    colour[0],
                    colour[1],
                    colour[2],
                    colour[3],
                    com.radiance.compatibility.vanilla.VanillaMaterialAdapter.primaryUv(
                            layer, RenderSystem.getTextureMatrix()));
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
                    RenderSystem.lineWidth(previousLineWidth);
                    RenderSystem.setShader(() -> shader);
                    RenderSystem.setShaderColor(
                            shaderColour[0], shaderColour[1], shaderColour[2], shaderColour[3]);
                    RenderSystem.setTextureMatrix(textureMatrix);
                    RenderSystem.setProjectionMatrix(projectionMatrix, sorting);
                    for (int i = 0; i < textures.length; i++)
                        RenderSystem.setShaderTexture(i, textures[i]);
                    if (PipelineStateProxy.BATCH_MATERIAL_STATE) {
                        com.radiance.client.proxy.vulkan.FramebufferProxy.bindFramebufferPair(
                                read, draw);
                    } else {
                        com.radiance.client.proxy.vulkan.FramebufferProxy.bindFramebuffer(
                                36008, read);
                        com.radiance.client.proxy.vulkan.FramebufferProxy.bindFramebuffer(
                                36009, draw);
                    }
                    // Mojang's observable viewport cache still follows the original method.
                    RenderSystem.viewport(
                            viewport[viewportOffset],
                            viewport[viewportOffset + 1],
                            viewport[viewportOffset + 2],
                            viewport[viewportOffset + 3]);
                }
            } catch (RuntimeException | Error cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }
}
