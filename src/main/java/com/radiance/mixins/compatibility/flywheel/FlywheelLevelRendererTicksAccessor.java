package com.radiance.mixins.compatibility.flywheel;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Flywheel's frame uniforms animate instances with this per-level client tick counter. */
@Mixin(LevelRenderer.class)
public interface FlywheelLevelRendererTicksAccessor {
    @Accessor("ticks")
    int radiance$getTicks();
}
