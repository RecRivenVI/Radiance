package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.BoundPartCapture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        var parts = VanillaModelPose.extract(canvas, pose, -1);
        if (parts.isEmpty()) return;
        if (!banner) {
            var type = base.renderType(RenderType::entitySolid);
            requests.add(
                    parts,
                    type,
                    base.buffer(buffers, RenderType::entitySolid, glint),
                    light,
                    overlay);
        }
        var layerOffset =
                new Quaternionf()
                        .rotationZYX(canvas.zRot, canvas.yRot, canvas.xRot)
                        .transform(new Vector3f(0, 0, -.002F));
        pose.last().pose().transformDirection(layerOffset);
        layer(
                parts,
                banner ? Sheets.BANNER_BASE : Sheets.SHIELD_BASE,
                color,
                1,
                layerOffset,
                buffers,
                requests,
                light,
                overlay);
        for (int i = 0; i < 16 && i < patterns.layers().size(); i++) {
            var pattern = patterns.layers().get(i);
            layer(
                    parts,
                    banner
                            ? Sheets.getBannerMaterial(pattern.pattern())
                            : Sheets.getShieldMaterial(pattern.pattern()),
                    pattern.color(),
                    i + 2,
                    layerOffset,
                    buffers,
                    requests,
                    light,
                    overlay);
        }
    }

    private static void layer(
            List<VanillaModelPose.Part> parts,
            Material material,
            DyeColor color,
            int index,
            Vector3f layerOffset,
            MultiBufferSource buffers,
            BoundPartCapture requests,
            int light,
            int overlay) {
        // Each layer changes only the tint and the translation of the same posed canvas.
        int tint = color.getTextureDiffuseColor();
        var layerParts = new ArrayList<VanillaModelPose.Part>(parts.size());
        for (var part : parts) {
            var shifted = part.pose().copy();
            var matrix = shifted.pose();
            matrix.m30(matrix.m30() + layerOffset.x * index);
            matrix.m31(matrix.m31() + layerOffset.y * index);
            matrix.m32(matrix.m32() + layerOffset.z * index);
            layerParts.add(new VanillaModelPose.Part(part.part(), shifted, tint));
        }
        var type = material.renderType(RenderType::entityNoOutline);
        requests.add(
                layerParts,
                type,
                material.buffer(buffers, RenderType::entityNoOutline),
                light,
                overlay);
    }
}
