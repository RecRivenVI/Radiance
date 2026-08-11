package com.radiance.mixins.compatibility.simulated;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DiagramScreen.class, remap = false)
public interface SimulatedDiagramScreenAccessor {

    @Accessor("subLevel")
    ClientSubLevel radiance$getSubLevel();

    @Accessor("config")
    DiagramConfig radiance$getConfig();
}
