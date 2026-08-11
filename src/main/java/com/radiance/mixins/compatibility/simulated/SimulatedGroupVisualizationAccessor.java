package com.radiance.mixins.compatibility.simulated;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
public interface SimulatedGroupVisualizationAccessor {
    @Accessor("sable$drawingDiagram")
    static boolean radiance$isDrawingDiagram() { throw new AssertionError(); }
}
