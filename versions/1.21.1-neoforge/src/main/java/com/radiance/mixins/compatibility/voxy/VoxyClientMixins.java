package com.radiance.mixins.compatibility.voxy;

import com.radiance.compatibility.voxy.VoxyPtBridge;
import me.cortex.voxy.client.VoxyClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VoxyClient.class, remap = false)
public abstract class VoxyClientMixins {
    @Inject(method = "initVoxyClient", at = @At("HEAD"), cancellable = true)
    private static void radiance$initializePt(CallbackInfo ci) {
        VoxyPtBridge.initialize();
        ci.cancel();
    }
}
