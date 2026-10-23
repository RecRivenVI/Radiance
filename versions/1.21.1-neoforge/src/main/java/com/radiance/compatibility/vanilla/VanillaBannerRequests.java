package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.BoundPartCapture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/** Preserve the accepted canvas layer depth while declaring each original material explicitly. */
public final class VanillaBannerRequests {
    private VanillaBannerRequests() {}

    public static void patterns(
            ModelPart canvas,
            Material base,
            boolean banner,
            DyeColor color,
            BannerPatternLayers patterns,
            PoseStack pose,
            MultiBufferSource buffers,
            BoundPartCapture requests,
            int light,
            int overlay,
            boolean glint) {
        if (!banner) {
            var type = base.renderType(RenderType::entitySolid);
            requests.add(
                    VanillaModelPose.extract(canvas, pose, -1),
                    type,
                    base.buffer(buffers, RenderType::entitySolid, glint),
                    light,
                    overlay);
        }
        layer(
                canvas,
                banner ? Sheets.BANNER_BASE : Sheets.SHIELD_BASE,
                color,
                1,
                pose,
                buffers,
                requests,
                light,
                overlay);
        for (int i = 0; i < 16 && i < patterns.layers().size(); i++) {
            var pattern = patterns.layers().get(i);
            layer(
                    canvas,
                    banner
                            ? Sheets.getBannerMaterial(pattern.pattern())
                            : Sheets.getShieldMaterial(pattern.pattern()),
                    pattern.color(),
                    i + 2,
                    pose,
                    buffers,
                    requests,
                    light,
                    overlay);
        }
    }

    private static void layer(
            ModelPart canvas,
            Material material,
            DyeColor color,
            int index,
            PoseStack pose,
            MultiBufferSource buffers,
            BoundPartCapture requests,
            int light,
            int overlay) {
        pose.pushPose();
        var offset = new org.joml.Vector3f(0, 0, -.002F * index);
        new org.joml.Quaternionf()
                .rotationZYX(canvas.zRot, canvas.yRot, canvas.xRot)
                .transform(offset);
        pose.translate(offset.x, offset.y, offset.z);
        var type = material.renderType(RenderType::entityNoOutline);
        requests.add(
                VanillaModelPose.extract(canvas, pose, color.getTextureDiffuseColor()),
                type,
                material.buffer(buffers, RenderType::entityNoOutline),
                light,
                overlay);
        pose.popPose();
    }
}
