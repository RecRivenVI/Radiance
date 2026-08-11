package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Display;

/** Display interpolation and the original cached text layout feed shared world requests. */
public final class VanillaDisplayRequests {
    private VanillaDisplayRequests() {}

    public static boolean supports(Object state) {
        if (state instanceof Display.BlockDisplay.BlockRenderState block)
            return VanillaBlockModelRequests.supports(block.blockState());
        if (state instanceof Display.ItemDisplay.ItemRenderState item)
            return VanillaItemReplacement.supportsWorldStack(item.itemStack());
        return state == null || state instanceof Display.TextDisplay.TextRenderState;
    }

    public static void collect(
            Display entity,
            Object state,
            float progress,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        if (state instanceof Display.BlockDisplay.BlockRenderState block)
            VanillaBlockModelRequests.add(
                    block.blockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        else if (state instanceof Display.ItemDisplay.ItemRenderState item) {
            pose.mulPose(Axis.YP.rotation((float) Math.PI));
            VanillaItemReplacement.worldItem(
                    entity.level(),
                    item.itemStack(),
                    item.itemTransform(),
                    pose,
                    buffers,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    entity.getId());
        } else if (state instanceof Display.TextDisplay.TextRenderState text) {
            var client = Minecraft.getInstance();
            var font = client.font;
            int flags = text.flags();
            boolean through = (flags & 2) != 0, shadow = (flags & 1) != 0;
            byte opacity = (byte) text.textOpacity().get(progress);
            int background =
                    (flags & 4) != 0
                            ? (int) (client.options.getBackgroundOpacity(.25F) * 255) << 24
                            : text.backgroundColor().get(progress);
            var matrix = pose.last().pose();
            matrix.rotate((float) Math.PI, 0, 1, 0);
            matrix.scale(-.025F, -.025F, -.025F);
            var layout =
                    ((Display.TextDisplay) entity)
                            .cacheDisplay(
                                    (component, lineWidth) -> {
                                        var lines = new ArrayList<Display.TextDisplay.CachedLine>();
                                        int width = 0;
                                        for (var line : font.split(component, lineWidth)) {
                                            int size = font.width(line);
                                            width = Math.max(width, size);
                                            lines.add(
                                                    new Display.TextDisplay.CachedLine(line, size));
                                        }
                                        return new Display.TextDisplay.CachedInfo(lines, width);
                                    });
            int width = layout.width(), height = layout.lines().size() * 10;
            matrix.translate(1 - width / 2F, -height, 0);
            if (background != 0) {
                var out =
                        buffers.getBuffer(
                                through
                                        ? RenderType.textBackgroundSeeThrough()
                                        : RenderType.textBackground());
                out.addVertex(matrix, -1, -1, 0).setColor(background).setLight(light);
                out.addVertex(matrix, -1, height, 0).setColor(background).setLight(light);
                out.addVertex(matrix, width, height, 0).setColor(background).setLight(light);
                out.addVertex(matrix, width, -1, 0).setColor(background).setLight(light);
            }
            float y = 0;
            int textOrdinal = 0;
            var align = Display.TextDisplay.getAlign((byte) flags);
            for (var line : layout.lines()) {
                float x =
                        switch (align) {
                            case LEFT -> 0;
                            case RIGHT -> width - line.width();
                            case CENTER -> width / 2F - line.width() / 2F;
                        };
                VanillaTextRequests.draw(
                        entity,
                        "display",
                        textOrdinal++,
                        font,
                        line.contents(),
                        x,
                        y,
                        opacity << 24 | 0xffffff,
                        shadow,
                        matrix,
                        buffers,
                        through ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET,
                        0,
                        light);
                y += 10;
            }
        }
    }
}
