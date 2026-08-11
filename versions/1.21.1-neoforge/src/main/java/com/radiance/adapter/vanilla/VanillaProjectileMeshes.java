package com.radiance.adapter.vanilla;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.radiance.client.vertex.LocalMeshTakeover;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Immutable arrow and billboard definitions from the original primitive layouts. */
public final class VanillaProjectileMeshes {
    private VanillaProjectileMeshes() {}

    public static void vertex(
            PoseStack.Pose pose,
            VertexConsumer out,
            float x,
            float y,
            float z,
            float u,
            float v,
            int nx,
            int ny,
            int nz,
            int light,
            int color) {
        out.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    public static final LocalMeshTakeover.Definition ARROW =
            (root, out, light, color) -> {
                var pose = new PoseStack();
                pose.mulPose(root.pose());
                vertex(pose.last(), out, -7, -2, -2, 0, .15625F, -1, 0, 0, light, color);
                vertex(pose.last(), out, -7, -2, 2, .15625F, .15625F, -1, 0, 0, light, color);
                vertex(pose.last(), out, -7, 2, 2, .15625F, .3125F, -1, 0, 0, light, color);
                vertex(pose.last(), out, -7, 2, -2, 0, .3125F, -1, 0, 0, light, color);
                vertex(pose.last(), out, -7, 2, -2, 0, .15625F, 1, 0, 0, light, color);
                vertex(pose.last(), out, -7, 2, 2, .15625F, .15625F, 1, 0, 0, light, color);
                vertex(pose.last(), out, -7, -2, 2, .15625F, .3125F, 1, 0, 0, light, color);
                vertex(pose.last(), out, -7, -2, -2, 0, .3125F, 1, 0, 0, light, color);
                for (int side = 0; side < 4; side++) {
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    vertex(pose.last(), out, -8, -2, 0, 0, 0, 0, 0, 1, light, color);
                    vertex(pose.last(), out, 8, -2, 0, .5F, 0, 0, 0, 1, light, color);
                    vertex(pose.last(), out, 8, 2, 0, .5F, .15625F, 0, 0, 1, light, color);
                    vertex(pose.last(), out, -8, 2, 0, 0, .15625F, 0, 0, 1, light, color);
                }
            };

    public static LocalMeshTakeover.Definition billboard(float u0, float u1, float v0, float v1) {
        return (pose, out, light, color) -> {
            vertex(pose, out, -.5F, -.25F, 0, u0, v1, 0, 1, 0, light, color);
            vertex(pose, out, .5F, -.25F, 0, u1, v1, 0, 1, 0, light, color);
            vertex(pose, out, .5F, .75F, 0, u1, v0, 0, 1, 0, light, color);
            vertex(pose, out, -.5F, .75F, 0, u0, v0, 0, 1, 0, light, color);
        };
    }

    public static final LocalMeshTakeover.Definition FIREBALL = billboard(0, 1, 0, 1);
    public static final LocalMeshTakeover.Definition HOOK =
            (pose, out, light, color) -> {
                vertex(pose, out, -.5F, -.5F, 0, 0, 1, 0, 1, 0, light, color);
                vertex(pose, out, .5F, -.5F, 0, 1, 1, 0, 1, 0, light, color);
                vertex(pose, out, .5F, .5F, 0, 1, 0, 0, 1, 0, light, color);
                vertex(pose, out, -.5F, .5F, 0, 0, 0, 0, 1, 0, light, color);
            };
    public static final LocalMeshTakeover.Definition[] ORBS = new LocalMeshTakeover.Definition[12];

    static {
        for (int i = 0; i < ORBS.length; i++)
            ORBS[i] =
                    billboard(
                            (i % 4 * 16) / 64F,
                            (i % 4 * 16 + 16) / 64F,
                            (i / 4 * 16) / 64F,
                            (i / 4 * 16 + 16) / 64F);
    }
}
