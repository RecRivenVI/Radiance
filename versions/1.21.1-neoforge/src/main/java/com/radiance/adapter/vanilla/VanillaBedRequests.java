package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.client.vertex.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;

public final class VanillaBedRequests {
    private VanillaBedRequests() {}

    public static void add(
            ModelPart root,
            Direction facing,
            boolean foot,
            Material material,
            PoseStack pose,
            StorageVertexConsumerProvider buffers,
            BoundPartCapture requests,
            int light,
            int overlay) {
        pose.pushPose();
        pose.translate(0, .5625F, foot ? -1 : 0);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.translate(.5F, .5F, .5F);
        pose.mulPose(Axis.ZP.rotationDegrees(180 + facing.toYRot()));
        pose.translate(-.5F, -.5F, -.5F);
        VanillaBlockReplacement.add(
                VanillaModelPose.extract(root, pose, -1),
                material,
                RenderType::entitySolid,
                buffers,
                requests,
                light,
                overlay);
        pose.popPose();
    }
}
