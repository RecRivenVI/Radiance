package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.render.DebugEmissionScope;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkBorderRenderer.class)
public abstract class ChunkBorderEmissionMixins {
    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;DDD)V")
    private void radiance$markChunkBorders(PoseStack pose, MultiBufferSource buffers,
        double x, double y, double z, Operation<Void> original) {
        DebugEmissionScope.withChunkBorders(() -> original.call(pose, buffers, x, y, z));
    }
}
