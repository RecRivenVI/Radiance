package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.BoundPartCapture;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.renderer.*;
import net.minecraft.core.Direction;

/** Shared skull definition/animation contract for world blocks and attached head items. */
public final class VanillaSkullRequests {
    private VanillaSkullRequests() {}

    public static void add(
            Direction wall,
            float yaw,
            float animation,
            PoseStack pose,
            MultiBufferSource buffers,
            BoundPartCapture requests,
            int light,
            SkullModelBase model,
            RenderType type) {
        pose.pushPose();
        if (wall == null) pose.translate(.5F, 0, .5F);
        else pose.translate(.5F - wall.getStepX() * .25F, .25F, .5F - wall.getStepZ() * .25F);
        pose.scale(-1, -1, 1);
        model.setupAnim(animation, yaw, 0);
        requests.add(
                VanillaModelPose.extract(model, pose, -1),
                type,
                buffers.getBuffer(type),
                light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
