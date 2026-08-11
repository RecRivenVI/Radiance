package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.texture.FontAtlasLifecycle;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class FontBatchBufferSourceMixins implements FontAtlasLifecycle.BatchOwner {
    @Shadow @Final protected Map<RenderType, BufferBuilder> startedBuilders;
    @Shadow protected RenderType lastSharedType;
    @Unique private final Map<RenderType, AbstractTexture> radiance$fontPages = new HashMap<>();

    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void radiance$captureFontPage(
            RenderType layer, CallbackInfoReturnable<VertexConsumer> cir) {
        if (startedBuilders.containsKey(layer))
            FontAtlasLifecycle.capture(this, radiance$fontPages, layer);
    }

    @Inject(
            method =
                    "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V",
            at = @At("RETURN"))
    private void radiance$releaseFontPage(
            RenderType layer, BufferBuilder builder, CallbackInfo ci) {
        FontAtlasLifecycle.release(this, radiance$fontPages, layer);
    }

    @Override
    public void radiance$discardFontBatches(Set<AbstractTexture> pages) {
        lastSharedType =
                FontAtlasLifecycle.discardPending(
                        this, startedBuilders, radiance$fontPages, lastSharedType, pages);
    }
}
