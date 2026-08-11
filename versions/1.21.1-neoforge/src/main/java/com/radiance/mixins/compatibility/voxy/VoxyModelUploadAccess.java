package com.radiance.mixins.compatibility.voxy;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(
        targets = "me.cortex.voxy.client.core.model.ModelFactory$ModelBakeResultUpload",
        remap = false)
public interface VoxyModelUploadAccess {
    @Accessor("model")
    MemoryBuffer radiance$model();

    @Accessor("texture")
    MemoryBuffer radiance$texture();

    @Accessor("hasMips")
    boolean radiance$hasMips();

    @Accessor("modelId")
    int radiance$id();

    @Accessor("biomeUploadIndex")
    int radiance$biomeIndex();

    @Accessor("biomeUpload")
    MemoryBuffer radiance$biome();

    @Invoker("free")
    void radiance$free();
}
