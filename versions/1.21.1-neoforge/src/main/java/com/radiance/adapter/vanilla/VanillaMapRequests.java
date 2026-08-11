package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.api.render.LocalSurface;
import com.radiance.client.vertex.LocalMeshTakeover;
import com.radiance.render.contract.DrawStateSnapshot.UvTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.MapDecorationTextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Map pixel uploads remain owned by MapInstance; image/marker quads share stable local templates.
 */
public final class VanillaMapRequests {
    private VanillaMapRequests() {}

    public static void image(
            PoseStack pose,
            MultiBufferSource buffers,
            RenderType layer,
            int light,
            float x0,
            float y0,
            float x1,
            float y1,
            float z,
            UvTransform uv) {
        pose.pushPose();
        try {
            pose.translate(x0, y0, z);
            pose.scale(x1 - x0, y1 - y0, 1);
            LocalMeshTakeover.template(
                    "vanilla-map-quad",
                    pose.last(),
                    buffers.getBuffer(layer),
                    LocalSurface.uniform(light, 0, -1, uv),
                    (p, out, l, c) -> {
                        out.addVertex(p.pose(), 0, 1, 0).setColor(c).setUv(0, 1).setLight(l);
                        out.addVertex(p.pose(), 1, 1, 0).setColor(c).setUv(1, 1).setLight(l);
                        out.addVertex(p.pose(), 1, 0, 0).setColor(c).setUv(1, 0).setLight(l);
                        out.addVertex(p.pose(), 0, 0, 0).setColor(c).setUv(0, 0).setLight(l);
                    });
        } finally {
            pose.popPose();
        }
    }

    public static void collect(
            PoseStack pose,
            MultiBufferSource buffers,
            boolean frame,
            int light,
            MapItemSavedData data,
            RenderType image,
            MapDecorationTextureManager textures) {
        image(pose, buffers, image, light, 0, 0, 128, 128, -.01F, UvTransform.IDENTITY);
        int ordinal = 0;
        for (var decoration : data.getDecorations()) {
            if (frame && !decoration.renderOnFrame()) continue;
            if (net.neoforged.neoforge.client.gui.map.MapDecorationRendererManager.render(
                    decoration, pose, buffers, data, textures, frame, light, ordinal)) {
                ordinal++;
                continue;
            }
            pose.pushPose();
            try {
                pose.translate(decoration.x() / 2F + 64, decoration.y() / 2F + 64, -.02F);
                pose.mulPose(Axis.ZP.rotationDegrees(decoration.rot() * 360 / 16F));
                pose.scale(4, 4, 3);
                pose.translate(-.125F, .125F, 0);
                var sprite = textures.get(decoration);
                image(
                        pose,
                        buffers,
                        RenderType.text(sprite.atlasLocation()),
                        light,
                        -1,
                        -1,
                        1,
                        1,
                        ordinal * -.001F,
                        new UvTransform(
                                sprite.getU1() - sprite.getU0(),
                                0,
                                sprite.getU0(),
                                0,
                                sprite.getV0() - sprite.getV1(),
                                sprite.getV1()));
            } finally {
                pose.popPose();
            }
            if (decoration.name().isPresent()) {
                var name = decoration.name().get();
                var font = Minecraft.getInstance().font;
                float width = font.width(name), scale = Mth.clamp(25 / width, 0, 6 / 9F);
                pose.pushPose();
                try {
                    pose.translate(
                            decoration.x() / 2F + 64 - width * scale / 2,
                            decoration.y() / 2F + 68,
                            -.025F);
                    pose.scale(scale, scale, 1);
                    pose.translate(0, 0, -.1F);
                    Object owner =
                            buffers
                                            instanceof
                                            com.radiance.client.vertex.StorageVertexConsumerProvider
                                                    storage
                                    ? storage.sourceOwner
                                    : null;
                    VanillaTextRequests.draw(
                            owner,
                            data,
                            ordinal,
                            font,
                            name.getVisualOrderText(),
                            0,
                            0,
                            -1,
                            false,
                            pose.last().pose(),
                            buffers,
                            Font.DisplayMode.NORMAL,
                            Integer.MIN_VALUE,
                            light);
                } finally {
                    pose.popPose();
                }
            }
            ordinal++;
        }
    }
}
