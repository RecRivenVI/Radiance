package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilPatchState;
import foundry.veil.impl.client.render.pipeline.PatchStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = PatchStateShard.class, remap = false)
public abstract class VeilPatchStateShardMixins {

    @Inject(method = "lambda$new$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$setPatchVertices(int vertices, CallbackInfo ci) {
        VeilPatchState.set(vertices);
        ci.cancel();
    }

    @Inject(method = "lambda$new$1", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$clearPatchVertices(CallbackInfo ci) {
        VeilPatchState.clear();
        ci.cancel();
    }
}
