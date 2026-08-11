package com.radiance.mixins.compatibility.voxy;

import com.radiance.compatibility.voxy.VoxyPtBridge;
import me.cortex.voxy.client.DebugEntries;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DebugEntries.class, remap = false)
public abstract class VoxyDebugEntriesMixins {
    @Inject(method = "updateTiming", at = @At("HEAD"), cancellable = true)
    private static void radiance$noGlTimer(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "append", at = @At("TAIL"))
    private static void radiance$ptStatus(
            CustomizeGuiOverlayEvent.DebugText event, CallbackInfo ci) {
        long[] counters = VoxyPtBridge.counters();
        if (counters.length >= 5)
            event.getLeft()
                    .add(
                            "Radiance Voxy PT: resident="
                                    + counters[0]
                                    + " selected="
                                    + counters[1]
                                    + " models="
                                    + counters[2]
                                    + " builds="
                                    + counters[4]);
    }
}
