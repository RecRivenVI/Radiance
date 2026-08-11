package com.radiance.mixins.compatibility.ponder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;

@Pseudo
@Mixin(targets = "net.createmod.catnip.gui.UIRenderHelper", remap = false)
public abstract class PonderUIRenderHelperMixins {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$skipOpenGlFramebuffer(CallbackInfo ci) {
        // RenderTarget storage is now backed by the Vulkan framebuffer bridge. Keep
        // Catnip's real target object alive for navigation and stencil transitions.
        com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() -> {
            if (UIRenderHelper.framebuffer == null) {
                UIRenderHelper.framebuffer = UIRenderHelper.CustomRenderTarget.create(
                    Minecraft.getInstance().getWindow());
            }
        });
        ci.cancel();
    }
}
