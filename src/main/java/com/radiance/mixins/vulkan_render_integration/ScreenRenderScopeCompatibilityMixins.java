package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.client.render.RenderCaptureContract;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;

/** Marks NeoForge's screen render wrapper as an explicit Vulkan UI scope. */
@Mixin(Screen.class)
public abstract class ScreenRenderScopeCompatibilityMixins {

    @WrapMethod(method = "renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
    private void radiance$renderWithScreenScope(GuiGraphics graphics, int mouseX, int mouseY,
        float partialTick, Operation<Void> original) {
        try (RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.GUI, "Screen.renderWithTooltip")) {
            original.call(graphics, mouseX, mouseY, partialTick);
        }
    }
}
