package com.radiance.mixins.vulkan_render_integration.accessor;

import com.radiance.compatibility.vanilla.VanillaModelPose;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AgeableListModel.class)
public interface AgeableModelPoseAccessor extends VanillaModelPose.AgeableAccess {
    @Invoker("headParts")
    Iterable<ModelPart> radiance$headParts();

    @Invoker("bodyParts")
    Iterable<ModelPart> radiance$bodyParts();

    @Accessor("scaleHead")
    boolean radiance$scaleHead();

    @Accessor("babyYHeadOffset")
    float radiance$babyYHeadOffset();

    @Accessor("babyZHeadOffset")
    float radiance$babyZHeadOffset();

    @Accessor("babyHeadScale")
    float radiance$babyHeadScale();

    @Accessor("babyBodyScale")
    float radiance$babyBodyScale();

    @Accessor("bodyYOffset")
    float radiance$bodyYOffset();
}
