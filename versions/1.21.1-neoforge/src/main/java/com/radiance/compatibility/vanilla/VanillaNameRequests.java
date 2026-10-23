package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.render.NameTagBackgroundStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;

/** Original name placement and accepted background ownership, expressed as world-text requests. */
public final class VanillaNameRequests {
    private VanillaNameRequests() {}

    public static void collect(
            EntityRenderer<?> renderer,
            Entity entity,
            Component content,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            float partial) {
        if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer
                && entity instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            pose.pushPose();
            var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            if (dispatcher.distanceToSqr(player) < 100) {
                var scoreboard = player.getScoreboard();
                var objective =
                        scoreboard.getDisplayObjective(
                                net.minecraft.world.scores.DisplaySlot.BELOW_NAME);
                if (objective != null) {
                    var score = scoreboard.getPlayerScoreInfo(player, objective);
                    var value =
                            net.minecraft.world.scores.ReadOnlyScoreInfo.safeFormatValue(
                                    score,
                                    objective.numberFormatOrDefault(
                                            net.minecraft.network.chat.numbers.StyledFormat
                                                    .NO_STYLE));
                    base(
                            renderer,
                            entity,
                            Component.empty()
                                    .append(value)
                                    .append(net.minecraft.network.chat.CommonComponents.SPACE)
                                    .append(objective.getDisplayName()),
                            pose,
                            buffers,
                            light,
                            partial);
                    pose.translate(0, 9F * 1.15F * .025F, 0);
                }
            }
            base(renderer, entity, content, pose, buffers, light, partial);
            pose.popPose();
            return;
        }
        if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame)
            content = frame.getItem().getHoverName();
        base(renderer, entity, content, pose, buffers, light, partial);
    }

    private static void base(
            EntityRenderer<?> renderer,
            Entity entity,
            Component content,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            float partial) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (!net.neoforged.neoforge.client.ClientHooks.isNameplateInRenderDistance(
                entity, dispatcher.distanceToSqr(entity))) return;
        var attachment =
                entity.getAttachments()
                        .getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partial));
        if (attachment == null) return;
        boolean through = !entity.isDiscrete();
        int y = "deadmau5".equals(content.getString()) ? -10 : 0;
        pose.pushPose();
        pose.translate(attachment.x, attachment.y + .5, attachment.z);
        pose.mulPose(dispatcher.cameraOrientation());
        pose.scale(.025F, -.025F, .025F);
        var font = renderer.getFont();
        float x = -font.width(content) / 2;
        int background =
                (int) (Minecraft.getInstance().options.getBackgroundOpacity(.25F) * 255) << 24;
        var mode = through ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        if (background != 0 && EntityProxy.postTextVertexConsumerProvider != null) {
            var out =
                    buffers.getBuffer(
                            through
                                    ? RenderType.textBackgroundSeeThrough()
                                    : RenderType.textBackground());
            int color = NameTagBackgroundStyle.applyOpacity(background);
            var matrix = pose.last().pose();
            float x0 = x - 1, x1 = x + font.width(content) + 1, y0 = y + 9, y1 = y - 1;
            out.addVertex(matrix, x0, y0, .01F).setColor(color).setLight(light);
            out.addVertex(matrix, x1, y0, .01F).setColor(color).setLight(light);
            out.addVertex(matrix, x1, y1, .01F).setColor(color).setLight(light);
            out.addVertex(matrix, x0, y1, .01F).setColor(color).setLight(light);
            background = 0;
        }
        font.drawInBatch(
                content,
                x,
                y,
                553648127,
                false,
                pose.last().pose(),
                buffers,
                mode,
                background,
                light);
        if (through)
            font.drawInBatch(
                    content,
                    x,
                    y,
                    -1,
                    false,
                    pose.last().pose(),
                    buffers,
                    Font.DisplayMode.NORMAL,
                    0,
                    light);
        pose.popPose();
    }
}
