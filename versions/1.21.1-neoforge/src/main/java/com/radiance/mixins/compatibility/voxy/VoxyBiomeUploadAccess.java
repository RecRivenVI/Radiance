package com.radiance.mixins.compatibility.voxy;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "me.cortex.voxy.client.core.model.ModelFactory$BiomeUploadResult", remap = false)
public interface VoxyBiomeUploadAccess {
    @Accessor("biomeColourBuffer")
    MemoryBuffer radiance$colours();

    @Accessor("modelBiomeIndexPairs")
    MemoryBuffer radiance$pairs();

    @Invoker("free")
    void radiance$free();
}
