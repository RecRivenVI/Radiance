package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.render.RasterPreviewScope;
import net.minecraft.client.renderer.RenderType;

/**
 * Converts Simulated's spring vertex contract into Radiance material data.
 */
public final class SimulatedVertexCompatibility {
    private static final boolean COMPACT_SOURCE = Boolean.getBoolean("radiance.compactVertices");

    private SimulatedVertexCompatibility() {
    }

    public static VertexConsumer createQuadConsumer(ByteBufferBuilder allocator,
        RenderType renderType) {
        if (isLockLayer(renderType)) {
            // Simulated's lock uses POSITION_COLOR_TEX_LIGHTMAP, full-bright vertices,
            // and a 0.1 texture cutout. Alpha here is texture coverage, not glass.
            PBRVertexConsumer result = new LockPBRVertexConsumer(allocator, renderType);
            result.setDefaultAlbedoEmission(1.0F);
            return result;
        }
        SpringDrawContract.Draw spring = SpringDrawContract.from(renderType);
        if (spring != null) {
            return SpringWorldLowering.createConsumer(allocator, spring);
        }
        return PBRVertexConsumer.dynamic(allocator, renderType,
            COMPACT_SOURCE && !RasterPreviewScope.active());
    }

    public static boolean isLockLayer(RenderType type) {
        return "simulated:lock".equals(type.name);
    }

    private static final class LockPBRVertexConsumer extends PBRVertexConsumer {
        private LockPBRVertexConsumer(ByteBufferBuilder allocator, RenderType renderType) {
            super(allocator, renderType, ALPHA_MODE_CUTOUT_LOW);
        }
    }

}
