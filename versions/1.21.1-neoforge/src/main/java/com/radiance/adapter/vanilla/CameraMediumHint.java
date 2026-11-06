package com.radiance.adapter.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Admission hint only: the GPU still determines the actual medium and its optical properties. */
public final class CameraMediumHint {
    private CameraMediumHint() {}

    public static boolean needed(Minecraft minecraft, Vec3 eye) {
        var level = minecraft.level;
        if (level == null) return false;
        var position = BlockPos.containing(eye);
        var state = level.getBlockState(position);
        if (ItemBlockRenderTypes.getChunkRenderType(state) != RenderType.translucent())
            return false;
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        // Custom renderers may supply geometry outside the baked mesh. Do not exclude their probe.
        if (model.getClass() != SimpleBakedModel.class
                && model.getClass() != MultiPartBakedModel.class) return true;
        var data = level.getModelData(position);
        data = model.getModelData(level, position, state, data == null ? ModelData.EMPTY : data);
        var offset = state.getOffset(level, position);
        var probe =
                new Probe(
                        eye.x - position.getX() - offset.x,
                        eye.y - position.getY() - offset.y,
                        eye.z - position.getZ() - offset.z);
        var random = RandomSource.create();
        long seed = state.getSeed(position);
        for (int face = 0; face <= 6; face++) {
            random.setSeed(seed);
            var quads =
                    model.getQuads(
                            state,
                            face == 6 ? null : Direction.values()[face],
                            random,
                            data,
                            RenderType.translucent());
            for (var quad : quads) probe.add(quad.getVertices());
        }
        return probe.inside();
    }

    static final class Probe {
        private final double x, y, z;
        private int winding;
        private boolean uncertain;

        Probe(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void add(int[] vertices) {
            if (vertices.length != 32) {
                uncertain = true;
                return;
            }
            int first = triangle(vertices, 0, 1, 2);
            // A ray exactly on the quad's diagonal must count this face only once.
            winding += first != 0 ? first : triangle(vertices, 2, 3, 0);
        }

        private int triangle(int[] v, int a, int b, int c) {
            double ax = Float.intBitsToFloat(v[a * 8]) - x;
            double ay = Float.intBitsToFloat(v[a * 8 + 1]) - y;
            double az = Float.intBitsToFloat(v[a * 8 + 2]) - z;
            double bx = Float.intBitsToFloat(v[b * 8]) - x;
            double by = Float.intBitsToFloat(v[b * 8 + 1]) - y;
            double bz = Float.intBitsToFloat(v[b * 8 + 2]) - z;
            double cx = Float.intBitsToFloat(v[c * 8]) - x;
            double cy = Float.intBitsToFloat(v[c * 8 + 1]) - y;
            double cz = Float.intBitsToFloat(v[c * 8 + 2]) - z;
            if (!Double.isFinite(ax + ay + az + bx + by + bz + cx + cy + cz)) {
                uncertain = true;
                return 0;
            }
            double ex = bx - ax, ey = by - ay, ez = bz - az;
            double fx = cx - ax, fy = cy - ay, fz = cz - az;
            // The fixed non-axis-aligned direction avoids ordinary block edges and seams.
            double px = .893 * fz - .381 * fy;
            double py = .381 * fx - .237 * fz;
            double pz = .237 * fy - .893 * fx;
            double det = ex * px + ey * py + ez * pz;
            if (Math.abs(det) < 1e-12) return 0;
            double u = -(ax * px + ay * py + az * pz) / det;
            double qx = -ay * ez + az * ey;
            double qy = -az * ex + ax * ez;
            double qz = -ax * ey + ay * ex;
            double w = (.237 * qx + .893 * qy + .381 * qz) / det;
            if (u < -1e-10 || w < -1e-10 || u + w > 1 + 1e-10) return 0;
            double distance = (fx * qx + fy * qy + fz * qz) / det;
            if (Math.abs(distance) < 1e-7) uncertain = true;
            if (distance <= 0) return 0;
            return det < 0 ? 1 : -1;
        }

        boolean inside() {
            return uncertain || winding != 0;
        }
    }
}
