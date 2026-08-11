package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.radiance.client.render.ScreenEffectCoordinator;
import com.radiance.render.contract.DrawTargetContract;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Screen.class)
public class ScreenMixins {

    @WrapMethod(method = "renderTransparentBackground(Lnet/minecraft/client/gui/GuiGraphics;)V")
    private void radiance$transparentBackground(GuiGraphics graphics, Operation<Void> original) {
        ScreenEffectCoordinator.guiBackground(
                graphics,
                DrawTargetContract.GuiRole.BACKGROUND_EFFECT,
                () -> original.call(graphics));
    }

    @WrapMethod(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V")
    private void radiance$menuBackground(GuiGraphics graphics, Operation<Void> original) {
        ScreenEffectCoordinator.guiBackground(
                graphics,
                DrawTargetContract.GuiRole.BACKGROUND_EFFECT,
                () -> original.call(graphics));
    }

    @WrapMethod(method = "renderPanorama(Lnet/minecraft/client/gui/GuiGraphics;F)V")
    private void radiance$panorama(
            GuiGraphics graphics, float partialTick, Operation<Void> original) {
        ScreenEffectCoordinator.guiBackground(
                graphics,
                DrawTargetContract.GuiRole.WORLD_REPLACEMENT,
                () -> original.call(graphics, partialTick));
    }

    @Redirect(
            method = "renderBlurredBackground(F)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void cancelFrameBufferInApplyBlur(RenderTarget instance, boolean setViewport) {}
}
