package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.client.render.WorldMeshSink;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WorldMeshSink.FrameToken.class, remap = false)
public abstract class ProfileWorldMeshFrameMixin {
    @WrapMethod(method = "close")
    private void audit$close(Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.WORLD_MESH_CLOSE)) { original.call(); }
    }
}
