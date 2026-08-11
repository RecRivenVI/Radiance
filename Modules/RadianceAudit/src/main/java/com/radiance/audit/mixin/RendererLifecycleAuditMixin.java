package com.radiance.audit.mixin;

import com.radiance.audit.LifecycleAcceptance;
import com.radiance.client.proxy.vulkan.RendererProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the real shutdown path; never replaces it or suppresses its failures. */
@Mixin(value = RendererProxy.class, remap = false)
public abstract class RendererLifecycleAuditMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private static void beforeClose(CallbackInfo ci) { LifecycleAcceptance.beforeRendererClose(); }
    @Inject(method = "close", at = @At("RETURN"))
    private static void afterClose(CallbackInfo ci) { LifecycleAcceptance.afterRendererClose(); }
}
