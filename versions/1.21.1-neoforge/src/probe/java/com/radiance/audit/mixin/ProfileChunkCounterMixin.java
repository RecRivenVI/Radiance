package com.radiance.audit.mixin;

import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.audit.FlowProfileCounters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderAuditBridge.class, remap = false)
public abstract class ProfileChunkCounterMixin {
    @Inject(method = "counter", at = @At("HEAD"))
    private static void audit$count(
            String category, String event, long amount, String detail, CallbackInfo ci) {
        if ("CHUNK_BUILD".equals(category)) FlowProfileCounters.event(event, amount);
    }
}
