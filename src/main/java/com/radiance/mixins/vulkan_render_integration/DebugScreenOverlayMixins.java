package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixins {
    @ModifyExpressionValue(method = "getGameInformation", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/Minecraft;fpsString:Ljava/lang/String;"))
    private String radiance$replaceFpsNumber(String original) {
        if (!Options.dlssFrameGeneration) return original;
        long rates = RendererProxy.presentationRates();
        long output = (rates >>> 32) + (rates & 0xffffffffL);
        if (output == 0) return original;
        return original.replaceFirst("^\\d+(?= fps\\b)", Long.toString(output));
    }

    @ModifyExpressionValue(method = "getGameInformation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;getSectionStatistics()Ljava/lang/String;"))
    private String radiance$replaceSectionCounts(String original) {
        long counts = RendererProxy.sectionCounts();
        if (counts == -1) return original;
        return original.replaceFirst("^C: \\d+/\\d+",
            "C: " + (counts >>> 32) + "/" + (counts & 0xffffffffL));
    }
}
