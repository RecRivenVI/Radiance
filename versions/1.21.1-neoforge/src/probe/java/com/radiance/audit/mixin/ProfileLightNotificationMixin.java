package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientChunkCache.class)
public abstract class ProfileLightNotificationMixin {
    @WrapMethod(method = "onLightUpdate")
    private void audit$notify(LightLayer layer, SectionPos section, Operation<Void> original) {
        try (var ignored = FrameProfiler.span(Stage.LIGHT_SECTION_NOTIFY)) {
            FrameProfiler.lightWork(Stage.LIGHT_SECTION_NOTIFY, 1);
            original.call(layer, section);
        }
    }
}
