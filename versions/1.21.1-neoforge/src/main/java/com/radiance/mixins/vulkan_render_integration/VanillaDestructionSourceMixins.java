package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.vertex.SourceBindings;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Progress objects own their definitions; collection of the object retires persistent submission.
 */
@Mixin(BlockDestructionProgress.class)
public abstract class VanillaDestructionSourceMixins implements SourceBindings.Owner {
    @Unique private SourceBindings radiance$bindings;

    public SourceBindings radiance$bindings() {
        if (radiance$bindings == null) radiance$bindings = new SourceBindings();
        return radiance$bindings;
    }
}
