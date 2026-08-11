package com.radiance.audit.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public abstract class FontImageLifetimeMixin {
    @Inject(method = "close()V", at = @At("HEAD"))
    private void radiance$closing(CallbackInfo ci) {
        com.radiance.audit.FontSourceProbe.closing((NativeImage) (Object) this);
    }
}
