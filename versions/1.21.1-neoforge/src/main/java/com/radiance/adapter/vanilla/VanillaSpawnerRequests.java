package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;

public final class VanillaSpawnerRequests {
    private VanillaSpawnerRequests() {}

    public static void add(
            Entity entity,
            double previous,
            double current,
            float partial,
            PoseStack pose,
            StorageVertexConsumerProvider buffers,
            int light) {
        if (entity == null) return;
        pose.pushPose();
        pose.translate(.5F, 0, .5F);
        float scale = .53125F;
        float size = Math.max(entity.getBbWidth(), entity.getBbHeight());
        if (size > 1) scale /= size;
        pose.translate(0, .4F, 0);
        pose.mulPose(
                Axis.YP.rotationDegrees(
                        (float) Mth.lerp((double) partial, previous, current) * 10));
        pose.translate(0, -.2F, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-30));
        pose.scale(scale, scale, scale);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (entity instanceof LivingEntity living
                && VanillaLivingReplacement.eligible(dispatcher, entity, buffers, buffers))
            VanillaLivingReplacement.render(dispatcher, living, partial, pose, buffers, light);
        else if (VanillaEntityReplacement.eligible(dispatcher, entity, buffers, buffers))
            VanillaEntityReplacement.render(
                    dispatcher, entity, 0, partial, pose, buffers, buffers, light);
        else dispatcher.render(entity, 0, 0, 0, 0, partial, pose, buffers, light);
        pose.popPose();
    }
}
