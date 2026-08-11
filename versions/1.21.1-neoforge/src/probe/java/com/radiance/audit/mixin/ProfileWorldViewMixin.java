package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.api.render.WorldView;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WorldView.class, remap = false)
public abstract class ProfileWorldViewMixin {
    @WrapMethod(method = "submit()V")
    private void audit$world(Operation<Void> original) {
        try (var span = FrameProfiler.span(Stage.WORLD_UNIFORM)) {
            original.call();
        }
    }
}
