package com.radiance.client.render;

/** Minecraft 1.21.1 GlowItemFrame lightmap semantics expressed as PBR emission. */
public final class GlowItemFrameEmission {

    public static final float FRAME = 5.0F / 15.0F;
    private static final float MAX_LIGHT_COORDINATE = 240.0F;

    private GlowItemFrameEmission() {
    }

    public static float fromPackedLight(int packedLight) {
        return Math.min(1.0F, (packedLight & 0xFFFF) / MAX_LIGHT_COORDINATE);
    }
}
