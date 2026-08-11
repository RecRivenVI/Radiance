package com.radiance.mixins.compatibility.veil;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "foundry.veil.api.client.render.ext.VeilDebug", remap = false)
public abstract class VeilDebugMixins {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$disableOpenGlDebugLabels(CallbackInfoReturnable<Object> cir) {
        try {
            Class<?> veilDebug = Class.forName(
                "foundry.veil.api.client.render.ext.VeilDebug",
                false,
                Thread.currentThread().getContextClassLoader()
            );
            cir.setReturnValue(veilDebug.getField("DISABLED").get(null));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to disable Veil OpenGL debug labels", exception);
        }
    }
}
