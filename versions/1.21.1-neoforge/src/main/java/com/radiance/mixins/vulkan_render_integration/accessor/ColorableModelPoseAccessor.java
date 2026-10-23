package com.radiance.mixins.vulkan_render_integration.accessor;

import com.radiance.compatibility.vanilla.VanillaModelPose;
import net.minecraft.client.model.ColorableAgeableListModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({
    ColorableAgeableListModel.class,
    net.minecraft.client.model.ColorableHierarchicalModel.class
})
public interface ColorableModelPoseAccessor extends VanillaModelPose.ColorAccess {
    @Accessor("color")
    int radiance$modelColor();
}
