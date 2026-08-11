package com.radiance.mixins.vulkan_render_integration;

import com.radiance.adapter.vanilla.VanillaModelPose;
import net.minecraft.client.model.CamelModel;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CamelModel.class)
public abstract class CamelRootPoseMixins implements VanillaModelPose.YoungRootAccess {
    public float radiance$youngScale() {
        return .45F;
    }

    public float radiance$youngYOffset() {
        return 1.834375F;
    }
}
