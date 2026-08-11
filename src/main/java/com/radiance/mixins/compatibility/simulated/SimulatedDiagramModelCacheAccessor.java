package com.radiance.mixins.compatibility.simulated;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelBlockRenderer.class)
public interface SimulatedDiagramModelCacheAccessor {
    @Accessor("CACHE")
    static ThreadLocal<?> radiance$cache() { throw new AssertionError(); }
}
