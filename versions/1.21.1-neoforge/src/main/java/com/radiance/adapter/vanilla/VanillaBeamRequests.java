package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.radiance.client.vertex.LocalMeshTakeover;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.*;

/** Beacon and gateway beams share the original section, scroll and two-shell formulas. */
public final class VanillaBeamRequests {
    private VanillaBeamRequests() {}

    private record Shape(int start, int end, float radius, boolean outer) {}

    public static void collect(
            ResourceLocation texture,
            float partial,
            float scale,
            long tick,
            int start,
            int height,
            int color,
            float inner,
            float outer,
            PoseStack pose,
            MultiBufferSource buffers) {
        int end = start + height;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        float phase = (float) Math.floorMod(tick, 40) + partial;
        float signed = height < 0 ? phase : -phase,
                scroll = Mth.frac(signed * .2F - Mth.floor(signed * .1F));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(phase * 2.25F - 45));
        float v0 = -1 + scroll;
        request(
                texture,
                new Shape(start, end, inner, false),
                height * scale * (.5F / inner),
                v0,
                color,
                pose,
                buffers);
        pose.popPose();
        request(
                texture,
                new Shape(start, end, outer, true),
                height * scale,
                v0,
                FastColor.ARGB32.color(32, color),
                pose,
                buffers);
        pose.popPose();
    }

    private static void request(
            ResourceLocation texture,
            Shape shape,
            float vScale,
            float vOffset,
            int color,
            PoseStack pose,
            MultiBufferSource buffers) {
        LocalMeshTakeover.template(
                shape,
                pose.last(),
                buffers.getBuffer(RenderType.beaconBeam(texture, shape.outer)),
                com.radiance.api.render.LocalSurface.uniform(
                        15728880,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        color,
                        new com.radiance.render.contract.DrawStateSnapshot.UvTransform(
                                1, 0, 0, 0, vScale, vOffset)),
                (p, out, light, tint) -> {
                    float r = shape.radius;
                    float[] x =
                            shape.outer ? new float[] {-r, r, -r, r} : new float[] {0, r, -r, 0};
                    float[] z =
                            shape.outer ? new float[] {-r, -r, r, r} : new float[] {r, 0, 0, -r};
                    int[] pairs = {0, 1, 3, 2, 1, 3, 2, 0};
                    for (int i = 0; i < pairs.length; i += 2) {
                        int a = pairs[i], b = pairs[i + 1];
                        VanillaProjectileMeshes.vertex(
                                p, out, x[a], shape.end, z[a], 1, 1, 0, 1, 0, light, tint);
                        VanillaProjectileMeshes.vertex(
                                p, out, x[a], shape.start, z[a], 1, 0, 0, 1, 0, light, tint);
                        VanillaProjectileMeshes.vertex(
                                p, out, x[b], shape.start, z[b], 0, 0, 0, 1, 0, light, tint);
                        VanillaProjectileMeshes.vertex(
                                p, out, x[b], shape.end, z[b], 0, 1, 0, 1, 0, light, tint);
                    }
                });
    }
}
