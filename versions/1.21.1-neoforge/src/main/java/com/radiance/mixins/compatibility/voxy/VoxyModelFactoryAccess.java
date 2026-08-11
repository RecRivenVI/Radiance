package com.radiance.mixins.compatibility.voxy;

import java.util.concurrent.ConcurrentLinkedDeque;
import me.cortex.voxy.client.core.model.ModelFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ModelFactory.class, remap = false)
public interface VoxyModelFactoryAccess {
    @Accessor("uploadResults")
    ConcurrentLinkedDeque<?> radiance$uploads();
}
