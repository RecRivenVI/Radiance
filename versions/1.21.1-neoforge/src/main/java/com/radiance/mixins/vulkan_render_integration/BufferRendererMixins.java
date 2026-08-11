package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.api.render.RasterDraws;
import com.radiance.api.render.SourceHooks;
import com.radiance.client.proxy.vulkan.BufferProxy;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferUploader.class)
public class BufferRendererMixins {

    @Inject(
            method = "_drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void rewriteDrawWithGlobalProgram(MeshData buffer, CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        try (buffer) {
            ShaderInstance shaderProgram = RenderSystem.getShader();
            if (shaderProgram == null) {
                throw new IllegalStateException(
                        "No active shader for shader draw: " + buffer.drawState().format());
            }
            if (!SourceHooks.validateDraw(
                    shaderProgram,
                    buffer.drawState().format(),
                    buffer.drawState().mode(),
                    buffer.drawState().vertexCount(),
                    buffer.drawState().indexCount())) {
                ci.cancel();
                return;
            }
            BufferProxy.VertexIndexBufferHandle handle =
                    BufferProxy.createAndUploadVertexIndexBuffer(buffer);
            RasterDraws.drawWithShader(
                    handle,
                    buffer.drawState().mode(),
                    buffer.drawState().indexCount(),
                    buffer.drawState().indexType(),
                    RenderSystem.getModelViewMatrix(),
                    RenderSystem.getProjectionMatrix(),
                    shaderProgram);
        }
        ci.cancel();
    }
}
