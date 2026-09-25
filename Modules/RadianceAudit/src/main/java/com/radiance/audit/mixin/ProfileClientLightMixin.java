package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientLevel.class)
public abstract class ProfileClientLightMixin {
    @WrapMethod(method = "pollLightUpdates")
    private void audit$poll(Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.LIGHT_UPDATES)) { original.call(); }
    }
}
