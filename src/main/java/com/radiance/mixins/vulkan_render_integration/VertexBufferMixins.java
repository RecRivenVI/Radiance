package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.render.RasterDrawBridge;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's long-lived OpenGL vertex buffers with native Vulkan resources.
 * <p>
 * The priority is deliberately below Veil's default so this mixin is applied later and its
 * {@code draw} HEAD callback runs before Veil's {@code drawPatches} handler; otherwise Veil
 * issues raw {@code glGetInteger} calls that crash the Vulkan-only client.
 */
@Mixin(value = VertexBuffer.class, priority = 500)
public class VertexBufferMixins {

    @Shadow
    private int vertexBufferId;
    @Shadow
    private int indexBufferId;
    @Shadow
    private int arrayObjectId;
    @Shadow
    @Nullable
    private VertexFormat format;
    @Shadow
    @Nullable
    private RenderSystem.AutoStorageIndexBuffer sequentialIndices;
    @Shadow
    private VertexFormat.IndexType indexType;
    @Shadow
    private int indexCount;
    @Shadow
    private VertexFormat.Mode mode;
    @Unique
    private int radiance$patchIndexBufferId = -1;
    @Unique
    private int radiance$patchIndexCount;
    @Unique
    private int radiance$vertexCount;

    @Redirect(method = "<init>(Lcom/mojang/blaze3d/vertex/VertexBuffer$Usage;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int skipLegacyBufferAllocation() {
        return 0;
    }

    @Redirect(method = "<init>(Lcom/mojang/blaze3d/vertex/VertexBuffer$Usage;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int skipLegacyVertexArrayAllocation() {
        return 0;
    }

    @Inject(method = "<init>(Lcom/mojang/blaze3d/vertex/VertexBuffer$Usage;)V",
        at = @At("RETURN"))
    private void allocatePersistentBuffers(VertexBuffer.Usage usage, CallbackInfo ci) {
        BufferProxy.VertexIndexBufferHandle buffers = BufferProxy.allocatePersistentBufferPair();
        this.vertexBufferId = buffers.vertexId;
        this.indexBufferId = buffers.indexId;
        this.arrayObjectId = 0;
    }

    @Inject(method = "upload(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("HEAD"),
        cancellable = true)
    private void uploadPersistentMesh(MeshData mesh, CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        try (mesh) {
            if (this.arrayObjectId == -1) {
                ci.cancel();
                return;
            }

            MeshData.DrawState drawState = mesh.drawState();
            BufferProxy.VertexIndexBufferHandle replacement =
                BufferProxy.createAndUploadPersistentVertexIndexBuffer(mesh);
            BufferProxy.VertexIndexBufferHandle previous =
                new BufferProxy.VertexIndexBufferHandle(this.vertexBufferId, this.indexBufferId,
                    this.radiance$patchIndexBufferId, this.radiance$patchIndexCount);

            this.vertexBufferId = replacement.vertexId;
            this.indexBufferId = replacement.indexId;
            this.radiance$patchIndexBufferId = replacement.patchIndexId;
            this.radiance$patchIndexCount = replacement.patchIndexCount;
            this.radiance$vertexCount = drawState.vertexCount();
            this.format = drawState.format();
            this.sequentialIndices = null;
            this.indexType = drawState.indexType();
            this.indexCount = drawState.indexCount();
            this.mode = drawState.mode();
            BufferProxy.releasePersistentPair(previous);
        }
        ci.cancel();
    }

