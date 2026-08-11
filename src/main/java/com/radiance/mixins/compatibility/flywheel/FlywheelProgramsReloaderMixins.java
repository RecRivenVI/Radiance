package com.radiance.mixins.compatibility.flywheel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.backend.compile.FlwProgramsReloader",
    remap = false)
public abstract class FlywheelProgramsReloaderMixins {

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"), cancellable = true,
        remap = false)
    private void radiance$skipOpenGlReload(CallbackInfo ci) {
        ci.cancel();
    }
}
