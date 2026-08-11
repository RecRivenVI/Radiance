package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.client.render.RenderCaptureContract;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

/** Marks the complete vanilla HUD layer pass as an explicit Vulkan UI scope. */
@Mixin(Gui.class)
public abstract class GuiRenderScopeCompatibilityMixins {

    @WrapMethod(method = "render(Lnet/minecraft/client/gui/GuiGraphics;"
        + "Lnet/minecraft/client/DeltaTracker;)V")
    private void radiance$renderWithGuiScope(GuiGraphics graphics, DeltaTracker deltaTracker,
        Operation<Void> original) {
        try (RenderCaptureContract.ScopeToken ignored = RenderCaptureContract.enter(
            RenderCaptureContract.ScopeKind.GUI, "Gui.render")) {
            original.call(graphics, deltaTracker);
        }
    }
}
