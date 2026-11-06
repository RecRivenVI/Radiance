package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientLevel.class)
public abstract class ProfileClientLightMixin {
    @Shadow @Final private java.util.Deque<Runnable> lightUpdateQueue;

    @WrapMethod(method = "pollLightUpdates")
    private void audit$poll(Operation<Void> original) {
        int before = lightUpdateQueue.size();
        try (var ignored = FrameProfiler.span(Stage.LIGHT_QUEUE)) {
            original.call();
        } finally {
            FrameProfiler.lightQueue(before, lightUpdateQueue.size());
        }
    }
}
