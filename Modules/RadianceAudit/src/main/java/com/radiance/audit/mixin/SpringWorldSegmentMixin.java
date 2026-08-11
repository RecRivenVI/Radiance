package com.radiance.audit.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.audit.SpringWorldProbe;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes calls to the pinned original spline segment producer in the main world. */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.spring.SpringRenderer", remap = false)
public abstract class SpringWorldSegmentMixin {
    @Inject(method = "renderSegment", at = @At("HEAD"), remap = false)
    private void audit$springSegment(PoseStack pose, Vector3dc startDirection,
        Vector3dc endDirection, Vector3dc startUp, Vector3dc endUp,
        Vector3dc start, Vector3dc end, boolean second, float uvStart, float uvEnd,
        int light, int color, VertexConsumer consumer, float width, float textureWidth,
        CallbackInfo ci) {
        SpringWorldProbe.segment();
    }
}
