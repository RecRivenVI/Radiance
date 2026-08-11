package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.client.render.ScreenEffectCoordinator;
import com.radiance.render.contract.DrawTargetContract;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DeathScreen.class)
public class DeathBackgroundMixins {
    @WrapMethod(method = "renderDeathBackground(Lnet/minecraft/client/gui/GuiGraphics;II)V")
    private static void radiance$deathBackground(
            GuiGraphics graphics, int width, int height, Operation<Void> original) {
        ScreenEffectCoordinator.guiBackground(
                graphics,
                DrawTargetContract.GuiRole.BACKGROUND_EFFECT,
                () -> original.call(graphics, width, height));
    }
}
