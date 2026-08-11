package com.radiance.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderRegistry;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/** Records ordinary indexed raster draws into the active Vulkan UI/custom-target pass. */
public final class RasterDrawBridge {

    private RasterDrawBridge() {
    }

    public static void drawApplied(BufferProxy.VertexIndexBufferHandle buffers,
        VertexFormat.Mode mode, int indexCount, VertexFormat.IndexType indexType) {
        draw(buffers, mode, indexCount, indexType, AppliedShaderState.requireCurrent());
    }

    public static void drawWithShader(BufferProxy.VertexIndexBufferHandle buffers,
        VertexFormat.Mode mode, int indexCount, VertexFormat.IndexType indexType,
        Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        RenderSystem.assertOnRenderThread();
        if (shader == null) {
            throw new IllegalStateException("Raster draw requires a shader");
        }

        shader.setDefaultUniforms(mode, modelView, projection,
            net.minecraft.client.Minecraft.getInstance().getWindow());
        shader.apply();
        try {
            drawApplied(buffers, mode, indexCount, indexType);
        } finally {
            shader.clear();
        }
    }

    private static void draw(BufferProxy.VertexIndexBufferHandle buffers,
        VertexFormat.Mode mode, int indexCount, VertexFormat.IndexType indexType,
        ShaderInstance shader) {
        RenderSystem.assertOnRenderThread();
        if (buffers == null || buffers.vertexId < 0 || buffers.indexId < 0) {
            throw new IllegalStateException("Raster draw requires live vertex and index buffers");
        }
        if (mode == null || indexType == null || indexCount < 0) {
            throw new IllegalStateException("Raster draw metadata is incomplete");
        }

        RenderCaptureContract.BufferDrawDecision decision =
            RenderCaptureContract.classifyBufferDraw();
        if (decision.route() != RenderCaptureContract.BufferDrawRoute.VULKAN_UI
            && decision.route() != RenderCaptureContract.BufferDrawRoute.VULKAN_WORLD_RASTER
            && decision.route() != RenderCaptureContract.BufferDrawRoute.VULKAN_CUSTOM_TARGET) {
            RenderAuditBridge.transition(0L, "REJECTED", decision.route().name(),
                decision.reason(), true);
            throw RenderCaptureContract.reject(decision, "VertexBuffer raster draw");
        }

        ShaderDefinition definition = ShaderRegistry.getOrCreate(shader, mode);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ShaderProxy.UniformHandle uniform = ShaderProxy.createUniform(definition, shader, stack);
            ShaderProxy.draw(buffers, definition.nativeId(), indexCount,
                Constants.IndexTypes.getValue(indexType), uniform.addr(), uniform.size());
        }
        if (indexCount > 0) {
            ShaderRegistry.recordSuccessfulUse(shader, mode);
        }
        // A producer can submit more than one raster draw (fire is two quads, for example).
        // Record route evidence without closing the producer; its semantic hook owns the end.
        RenderAuditBridge.transition(0L, "TRANSLATED", decision.route().name(),
            "submitted through ShaderProxy.draw", false);
    }
}
