package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.client.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;

/** Original stable RNG placement; arrow and stinger definitions share the local mesh executor. */
public final class VanillaStuckItemRequests {
    private VanillaStuckItemRequests() {}

    private static final RenderType STINGER_TYPE =
            RenderType.entityCutoutNoCull(
                    ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_stinger.png"));
    private static final LocalMeshTakeover.Definition STINGER =
            (root, out, light, color) -> {
                var pose = new PoseStack();
                pose.mulPose(root.pose());
                for (int i = 0; i < 4; i++) {
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    VanillaProjectileMeshes.vertex(
                            pose.last(), out, -4.5F, -1, 0, 0, 0, 0, 1, 0, light, color);
                    VanillaProjectileMeshes.vertex(
                            pose.last(), out, 4.5F, -1, 0, .125F, 0, 0, 1, 0, light, color);
                    VanillaProjectileMeshes.vertex(
                            pose.last(), out, 4.5F, 1, 0, .125F, .0625F, 0, 1, 0, light, color);
                    VanillaProjectileMeshes.vertex(
                            pose.last(), out, -4.5F, 1, 0, 0, .0625F, 0, 1, 0, light, color);
                }
            };

    public static void collect(
            boolean arrows,
            LivingEntity entity,
            PlayerModel<?> model,
            float partial,
            PoseStack pose,
            MultiBufferSource buffers,
            BoundPartCapture requests,
            int light) {
        int count = arrows ? entity.getArrowCount() : entity.getStingerCount();
        var random = RandomSource.create(entity.getId());
        for (int i = 0; i < count; i++) {
            pose.pushPose();
            var part = model.getRandomModelPart(random);
            var cube = part.getRandomCube(random);
            part.translateAndRotate(pose);
            float x = random.nextFloat(), y = random.nextFloat(), z = random.nextFloat();
            pose.translate(
                    Mth.lerp(x, cube.minX, cube.maxX) / 16,
                    Mth.lerp(y, cube.minY, cube.maxY) / 16,
                    Mth.lerp(z, cube.minZ, cube.maxZ) / 16);
            x = -(x * 2 - 1);
            y = -(y * 2 - 1);
            z = -(z * 2 - 1);
            float horizontal = Mth.sqrt(x * x + z * z);
            float yaw = (float) (Math.atan2(x, z) * 180F / (float) Math.PI),
                    pitch = (float) (Math.atan2(y, horizontal) * 180F / (float) Math.PI);
            if (arrows) {
                var arrow =
                        new Arrow(
                                entity.level(),
                                entity.getX(),
                                entity.getY(),
                                entity.getZ(),
                                ItemStack.EMPTY,
                                null);
                arrow.setYRot(yaw);
                arrow.setXRot(pitch);
                arrow.yRotO = yaw;
                arrow.xRotO = pitch;
                var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                if (VanillaEntityReplacement.eligible(dispatcher, arrow, buffers, requests.owner()))
                    VanillaEntityReplacement.render(
                            dispatcher, arrow, 0, partial, pose, requests.owner(), buffers, light);
                else dispatcher.render(arrow, 0, 0, 0, 0, partial, pose, buffers, light);
            } else {
                pose.translate(0, 0, 0);
                pose.mulPose(Axis.YP.rotationDegrees(yaw - 90));
                pose.mulPose(Axis.ZP.rotationDegrees(pitch));
                pose.mulPose(Axis.XP.rotationDegrees(45));
                pose.scale(.03125F, .03125F, .03125F);
                pose.translate(2.5F, 0, 0);
                LocalMeshTakeover.request(
                        STINGER, pose.last(), buffers.getBuffer(STINGER_TYPE), light, -1, STINGER);
            }
            pose.popPose();
        }
    }
}
