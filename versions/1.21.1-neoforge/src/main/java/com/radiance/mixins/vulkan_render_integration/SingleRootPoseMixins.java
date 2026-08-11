package com.radiance.mixins.vulkan_render_integration;

import com.radiance.adapter.vanilla.VanillaModelPose;
import java.util.List;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.*;

@Mixin({BookModel.class, ShieldModel.class, SkullModel.class, TridentModel.class})
public abstract class SingleRootPoseMixins implements VanillaModelPose.DefinedRoots {
    @Shadow(remap = false)
    @Final
    private ModelPart root;

    @Unique private List<VanillaModelPose.Group> radiance$roots;

    public List<VanillaModelPose.Group> radiance$rootGroups(boolean young) {
        if (radiance$roots == null) radiance$roots = List.of(VanillaModelPose.Group.ordinary(root));
        return radiance$roots;
    }
}
