package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.audit.SpringMenuProbe;
import com.radiance.client.proxy.vulkan.BufferProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retains the exact built spring MeshData before its Vulkan upload. */
@Mixin(value = BufferProxy.class, remap = false)
public abstract class SpringMenuMeshMixin {
    @Inject(method = "createAndUploadVertexIndexBuffer", at = @At("HEAD"), remap = false)
    private static void audit$springMesh(MeshData mesh,
        CallbackInfoReturnable<BufferProxy.VertexIndexBufferHandle> cir) {
        int ordinal = SpringMenuProbe.drawOrdinal();
        if (ordinal >= 0) SpringMenuProbe.retainMesh(mesh, ordinal);
    }
}
