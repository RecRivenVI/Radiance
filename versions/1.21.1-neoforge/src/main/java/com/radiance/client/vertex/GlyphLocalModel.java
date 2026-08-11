package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.api.render.LocalSurface;
import com.radiance.render.contract.DrawStateSnapshot.UvTransform;
import com.radiance.render.contract.RenderAbi;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Font layout stays source-owned; an atlas glyph's shape is a stable resource and its placement is
 * an instance.
 */
public final class GlyphLocalModel {
    private GlyphLocalModel() {}

    public static boolean eligible(VertexConsumer output) {
        return output instanceof PBRVertexConsumer pbr
                && pbr.rigidOwner != null
                && pbr.glyphTemplateEligible();
    }

    public static boolean request(
            Object shape,
            boolean italic,
            float x,
            float y,
            Matrix4f matrix,
            VertexConsumer consumer,
            float r,
            float g,
            float b,
            float a,
            int light,
            float left,
            float right,
            float up,
            float down,
            float u0,
            float u1,
            float v0,
            float v1) {
        if (!(consumer instanceof PBRVertexConsumer pbr)
                || pbr.rigidOwner == null
                || !pbr.glyphTemplateEligible()) return false;
        var pose = templatePose(new Matrix4f(matrix).translate(x, y, 0), pbr);
        if (pose == null) return false;
        var surface =
                new LocalSurface(
                        RenderAbi.APPEARANCE_COLOR_MULTIPLY | RenderAbi.APPEARANCE_OVERRIDE_LIGHT,
                        light,
                        0,
                        UvTransform.IDENTITY,
                        quantize(r),
                        quantize(g),
                        quantize(b),
                        quantize(a));
        return LocalMeshTakeover.glyph(
                shape,
                pose.last(),
                consumer,
                surface,
                (p, out, l, c) -> {
                    float upper = italic ? 1 - .25F * up : 0, lower = italic ? 1 - .25F * down : 0;
                    out.addVertex(p.pose(), left + upper, up, 0)
                            .setColor(c)
                            .setUv(u0, v0)
                            .setLight(l);
                    out.addVertex(p.pose(), left + lower, down, 0)
                            .setColor(c)
                            .setUv(u0, v1)
                            .setLight(l);
                    out.addVertex(p.pose(), right + lower, down, 0)
                            .setColor(c)
                            .setUv(u1, v1)
                            .setLight(l);
                    out.addVertex(p.pose(), right + upper, up, 0)
                            .setColor(c)
                            .setUv(u1, v0)
                            .setLight(l);
                });
    }

    public static boolean layout(
            Object shape,
            Matrix4f matrix,
            VertexConsumer output,
            int light,
            LocalMeshTakeover.Definition definition) {
        if (!(output instanceof PBRVertexConsumer pbr)
                || pbr.rigidOwner == null
                || !pbr.glyphTemplateEligible()) return false;
        var pose = templatePose(matrix, pbr);
        return pose != null
                && LocalMeshTakeover.glyph(
                        shape,
                        pose.last(),
                        output,
                        new LocalSurface(
                                RenderAbi.APPEARANCE_OVERRIDE_LIGHT,
                                light,
                                0,
                                UvTransform.IDENTITY,
                                1,
                                1,
                                1,
                                1),
                        definition);
    }

    private static PoseStack templatePose(Matrix4f matrix, PBRVertexConsumer consumer) {
        var pose = new PoseStack();
        pose.last().pose().set(matrix);
        pose.last().normal().set(new Matrix3f(matrix).invert().transpose());
        if (consumer.textPolygonOffset()) {
            var axis = matrix.transformDirection(0, 0, 1, new Vector3f());
            if (axis.lengthSquared() == 0 || !axis.isFinite()) return null;
            var correction = new Vector3f(axis).normalize().sub(axis).mul(1e-4F);
            pose.last()
                    .pose()
                    .m30(pose.last().pose().m30() + correction.x)
                    .m31(pose.last().pose().m31() + correction.y)
                    .m32(pose.last().pose().m32() + correction.z);
        }
        return pose;
    }

    private static float quantize(float value) {
        return (int) (value * 255) / 255F;
    }
}
