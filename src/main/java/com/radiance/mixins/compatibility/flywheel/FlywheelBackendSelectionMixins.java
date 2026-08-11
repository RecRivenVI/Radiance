package com.radiance.mixins.compatibility.flywheel;

import com.radiance.compatibility.flywheel.RadianceFlywheelBackend;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.impl.FlwConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.BackendManagerImpl", remap = false)
public abstract class FlywheelBackendSelectionMixins {
    @Redirect(method = "chooseBackend", at = @At(value = "INVOKE",
        target = "Ldev/engine_room/flywheel/impl/FlwConfig;backend()Ldev/engine_room/flywheel/api/backend/Backend;"),
        remap = false)
    private static Backend radiance$resolveVulkanPreference(FlwConfig config) {
        return RadianceFlywheelBackend.resolvePreference(config.backend());
    }
}
