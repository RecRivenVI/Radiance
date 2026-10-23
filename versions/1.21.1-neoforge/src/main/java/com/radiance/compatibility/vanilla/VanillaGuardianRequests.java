package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.client.vertex.LocalMeshTakeover;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;

/** The original beam parameters, independent of the living renderer's mesh output. */
public final class VanillaGuardianRequests {
    private VanillaGuardianRequests() {}

    private record Beam(float length, float angle, float v0, float v1, float cap) {}

    private static final RenderType TYPE =
            RenderType.entityCutoutNoCull(
                    ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png"));

    public static void collect(
            Guardian source, float partial, PoseStack pose, MultiBufferSource buffers) {
        var target = source.getActiveAttackTarget();
        if (target == null) return;
        float strength = source.getAttackAnimationScale(partial),
                time = source.getClientSideAttackTime() + partial;
        float eye = source.getEyeHeight();
        pose.pushPose();
        pose.translate(0, eye, 0);
        var delta =
                position(target, target.getBbHeight() * .5, partial)
                        .subtract(position(source, eye, partial));
        float length = (float) (delta.length() + 1);
        delta = delta.normalize();
        float pitch = (float) Math.acos(delta.y), yaw = (float) Math.atan2(delta.z, delta.x);
        pose.mulPose(
                Axis.YP.rotationDegrees(((float) (Math.PI / 2) - yaw) * (180F / (float) Math.PI)));
        pose.mulPose(Axis.XP.rotationDegrees(pitch * (180F / (float) Math.PI)));
        float squared = strength * strength, v0 = -1 + time * .5F % 1;
        int color =
                net.minecraft.util.FastColor.ARGB32.color(
                        255,
                        64 + (int) (squared * 191),
                        32 + (int) (squared * 191),
                        128 - (int) (squared * 64));
        var beam =
                new Beam(
                        length,
                        time * .05F * -1.5F,
                        v0,
                        length * 2.5F + v0,
                        source.tickCount % 2 == 0 ? .5F : 0);
        LocalMeshTakeover.request(
                beam,
                pose.last(),
                buffers.getBuffer(TYPE),
                15728880,
                color,
                (p, out, light, tint) -> {
                    float[] side = new float[8], cap = new float[8];
                    float[] a = {
                        (float) Math.PI, 0, (float) (Math.PI / 2), (float) (Math.PI * 3 / 2)
                    };
                    float[] b = {
                        (float) (Math.PI * 3 / 4),
                        (float) (Math.PI / 4),
                        (float) (Math.PI * 5 / 4),
                        (float) (Math.PI * 7 / 4)
                    };
                    for (int i = 0; i < 4; i++) {
                        side[i * 2] = Mth.cos(beam.angle + a[i]) * .2F;
                        side[i * 2 + 1] = Mth.sin(beam.angle + a[i]) * .2F;
                        cap[i * 2] = Mth.cos(beam.angle + b[i]) * .282F;
                        cap[i * 2 + 1] = Mth.sin(beam.angle + b[i]) * .282F;
                    }
                    for (int i = 0; i < 4; i += 2) {
                        int j = i + 1;
                        VanillaProjectileMeshes.vertex(
                                p,
                                out,
                                side[i * 2],
                                beam.length,
                                side[i * 2 + 1],
                                .4999F,
                                beam.v1,
                                0,
                                1,
                                0,
                                light,
                                tint);
                        VanillaProjectileMeshes.vertex(
                                p,
                                out,
                                side[i * 2],
                                0,
                                side[i * 2 + 1],
                                .4999F,
                                beam.v0,
                                0,
                                1,
                                0,
                                light,
                                tint);
                        VanillaProjectileMeshes.vertex(
                                p,
                                out,
                                side[j * 2],
                                0,
                                side[j * 2 + 1],
                                0,
                                beam.v0,
                                0,
                                1,
                                0,
                                light,
                                tint);
                        VanillaProjectileMeshes.vertex(
                                p,
                                out,
                                side[j * 2],
                                beam.length,
                                side[j * 2 + 1],
                                0,
                                beam.v1,
                                0,
                                1,
                                0,
                                light,
                                tint);
                    }
                    int[] order = {0, 1, 3, 2};
                    for (int i = 0; i < 4; i++) {
                        int j = order[i];
                        VanillaProjectileMeshes.vertex(
                                p,
                                out,
                                cap[j * 2],
                                beam.length,
                                cap[j * 2 + 1],
                                i == 0 || i == 3 ? .5F : 1,
                                i < 2 ? beam.cap + .5F : beam.cap,
                                0,
                                1,
                                0,
                                light,
                                tint);
                    }
                });
        pose.popPose();
    }

    private static Vec3 position(LivingEntity value, double height, float partial) {
        return new Vec3(
                Mth.lerp((double) partial, value.xOld, value.getX()),
                Mth.lerp((double) partial, value.yOld, value.getY()) + height,
                Mth.lerp((double) partial, value.zOld, value.getZ()));
    }
}
