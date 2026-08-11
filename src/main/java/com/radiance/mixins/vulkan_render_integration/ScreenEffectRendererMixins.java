package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.render.HdrCameraEffectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves recognized camera overlays out of Vulkan UI and into the HDR world composite. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixins {
    // Veil injects at renderTex HEAD. Capture only when execution reaches vanilla's body, so an
    // actual Veil call gets the first chance to supply and draw its replacement semantics.
    @Inject(method = "renderTex", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/Tesselator;getInstance()Lcom/mojang/blaze3d/vertex/Tesselator;"),
        cancellable = true)
    private static void radiance$captureBlock(TextureAtlasSprite texture, PoseStack poseStack,
        CallbackInfo ci) {
        if (HdrCameraEffectRenderer.captureBlock(texture)) ci.cancel();
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private static void radiance$captureFluid(Minecraft minecraft, PoseStack poseStack,
        ResourceLocation texture, CallbackInfo ci) {
        if (HdrCameraEffectRenderer.captureFluid(minecraft, texture)) ci.cancel();
    }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void radiance$captureFire(Minecraft minecraft, PoseStack poseStack,
        CallbackInfo ci) {
        if (HdrCameraEffectRenderer.captureFire(ModelBakery.FIRE_1.sprite())) ci.cancel();
    }
}
