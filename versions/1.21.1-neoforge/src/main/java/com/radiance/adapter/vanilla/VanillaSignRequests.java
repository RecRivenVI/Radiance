package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.Vec3;

/** A sign's cached original layout is an explicit world-text request, separate from its model. */
public final class VanillaSignRequests {
    private VanillaSignRequests() {}

    public static com.radiance.client.vertex.PBRMaterialContext.Scope material(
            net.minecraft.world.level.block.entity.SignText text) {
        return com.radiance.client.vertex.PBRMaterialContext.pushAlbedoEmission(
                text.hasGlowingText() && !com.radiance.client.render.RasterPreviewScope.active()
                        ? 1
                        : 0);
    }

    public interface Hanging {
        ModelPart radiance$signRoot(SignBlockEntity sign);
    }

    public static void text(
            SignBlockEntity sign,
            Font font,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            float scale,
            Vec3 offset) {
        for (boolean front : new boolean[] {true, false}) {
            var text = front ? sign.getFrontText() : sign.getBackText();
            pose.pushPose();
            if (!front) pose.mulPose(Axis.YP.rotationDegrees(180));
            float unit = .015625F * scale;
            pose.translate(offset.x, offset.y, offset.z);
            pose.scale(unit, -unit, unit);
            int dark = SignRenderer.getDarkColor(text), half = 4 * sign.getTextLineHeight() / 2;
            var messages =
                    text.getRenderMessages(
                            Minecraft.getInstance().isTextFilteringEnabled(),
                            component -> {
                                var lines = font.split(component, sign.getMaxTextLineWidth());
                                return lines.isEmpty()
                                        ? FormattedCharSequence.EMPTY
                                        : lines.getFirst();
                            });
            int color = text.hasGlowingText() ? text.getColor().getTextColor() : dark;
            boolean outline = text.hasGlowingText() && outline(sign.getBlockPos(), color);
            int brightness = text.hasGlowingText() ? 15728880 : light;
            try (var material = material(text)) {
                for (int i = 0; i < 4; i++) {
                    var message = messages[i];
                    float x = -font.width(message) / 2;
                    if (outline)
                        VanillaTextRequests.outline(
                                sign,
                                front,
                                i,
                                font,
                                message,
                                x,
                                i * sign.getTextLineHeight() - half,
                                color,
                                dark,
                                pose.last().pose(),
                                buffers,
                                brightness);
                    else
                        VanillaTextRequests.draw(
                                sign,
                                front,
                                i,
                                font,
                                message,
                                x,
                                i * sign.getTextLineHeight() - half,
                                color,
                                false,
                                pose.last().pose(),
                                buffers,
                                Font.DisplayMode.POLYGON_OFFSET,
                                0,
                                brightness);
                }
            }
            pose.popPose();
        }
    }

    private static boolean outline(BlockPos position, int color) {
        if (color == DyeColor.BLACK.getTextColor()) return true;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player != null && client.options.getCameraType().isFirstPerson() && player.isScoping())
            return true;
        var camera = client.getCameraEntity();
        return camera != null && camera.distanceToSqr(Vec3.atCenterOf(position)) < 256;
    }
}
