package com.radiance.mixins.vulkan_render_integration;

import com.radiance.adapter.vanilla.VanillaModelPose;
import java.util.List;
import net.minecraft.client.model.dragon.DragonHeadModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.*;

@Mixin(DragonHeadModel.class)
public abstract class DragonHeadRootPoseMixins
        implements VanillaModelPose.DefinedRoots, VanillaModelPose.RootOffsetAccess {
    @Shadow @Final private ModelPart head;
    @Unique private List<VanillaModelPose.Group> radiance$roots;

    public float radiance$rootYOffset() {
        return -.374375F;
    }

    public List<VanillaModelPose.Group> radiance$rootGroups(boolean young) {
        if (radiance$roots == null)
            radiance$roots =
                    List.of(new VanillaModelPose.Group(List.of(head), .75F, .75F, .75F, 0, 0, 0));
        return radiance$roots;
    }
}
