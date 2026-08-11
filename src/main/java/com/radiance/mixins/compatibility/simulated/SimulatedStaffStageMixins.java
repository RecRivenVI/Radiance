package com.radiance.mixins.compatibility.simulated;

import com.radiance.compatibility.veil.VeilAdapter;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Simulated 1.3.2's second registration delegates only to PhysicsStaffRenderHandler. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.SimulatedClient", remap = false)
public abstract class SimulatedStaffStageMixins {
    @ModifyArg(method = "init", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lfoundry/veil/platform/VeilEventPlatform;onVeilRenderLevelStage(Lfoundry/veil/api/event/VeilRenderLevelStageEvent;)V"),
        index = 0, require = 1, remap = false)
    private static VeilRenderLevelStageEvent radiance$captureStaff(VeilRenderLevelStageEvent original) {
        return VeilAdapter.pathTraceStaffStage(original);
    }
}
