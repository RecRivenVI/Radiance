package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelLightEngine.class)
public abstract class ProfileLightEngineMixin {
    @WrapMethod(method = "runLightUpdates")
    private int audit$run(Operation<Integer> original) {
        // span() ignores logical-server and worker calls, even on a physical client.
        try (var ignored = FrameProfiler.span(Stage.LIGHT_UPDATES)) { return original.call(); }
    }
}
