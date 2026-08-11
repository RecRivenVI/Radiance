package com.radiance.compatibility.veil;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.proxy.vulkan.VertexArrayProxy;
import com.radiance.client.render.RenderCaptureContract;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.client.shader.ShaderRegistry;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import org.lwjgl.system.MemoryStack;

/** Draw-time binding between Veil's explicit VertexArray and the current Veil program. */
public final class VeilVertexArrayBridge {

    private static final ThreadLocal<RadianceVertexArray> CURRENT = new ThreadLocal<>();

    private VeilVertexArrayBridge() {
    }

    static void bind(RadianceVertexArray array) {
        CURRENT.set(array);
    }

    public static void unbind() {
        CURRENT.remove();
    }

    static void unbindIfCurrent(RadianceVertexArray array) {
        if (CURRENT.get() == array) CURRENT.remove();
    }

    static void draw(RadianceVertexArray array, int instances) {
        if (instances < 0) throw new IllegalArgumentException("Negative instance count");
        draw(array, -1, 0, 0, instances);
    }

    public static void drawIndirect(int indirectBuffer, long offset, int drawCount, int stride) {
        RadianceVertexArray array = CURRENT.get();
        if (array == null) throw new IllegalStateException("Sable draw requires a bound VertexArray");
        if (indirectBuffer < 0 || offset < 0 || drawCount < 0 || stride < 0) {
            throw new IllegalArgumentException("Invalid Sable indirect draw range");
        }
        draw(array, indirectBuffer, offset, stride, drawCount);
    }

    private static void draw(RadianceVertexArray array, int indirectBuffer, long offset,
        int stride, int count) {
        array.requireLive();
        RenderCaptureContract.BufferDrawDecision decision =
            RenderCaptureContract.classifyBufferDraw();
        if (decision.route() != RenderCaptureContract.BufferDrawRoute.VULKAN_UI
            && decision.route() != RenderCaptureContract.BufferDrawRoute.VULKAN_WORLD_RASTER
            && decision.route() != RenderCaptureContract.BufferDrawRoute.VULKAN_CUSTOM_TARGET) {
            throw RenderCaptureContract.reject(decision, "Veil custom VertexArray draw");
        }
        ShaderProgram program = VeilShaderBridge.currentProgram();
        VertexFormat.Mode mode = array.getDrawMode();
        ShaderDefinition definition = ShaderRegistry.getOrCreate(program.toShaderInstance(), mode);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ShaderProxy.UniformHandle uniform = ShaderProxy.createUniform(definition,
                program.toShaderInstance(), stack);
            if (indirectBuffer >= 0) {
                VertexArrayProxy.drawIndirect(array.nativeId(), definition.nativeId(),
                    indirectBuffer, offset, count, stride, uniform.addr(), uniform.size());
            } else {
                VertexArrayProxy.draw(array.nativeId(), definition.nativeId(),
                    array.getIndexCount(), count, uniform.addr(), uniform.size());
            }
        }
        if (count > 0 && array.getIndexCount() > 0) {
            ShaderRegistry.recordSuccessfulUse(program.toShaderInstance(), mode);
        }
    }
}
