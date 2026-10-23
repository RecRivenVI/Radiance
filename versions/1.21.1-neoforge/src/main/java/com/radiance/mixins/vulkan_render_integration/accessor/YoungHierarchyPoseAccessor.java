package com.radiance.mixins.vulkan_render_integration.accessor;

import com.radiance.compatibility.vanilla.VanillaModelPose;
import net.minecraft.client.model.AgeableHierarchicalModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AgeableHierarchicalModel.class)
public abstract class YoungHierarchyPoseAccessor implements VanillaModelPose.YoungRootAccess {
    @Shadow @Final private float youngScaleFactor;
    @Shadow @Final private float bodyYOffset;

    public float radiance$youngScale() {
        return youngScaleFactor;
    }

    public float radiance$youngYOffset() {
        return bodyYOffset / 16;
    }
}
