package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import com.radiance.client.proxy.world.CloudProxy;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CloudProxy.class, remap = false)
public abstract class ProfileCloudMixin {
    @WrapMethod(method = "queue")
    private static void audit$queue(ClientLevel level, Camera camera, int ticks, float partialTick,
        Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.CLOUDS)) { original.call(level, camera, ticks, partialTick); }
    }
}
