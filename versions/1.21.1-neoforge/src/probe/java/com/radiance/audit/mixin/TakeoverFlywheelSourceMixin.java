package com.radiance.audit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.radiance.compatibility.flywheel.RadianceFlywheelEngine", remap = false)
public abstract class TakeoverFlywheelSourceMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void audit$source(CallbackInfo ci) {
        com.radiance.audit.TakeoverSourceProbe.observeFlywheel(this);
    }
}
