package com.radiance.mixins.compatibility.flywheel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.api.visualization.VisualizationManager",
    remap = false)
public interface FlywheelVisualizationManagerMixins {
    // Preserve Flywheel's actual backend/level availability, including transformed block-entity
    // capture. Its original visualizer decides whether vanilla BER parts must be rendered.
}
