package com.radiance.mixins.vulkan_render_integration;

import com.radiance.compatibility.vanilla.VanillaModelPose;
import net.minecraft.client.model.TurtleModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TurtleModel.class)
public abstract class TurtleRootPoseMixins implements VanillaModelPose.RootOffsetAccess {
    @Shadow @Final private ModelPart eggBelly;

    public float radiance$rootYOffset() {
        return eggBelly.visible ? -.08F : 0;
    }
}
