package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.api.render.WorldView;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = WorldView.class, remap = false)
public abstract class ProfileWorldViewMixin {
    @Shadow @Final private Matrix4f projectionMatrix;

    @WrapMethod(method = "submit()V")
    private void audit$world(Operation<Void> original) {
        if (FrameProfiler.collecting()) FrameProfiler.worldProjection(projectionMatrix.m11());
        try (var span = FrameProfiler.span(Stage.WORLD_UNIFORM)) {
            original.call();
        }
    }
}
