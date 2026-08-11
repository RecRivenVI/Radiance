package com.radiance.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Converts the source depth cap into a finite interior bounded by its actual floor. */
public final class BoatWaterExclusion {
    public interface FloorSource {
        ModelPart radiance$waterFloor();
    }

    private BoatWaterExclusion() {}

    public static void request(
            ModelPart patch,
            ModelPart floor,
            PoseStack pose,
            VertexConsumer output,
            int light,
            int overlay) {
        render(patch, floor, pose, output, light, overlay, true);
    }

    public static void render(
            ModelPart patch,
            ModelPart floor,
            PoseStack pose,
            VertexConsumer output,
            int light,
            int overlay) {
        render(patch, floor, pose, output, light, overlay, false);
    }

    private static void emit(
            ModelPart patch,
            PoseStack pose,
            VertexConsumer output,
            int light,
            int overlay,
            boolean request) {
        if (request)
            com.radiance.client.vertex.BoundPartCapture.emitTemplate(
                    com.radiance.adapter.vanilla.VanillaModelPose.extract(patch, pose, -1),
                    output,
                    light,
                    overlay);
        else patch.render(pose, output, light, overlay);
    }

    private static void render(
            ModelPart patch,
            ModelPart floor,
            PoseStack pose,
            VertexConsumer output,
            int light,
            int overlay,
            boolean request) {
        if (floor == null || !patch.visible || !floor.visible || floor.skipDraw) {
            emit(patch, pose, output, light, overlay, request);
            return;
        }
        PoseStack local = new PoseStack();
        patch.translateAndRotate(local);
        Matrix4f patchToParent = new Matrix4f(local.last().pose());
        Matrix4f parentToPatch = new Matrix4f(patchToParent).invert();
        Bounds cap = bounds(patch, parentToPatch);
        Bounds bottom = bounds(floor, parentToPatch);
        int axis = 0;
        for (int i = 1; i < 3; ++i)
            if (cap.max.get(i) - cap.min.get(i) < cap.max.get(axis) - cap.min.get(axis)) axis = i;
        float low = cap.min.get(axis), high = cap.max.get(axis);
        float floorLow = bottom.min.get(axis), floorHigh = bottom.max.get(axis);
        // The captured source slab supplies its extrusion axis; the source hull supplies
        // the near floor surface. Never extend indefinitely or use a boat-size constant.
        float newLow = low, newHigh = high;
        if (floorHigh < low) newLow = floorHigh;
        else if (floorLow > high) newHigh = floorLow;
        if (!cap.finite()
                || !bottom.finite()
                || high - low <= 1e-6f
                || (newLow == low && newHigh == high)) {
            emit(patch, pose, output, light, overlay, request);
            return;
        }
        Matrix4f toWorld = new Matrix4f(pose.last().pose()).mul(patchToParent);
        Matrix4f toLocal = new Matrix4f(toWorld).invert();
        final int depthAxis = axis;
        final float start = newLow, scale = (newHigh - newLow) / (high - low);
        // A wrapper also keeps this control mesh out of persistent surface/BLAS capture.
        VertexConsumer mapped =
                new VertexConsumer() {
                    public VertexConsumer addVertex(float x, float y, float z) {
                        Vector3f p = toLocal.transformPosition(new Vector3f(x, y, z));
                        p.setComponent(depthAxis, start + (p.get(depthAxis) - low) * scale);
                        toWorld.transformPosition(p);
                        output.addVertex(p.x, p.y, p.z);
                        return this;
                    }

                    public VertexConsumer setColor(int r, int g, int b, int a) {
                        output.setColor(r, g, b, a);
                        return this;
                    }

                    public VertexConsumer setUv(float u, float v) {
                        output.setUv(u, v);
                        return this;
                    }

                    public VertexConsumer setUv1(int u, int v) {
                        output.setUv1(u, v);
                        return this;
                    }

                    public VertexConsumer setUv2(int u, int v) {
                        output.setUv2(u, v);
                        return this;
                    }

                    public VertexConsumer setNormal(float x, float y, float z) {
                        output.setNormal(x, y, z);
                        return this;
                    }
                };
        emit(patch, pose, mapped, light, overlay, request);
    }

    private static Bounds bounds(ModelPart part, Matrix4f transform) {
        PoseStack pose = new PoseStack();
        pose.mulPose(transform);
        Bounds bounds = new Bounds();
        part.visit(
                pose,
                (at, path, index, cube) -> {
                    // Cube's min/max fields omit authored deformation; use exactly
                    // the polygon positions that ModelPart.compile would submit.
                    for (var polygon : cube.polygons)
                        for (var vertex : polygon.vertices) {
                            Vector3f p = new Vector3f(vertex.pos).div(16);
                            at.pose().transformPosition(p);
                            bounds.min.min(p);
                            bounds.max.max(p);
                        }
                });
        return bounds;
    }

    private static final class Bounds {
        final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);

        boolean finite() {
            return min.isFinite() && max.isFinite();
        }
    }
}
