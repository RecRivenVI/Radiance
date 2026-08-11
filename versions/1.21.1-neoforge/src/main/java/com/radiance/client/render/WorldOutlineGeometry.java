package com.radiance.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Emits the logical edges supplied by Minecraft's complete {@link VoxelShape}. */
public final class WorldOutlineGeometry {

    private WorldOutlineGeometry() {}

    public static List<Edge> collectShapeEdges(VoxelShape shape) {
        List<Edge> edges = new ArrayList<>();
        shape.forAllEdges(
                (x0, y0, z0, x1, y1, z1) ->
                        edges.add(new Edge(new Point(x0, y0, z0), new Point(x1, y1, z1))));
        return List.copyOf(edges);
    }

    public static void emitShapeEdges(
            PoseStack.Pose pose, VertexConsumer consumer, VoxelShape shape, int color) {
        if (consumer instanceof com.radiance.client.vertex.CapturedLineVertexConsumer line) {
            line.source = LineSources.MODEL_OUTLINE;
            line.widthPolicy = com.radiance.render.contract.LineContract.WidthPolicy.DEBUG;
        }
        for (Edge edge : collectShapeEdges(shape)) {
            float dx = (float) (edge.end().x() - edge.start().x());
            float dy = (float) (edge.end().y() - edge.start().y());
            float dz = (float) (edge.end().z() - edge.start().z());
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length <= 0.0F) {
                continue;
            }
            dx /= length;
            dy /= length;
            dz /= length;
            // The derived edge authors its roll explicitly; source normals keep their meaning.
            float ax = Math.abs(dx);
            float ay = Math.abs(dy);
            float az = Math.abs(dz);
            float nx = 0.0F;
            float ny = 0.0F;
            float nz = 0.0F;
            if (ay <= ax && ay <= az) {
                ny = 1.0F;
            } else if (ax <= az) {
                nx = 1.0F;
            } else {
                nz = 1.0F;
            }
            emitPoint(pose, consumer, edge.start(), color, dx, dy, dz, nx, ny, nz);
            emitPoint(pose, consumer, edge.end(), color, dx, dy, dz, nx, ny, nz);
        }
    }

    private static void emitPoint(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Point point,
            int color,
            float dx,
            float dy,
            float dz,
            float nx,
            float ny,
            float nz) {
        consumer.addVertex(pose, (float) point.x(), (float) point.y(), (float) point.z())
                .setColor(color)
                .setNormal(pose, dx, dy, dz);
        if (consumer instanceof com.radiance.client.vertex.LineSectionReference reference)
            reference.sectionReference(pose, nx, ny, nz);
    }

    public record Point(double x, double y, double z) {}

    public record Edge(Point start, Point end) {}
}
