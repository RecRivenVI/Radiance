package com.radiance.mixins.compatibility.veil;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.compatibility.veil.VeilStageAdapter;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "foundry.veil.forge.impl.ForgeRenderTypeStageHandler", remap = false)
public class VeilFixedStageDrawMixins {
    @WrapMethod(method = "onRenderLevelStageEnd", remap = false)
    private static void radiance$keepGeometryInProducerStage(
            RenderLevelStageEvent event, Operation<Void> original) {
        VeilStageAdapter.runFixedStage(() -> original.call(event));
    }
}
