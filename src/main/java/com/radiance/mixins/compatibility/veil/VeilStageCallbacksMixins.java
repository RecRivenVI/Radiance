package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilStageAdapter;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Preserves the registered consumers, with their main-world output recorded after PT fusion. */
@Pseudo
@Mixin(targets = "foundry.veil.forge.platform.NeoForgeVeilEventPlatform", remap = false)
public class VeilStageCallbacksMixins {
    @ModifyVariable(method = "onVeilRenderLevelStage", at = @At("HEAD"), argsOnly = true, remap = false)
    private VeilRenderLevelStageEvent radiance$deferWorldConsumer(VeilRenderLevelStageEvent listener) {
        return VeilStageAdapter.wrap(listener);
    }
}
