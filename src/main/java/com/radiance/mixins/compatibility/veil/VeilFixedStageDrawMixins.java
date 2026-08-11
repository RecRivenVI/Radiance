package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilStageAdapter;
import foundry.veil.forge.impl.ForgeRenderTypeStageHandler;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "foundry.veil.forge.impl.ForgeRenderTypeStageHandler", remap = false)
public class VeilFixedStageDrawMixins {
    @Inject(method = "onRenderLevelStageEnd", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$drawAfterWorldFusion(RenderLevelStageEvent event, CallbackInfo ci) {
        if (VeilStageAdapter.deferFixedStage(event,
            () -> ForgeRenderTypeStageHandler.onRenderLevelStageEnd(event))) {
            ci.cancel();
        }
    }
}
