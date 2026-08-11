package com.radiance.audit.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BitmapProvider.class)
public abstract class FontProviderLifetimeMixin {
    @Shadow @Final private NativeImage image;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void radiance$register(CallbackInfo ci) {
        com.radiance.audit.FontSourceProbe.register(image);
    }
}
