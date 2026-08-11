package com.radiance.audit.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BitmapProvider.Glyph.class)
public abstract class FontGlyphLifetimeMixin {
    @Shadow @Final private NativeImage image;

    @Inject(method = "bake", at = @At("HEAD"))
    private void radiance$baking(CallbackInfoReturnable<?> cir) {
        com.radiance.audit.FontSourceProbe.baking(image);
    }
}
