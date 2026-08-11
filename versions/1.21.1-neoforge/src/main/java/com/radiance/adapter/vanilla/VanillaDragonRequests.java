package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.api.render.LocalSurface;
import com.radiance.client.vertex.LocalMeshTakeover;
import com.radiance.render.contract.DrawStateSnapshot.UvTransform;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** A dragon reuses its neck part at different poses; each occurrence keeps a separate identity. */
public final class VanillaDragonRequests {
    private VanillaDragonRequests() {}

    public interface PoseSource {
        List<VanillaModelPose.Part> radiance$parts(PoseStack pose, int color);
    }

    /** One tapered eight-sided mesh; distance and scrolling UV are instance parameters. */
    public static void beam(
            float x,
            float y,
            float z,
            float partial,
            int ticks,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        float horizontal = Mth.sqrt(x * x + z * z), length = Mth.sqrt(x * x + y * y + z * z);
        if (length == 0)
            return; // The source's beam has zero area here; do not create a singular instance.
        pose.pushPose();
        try {
            pose.translate(0, 2, 0);
            pose.mulPose(Axis.YP.rotation((float) -Math.atan2(z, x) - (float) (Math.PI / 2)));
            pose.mulPose(
                    Axis.XP.rotation((float) -Math.atan2(horizontal, y) - (float) (Math.PI / 2)));
            pose.scale(1, 1, length);
            float scroll = -(ticks + partial) * .01F;
            LocalMeshTakeover.template(
                    "vanilla-crystal-beam",
                    pose.last(),
                    buffers.getBuffer(
                            RenderType.entitySmoothCutout(
                                    EnderDragonRenderer.CRYSTAL_BEAM_LOCATION)),
                    LocalSurface.uniform(
                            light,
                            OverlayTexture.NO_OVERLAY,
                            -1,
                            new UvTransform(1, 0, 0, 0, length / 32, scroll)),
                    (p, out, l, c) -> {
                        float previousX = 0, previousY = .75F, previousU = 0;
                        for (int i = 1; i <= 8; i++) {
                            float nx = Mth.sin(i * (float) (Math.PI * 2) / 8) * .75F;
                            float ny = Mth.cos(i * (float) (Math.PI * 2) / 8) * .75F, u = i / 8F;
                            VanillaProjectileMeshes.vertex(
                                    p,
                                    out,
                                    previousX * .2F,
                                    previousY * .2F,
                                    0,
                                    previousU,
                                    0,
                                    0,
                                    -1,
                                    0,
                                    l,
                                    0xff000000);
                            VanillaProjectileMeshes.vertex(
                                    p, out, previousX, previousY, 1, previousU, 1, 0, -1, 0, l, -1);
                            VanillaProjectileMeshes.vertex(
                                    p, out, nx, ny, 1, u, 1, 0, -1, 0, l, -1);
                            VanillaProjectileMeshes.vertex(
                                    p, out, nx * .2F, ny * .2F, 0, u, 0, 0, -1, 0, l, 0xff000000);
                            previousX = nx;
                            previousY = ny;
                            previousU = u;
                        }
                    });
        } finally {
            pose.popPose();
        }
    }
}
