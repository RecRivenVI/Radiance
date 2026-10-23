package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.LocalMeshTakeover;
import net.minecraft.client.renderer.*;
import net.minecraft.util.RandomSource;

/** Seed is the complete original local shape input; it is not regenerated on ordinary frames. */
public final class VanillaLightningRequests {
    private VanillaLightningRequests() {}

    private record Shape(long seed) {}

    public static void collect(long seed, PoseStack pose, MultiBufferSource buffers) {
        LocalMeshTakeover.request(
                new Shape(seed),
                pose.last(),
                buffers.getBuffer(RenderType.lightning()),
                0,
                -1,
                (p, out, light, color) -> {
                    float[] xs = new float[8], zs = new float[8];
                    float x = 0, z = 0;
                    var random = RandomSource.create(seed);
                    for (int i = 7; i >= 0; i--) {
                        xs[i] = x;
                        zs[i] = z;
                        x += random.nextInt(11) - 5;
                        z += random.nextInt(11) - 5;
                    }
                    for (int layer = 0; layer < 4; layer++) {
                        var branchRandom = RandomSource.create(seed);
                        for (int branch = 0; branch < 3; branch++) {
                            int high = branch > 0 ? 7 - branch : 7, low = branch > 0 ? high - 2 : 0;
                            float a = xs[high] - x, b = zs[high] - z;
                            for (int height = high; height >= low; height--) {
                                float previousX = a, previousZ = b;
                                if (branch == 0) {
                                    a += branchRandom.nextInt(11) - 5;
                                    b += branchRandom.nextInt(11) - 5;
                                } else {
                                    a += branchRandom.nextInt(31) - 15;
                                    b += branchRandom.nextInt(31) - 15;
                                }
                                float top = .1F + layer * .2F, bottom = .1F + layer * .2F;
                                if (branch == 0) {
                                    top *= height * .1F + 1;
                                    bottom *= (height - 1F) * .1F + 1;
                                }
                                quad(
                                        p, out, a, b, height, previousX, previousZ, top, bottom,
                                        false, false, true, false);
                                quad(
                                        p, out, a, b, height, previousX, previousZ, top, bottom,
                                        true, false, true, true);
                                quad(
                                        p, out, a, b, height, previousX, previousZ, top, bottom,
                                        true, true, false, true);
                                quad(
                                        p, out, a, b, height, previousX, previousZ, top, bottom,
                                        false, true, false, false);
                            }
                        }
                    }
                });
    }

    private static void quad(
            PoseStack.Pose pose,
            VertexConsumer out,
            float x,
            float z,
            int y,
            float px,
            float pz,
            float top,
            float bottom,
            boolean x0,
            boolean z0,
            boolean x1,
            boolean z1) {
        out.addVertex(pose, x + (x0 ? bottom : -bottom), y * 16, z + (z0 ? bottom : -bottom))
                .setColor(.45F, .45F, .5F, .3F)
                .setUv(0, 0);
        out.addVertex(pose, px + (x0 ? top : -top), (y + 1) * 16, pz + (z0 ? top : -top))
                .setColor(.45F, .45F, .5F, .3F)
                .setUv(1, 0);
        out.addVertex(pose, px + (x1 ? top : -top), (y + 1) * 16, pz + (z1 ? top : -top))
                .setColor(.45F, .45F, .5F, .3F)
                .setUv(1, 1);
        out.addVertex(pose, x + (x1 ? bottom : -bottom), y * 16, z + (z1 ? bottom : -bottom))
                .setColor(.45F, .45F, .5F, .3F)
                .setUv(0, 1);
    }
}
