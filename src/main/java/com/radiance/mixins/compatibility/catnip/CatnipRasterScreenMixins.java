package com.radiance.mixins.compatibility.catnip;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.client.render.RasterPreviewScope;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Keep Ponder and configuration previews on their original raster/shading path. */
@Pseudo
@Mixin(targets = "net.createmod.catnip.gui.AbstractSimiScreen", remap = false)
public abstract class CatnipRasterScreenMixins {
    @WrapMethod(method = "render", remap = true)
    private void radiance$rasterPreview(GuiGraphics graphics, int x, int y, float tick,
        Operation<Void> original) {
        try (var ignored = RasterPreviewScope.enter()) {
            original.call(graphics, x, y, tick);
        }
    }
}
