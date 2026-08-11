package com.radiance.mixins.compatibility.catnip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.compatibility.outliner.RadianceOutlinerRenderer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.createmod.catnip.outliner.Outliner", remap = false)
public abstract class OutlinerRenderMixins {

    @Inject(
        method = "renderOutlines(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/createmod/catnip/render/SuperRenderTypeBuffer;"
            + "Lnet/minecraft/world/phys/Vec3;F)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$renderOutlines(PoseStack poseStack, SuperRenderTypeBuffer buffer,
        Vec3 camera, float partialTick, CallbackInfo ci) {
        if (!RadianceOutlinerRenderer.isActive()) {
            return;
        }
        RadianceOutlinerRenderer.render(poseStack, buffer, camera, partialTick);
        ci.cancel();
    }
}
