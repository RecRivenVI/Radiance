package com.radiance.mixins.vulkan_render_integration;

import com.radiance.compatibility.vanilla.VanillaModelPose;
import java.util.List;
import net.minecraft.client.model.PiglinHeadModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.*;

@Mixin(PiglinHeadModel.class)
public abstract class PiglinHeadRootPoseMixins implements VanillaModelPose.DefinedRoots {
    @Shadow @Final private ModelPart head;
    @Unique private List<VanillaModelPose.Group> radiance$roots;

    public List<VanillaModelPose.Group> radiance$rootGroups(boolean young) {
        if (radiance$roots == null) radiance$roots = List.of(VanillaModelPose.Group.ordinary(head));
        return radiance$roots;
    }
}
