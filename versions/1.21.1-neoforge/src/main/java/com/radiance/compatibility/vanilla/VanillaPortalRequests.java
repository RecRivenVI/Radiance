package com.radiance.compatibility.vanilla;

import com.mojang.blaze3d.vertex.*;
import com.radiance.client.vertex.LocalMeshTakeover;
import net.minecraft.client.renderer.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;

/** The original face mask selects one immutable portal definition, without a BER callback. */
public final class VanillaPortalRequests {
    private VanillaPortalRequests() {}

    private record Shape(int mask, float down, float up) {}

    public static void collect(
            TheEndPortalBlockEntity source,
            float down,
            float up,
            RenderType type,
            PoseStack pose,
            MultiBufferSource buffers) {
        int mask = 0;
        for (var face : Direction.values())
            if (source.shouldRenderFace(face)) mask |= 1 << face.ordinal();
        if (mask == 0) return;
        var shape = new Shape(mask, down, up);
        LocalMeshTakeover.request(
                shape,
                pose.last(),
                buffers.getBuffer(type),
                0,
                -1,
                (p, out, light, color) -> {
                    face(shape, Direction.SOUTH, p, out, 0, 1, 0, 1, 1, 1, 1, 1);
                    face(shape, Direction.NORTH, p, out, 0, 1, 1, 0, 0, 0, 0, 0);
                    face(shape, Direction.EAST, p, out, 1, 1, 1, 0, 0, 1, 1, 0);
                    face(shape, Direction.WEST, p, out, 0, 0, 0, 1, 0, 1, 1, 0);
                    face(shape, Direction.DOWN, p, out, 0, 1, shape.down, shape.down, 0, 0, 1, 1);
                    face(shape, Direction.UP, p, out, 0, 1, shape.up, shape.up, 1, 1, 0, 0);
                });
    }

    private static void face(
            Shape shape,
            Direction direction,
            PoseStack.Pose pose,
            VertexConsumer out,
            float x0,
            float x1,
            float y0,
            float y1,
            float z0,
            float z1,
            float z2,
            float z3) {
        if ((shape.mask & 1 << direction.ordinal()) == 0) return;
        out.addVertex(pose, x0, y0, z0);
        out.addVertex(pose, x1, y0, z1);
        out.addVertex(pose, x1, y1, z2);
        out.addVertex(pose, x0, y1, z3);
    }
}
