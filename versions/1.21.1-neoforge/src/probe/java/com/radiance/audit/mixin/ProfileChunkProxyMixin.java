package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.client.proxy.world.ChunkProxy;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ChunkProxy.class, remap = false)
public abstract class ProfileChunkProxyMixin {
    @WrapMethod(method = "rebuild")
    private static void audit$rebuild(Camera camera, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.CHUNK_SCHEDULE)) {
            original.call(camera);
        }
    }
}
