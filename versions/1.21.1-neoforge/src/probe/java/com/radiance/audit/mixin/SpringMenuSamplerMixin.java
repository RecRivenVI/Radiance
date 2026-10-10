package com.radiance.audit.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.audit.SpringMenuProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opt-in test lightmap seen by both Mojang/Veil GL and Radiance named sampler capture. */
@Mixin(RenderSystem.class)
public abstract class SpringMenuSamplerMixin {
    @Inject(method = "getShaderTexture", at = @At("HEAD"), cancellable = true)
    private static void audit$springMenuLightmap(int slot, CallbackInfoReturnable<Integer> ci) {
        int selected = SpringMenuProbe.lightmap(slot);
        if (selected >= 0) ci.setReturnValue(selected);
    }
}
