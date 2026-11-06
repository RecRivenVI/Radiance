package com.radiance.audit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Records the actual mixed-in fluid producer, including its reverse top quad. */
final class FluidBoundaryProbe implements VertexConsumer {
    private final List<float[]> vertices = new ArrayList<>();
    private float x, y, z;

    static Map<String, Object> capture(Minecraft mc, BlockPos position) {
        var sink = new FluidBoundaryProbe();
        var state = mc.level.getBlockState(position);
        mc.getBlockRenderer().renderLiquid(position, mc.level, sink, state, state.getFluidState());
        if (sink.vertices.size() != 8)
            throw new AssertionError(
                    "Expected actual front/back water-top quads: " + sink.vertices.size());
        boolean outward = sink.vertices.stream().allMatch(vertex -> vertex[4] >= .99F);
        if (!outward
                && Boolean.parseBoolean(
                        System.getProperty("radiance.audit.expectPhysicalFluidNormals", "true")))
            throw new AssertionError("Raster reverse member inverted the physical water normal");
        float expectedHeight =
                (position.getY() & 15) + state.getFluidState().getHeight(mc.level, position);
        boolean actualHeight =
                sink.vertices.stream()
                        .allMatch(vertex -> Math.abs(vertex[1] - expectedHeight) < 1e-5F);
        if (!actualHeight && strict())
            throw new AssertionError("Raster bias moved the physical fluid height");
        float a = winding(sink.vertices, 0), b = winding(sink.vertices, 4);
        if (a * b >= 0) throw new AssertionError("Fluid reverse winding was not exercised");
        return Map.of(
                "physicalOutward",
                outward,
                "vertices",
                sink.vertices,
                "frontWinding",
                a,
                "backWinding",
                b);
    }

    private static boolean strict() {
        return Boolean.parseBoolean(
                System.getProperty("radiance.audit.expectPhysicalFluidNormals", "true"));
    }

    static Map<String, Object> captureContact(Minecraft mc, BlockPos position) {
        var sink = new FluidBoundaryProbe();
        var state = mc.level.getBlockState(position);
        mc.getBlockRenderer().renderLiquid(position, mc.level, sink, state, state.getFluidState());
        if (sink.vertices.size() != 4 && sink.vertices.size() != 8)
            throw new AssertionError("Expected actual water/glass contact side");
        boolean onBoundary =
                sink.vertices.stream()
                        .allMatch(vertex -> vertex[3] > .99F && Math.abs(vertex[0] - 7.0F) < 1e-6F);
        if (!onBoundary && strict())
            throw new AssertionError("Raster inset left an artificial water/glass air gap");
        return Map.of("onBoundary", onBoundary, "vertices", sink.vertices);
    }

    private static float winding(List<float[]> vertices, int at) {
        float[] a = vertices.get(at), b = vertices.get(at + 1), c = vertices.get(at + 2);
        return (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
    }

    public VertexConsumer addVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public VertexConsumer setColor(int r, int g, int b, int a) {
        return this;
    }

    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    public VertexConsumer setNormal(float x, float y, float z) {
        vertices.add(new float[] {this.x, this.y, this.z, x, y, z});
        return this;
    }
}
