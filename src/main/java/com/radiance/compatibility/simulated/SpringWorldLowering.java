package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.radiance.client.vertex.PBRVertexConsumer;

/** Physical-surface lowering of the original live spring producer's vertices. */
public final class SpringWorldLowering {
    private SpringWorldLowering() {}

    public static PBRVertexConsumer createConsumer(ByteBufferBuilder allocator,
        SpringDrawContract.Draw draw) {
        SpringShaderVerification.requireActive();
        // No geometry cache: every producer draw supplies the current spline, UV and normal.
        // The active RenderType resolves its current body image and native sampler state.
        return PBRVertexConsumer.dynamicMaterial(allocator, draw.renderType(), false,
            SpringDrawContract.WORLD_MATERIAL);
    }
}
