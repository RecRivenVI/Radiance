package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.render.BoatWaterExclusion;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BoatModel.class)
public abstract class BoatModelWaterExclusionMixins implements BoatWaterExclusion.FloorSource {
    @Unique private ModelPart radiance$waterFloor;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void radiance$rememberFloor(ModelPart root, CallbackInfo ci) {
        radiance$waterFloor = root.hasChild("bottom") ? root.getChild("bottom") : null;
    }

    public ModelPart radiance$waterFloor() {
        return radiance$waterFloor;
    }
}
