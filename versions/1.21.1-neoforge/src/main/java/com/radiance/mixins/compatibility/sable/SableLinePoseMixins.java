package com.radiance.mixins.compatibility.sable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.CapturedLineVertexConsumer;
import com.radiance.client.vertex.LineSectionReference;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Preserve the line pose before Sable's default scalar forwarding discards it. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer", remap = false)
public abstract class SableLinePoseMixins implements VertexConsumer, LineSectionReference {
    @Shadow @Final private VertexConsumer delegate;

    @Override
    public VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
        if (delegate instanceof CapturedLineVertexConsumer) {
            delegate.addVertex(pose, x, y, z);
            return this;
        }
        return VertexConsumer.super.addVertex(pose, x, y, z);
    }

    @Override
    public void sectionReference(PoseStack.Pose pose, float x, float y, float z) {
        if (delegate instanceof LineSectionReference reference)
            reference.sectionReference(pose, x, y, z);
    }
}
