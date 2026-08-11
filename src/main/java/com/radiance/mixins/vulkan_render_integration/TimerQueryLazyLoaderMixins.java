package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.TimerQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mojang.blaze3d.systems.TimerQuery$TimerQueryLazyLoader")
public class TimerQueryLazyLoaderMixins {

    @Inject(method = "instantiate()Lcom/mojang/blaze3d/systems/TimerQuery;", at = @At("HEAD"), cancellable = true)
    private static void createVulkanTimerQuery(CallbackInfoReturnable<TimerQuery> cir) {
        cir.setReturnValue(new TimerQuery());
    }
}
