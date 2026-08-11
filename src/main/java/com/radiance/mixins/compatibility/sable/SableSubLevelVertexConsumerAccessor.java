package com.radiance.mixins.compatibility.sable;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer",
    remap = false)
public interface SableSubLevelVertexConsumerAccessor {

    @Accessor(value = "delegate", remap = false)
    VertexConsumer radiance$getDelegate();
}
