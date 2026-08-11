package com.radiance.compatibility.simulated;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PBRVertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Converts Simulated's spring vertex contract into Radiance material data.
 */
public final class SimulatedVertexCompatibility {

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
        return isSpringLayer(renderType)
            ? new SpringPBRVertexConsumer(allocator, renderType)
            : new PBRVertexConsumer(allocator, renderType);
    }

    public static boolean isLockLayer(RenderType type) {
        return "simulated:lock".equals(type.name);
    }

    public static boolean isSpringLayer(RenderType renderType) {
        if (!(renderType instanceof RenderType.CompositeRenderType compositeRenderType)
            || !"spring".equals(renderType.name)) {
            return false;
        }

        ResourceLocation texture = compositeRenderType.state.textureState.cutoutTexture()
            .orElse(null);
        return texture != null
            && "simulated".equals(texture.getNamespace())
            && texture.getPath().startsWith("textures/block/spring/");
    }

    private static final class LockPBRVertexConsumer extends PBRVertexConsumer {
        private LockPBRVertexConsumer(ByteBufferBuilder allocator, RenderType renderType) {
            super(allocator, renderType, ALPHA_MODE_CUTOUT_LOW);
        }
    }

    private static final class SpringPBRVertexConsumer extends PBRVertexConsumer {

        private SpringPBRVertexConsumer(ByteBufferBuilder allocator, RenderType renderType) {
            // Simulated's shader discards spring texels below 0.1 alpha. Radiance native's regular
            // cutout mode uses Minecraft's 0.5 threshold, which removes most of the thin
            // diagonal coil texture and leaves the spring visibly fragmented.
            super(allocator, renderType, ALPHA_MODE_CUTOUT_LOW);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int stress) {
            // Simulated's spring shader uses COLOR.a as a red stress-overlay factor, not
            // surface opacity. Preserve that distinction for Radiance native's material evaluation.
            return super.setColorMix(red, green, blue, stress);
        }
    }
}