    @Inject(method = "uploadIndexBuffer(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder$Result;)V",
        at = @At("HEAD"), cancellable = true)
    private void uploadPersistentIndexBuffer(ByteBufferBuilder.Result result, CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        try (result) {
            if (this.arrayObjectId == -1) {
                ci.cancel();
                return;
            }
            if (this.indexType == null) {
                throw new IllegalStateException(
                    "VertexBuffer index data cannot be replaced before a mesh upload");
            }

            int patchReplacement = this.mode == VertexFormat.Mode.QUADS
                ? BufferProxy.createAndUploadPersistentPatchIndexBuffer(result.byteBuffer(),
                    this.indexType, this.indexCount, this.radiance$vertexCount)
                : -1;
            int replacement;
            try {
                replacement = BufferProxy.createAndUploadPersistentIndexBuffer(
                    result.byteBuffer());
            } catch (RuntimeException | Error failure) {
                BufferProxy.releasePersistentBuffer(patchReplacement);
                throw failure;
            }
            int previous = this.indexBufferId;
            int previousPatch = this.radiance$patchIndexBufferId;
            this.indexBufferId = replacement;
            this.radiance$patchIndexBufferId = patchReplacement;
            this.radiance$patchIndexCount = patchReplacement < 0 ? 0 : this.radiance$vertexCount;
            this.sequentialIndices = null;
            BufferProxy.releasePersistentBuffer(previous);
            BufferProxy.releasePersistentBuffer(previousPatch);
        }
        ci.cancel();
    }

    @Inject(method = "bind()V", at = @At("HEAD"), cancellable = true)
    private void bindPersistentBuffer(CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        if (this.arrayObjectId == -1) {
            throw new IllegalStateException("Cannot bind a closed VertexBuffer");
        }
        ci.cancel();
    }

    @Inject(method = "unbind()V", at = @At("HEAD"), cancellable = true)
    private static void unbindPersistentBuffer(CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();
        ci.cancel();
    }

    @Inject(method = "draw()V", at = @At("HEAD"), cancellable = true)
    private void drawPersistentBuffer(CallbackInfo ci) {
        if (this.arrayObjectId == -1) {
            throw new IllegalStateException("Cannot draw a closed VertexBuffer");
        }
        RasterDrawBridge.drawApplied(
            new BufferProxy.VertexIndexBufferHandle(this.vertexBufferId, this.indexBufferId,
                this.radiance$patchIndexBufferId, this.radiance$patchIndexCount),
            // The native draw selects this four-control-point index only for tessellation.
            this.mode, this.indexCount, this.indexType);
        ci.cancel();
    }

    @Inject(method = "drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
        at = @At("HEAD"), cancellable = true)
    private void drawPersistentBufferWithShader(Matrix4f modelView, Matrix4f projection,
        ShaderInstance shader, CallbackInfo ci) {
        if (this.arrayObjectId == -1) {
            throw new IllegalStateException("Cannot draw a closed VertexBuffer");
        }
        BufferProxy.VertexIndexBufferHandle buffers =
            new BufferProxy.VertexIndexBufferHandle(this.vertexBufferId, this.indexBufferId,
                this.radiance$patchIndexBufferId, this.radiance$patchIndexCount);
        VertexFormat.Mode drawMode = this.mode;
        int drawIndexCount = this.indexCount;
        VertexFormat.IndexType drawIndexType = this.indexType;
        if (!RenderSystem.isOnRenderThread()) {
            Matrix4f modelViewCopy = new Matrix4f(modelView);
            Matrix4f projectionCopy = new Matrix4f(projection);
            RenderSystem.recordRenderCall(() -> RasterDrawBridge.drawWithShader(buffers,
                drawMode, drawIndexCount, drawIndexType, modelViewCopy, projectionCopy, shader));
        } else {
            RasterDrawBridge.drawWithShader(buffers, drawMode, drawIndexCount, drawIndexType,
                modelView, projection, shader);
        }
        ci.cancel();
    }

    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true)
    private void closePersistentBuffers(CallbackInfo ci) {
        int vertexId = this.vertexBufferId;
        int indexId = this.indexBufferId;
        int patchIndexId = this.radiance$patchIndexBufferId;
        int patchIndexCount = this.radiance$patchIndexCount;
        this.vertexBufferId = -1;
        this.indexBufferId = -1;
        this.arrayObjectId = -1;
        this.radiance$patchIndexBufferId = -1;
        this.radiance$patchIndexCount = 0;
        BufferProxy.releasePersistentPair(
            new BufferProxy.VertexIndexBufferHandle(vertexId, indexId, patchIndexId,
                patchIndexCount));
        ci.cancel();
    }
}
