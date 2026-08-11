package com.radiance.mixins.compatibility.voxy;

import com.radiance.compatibility.voxy.VoxyCaptureLock;
import me.cortex.voxy.client.core.rendering.building.RenderDataFactory;
import me.cortex.voxy.common.world.WorldSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderDataFactory.class, remap = false)
public abstract class VoxyMesherMixins {
    @Redirect(
            method = {"generateMesh", "acquireNeighborData"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lme/cortex/voxy/common/world/WorldSection;_unsafeGetRawDataArray()[J"))
    private long[] radiance$immutableInput(WorldSection section) {
        return VoxyCaptureLock.copy(section._unsafeGetRawDataArray());
    }
}
