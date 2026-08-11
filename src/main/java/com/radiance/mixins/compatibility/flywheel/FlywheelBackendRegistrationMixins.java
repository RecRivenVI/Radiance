package com.radiance.mixins.compatibility.flywheel;

import com.radiance.compatibility.flywheel.RadianceFlywheelBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.FlwImpl", remap = false)
public abstract class FlywheelBackendRegistrationMixins {
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private static void radiance$registerVulkanBackendBeforeRegistryFreeze(CallbackInfo ci) {
        RadianceFlywheelBackend.register();
    }
}
