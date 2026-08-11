package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.render.RasterPreviewScope;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixins {

    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer radiance$captureDebugHitbox(MultiBufferSource source,
        RenderType renderType) {
        return EntityProxy.captureDebugLineConsumer(source, renderType);
    }

    @Inject(method = "renderShadow",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void cancelRenderShadow(PoseStack matrices,
        MultiBufferSource vertexConsumers,
        Entity entity,
        float opacity,
        float tickDelta,
        LevelReader world,
        float radius,
        CallbackInfo ci) {
        // PT supplies physical shadows; a raster preview still needs the producer's
        // ordinary shadow draw, including its original depth/blend state.
        if (!RasterPreviewScope.active()) ci.cancel();
    }
}
