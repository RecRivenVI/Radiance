package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GlowItemFrameEmissionTest {

    @Test
    void preservesTheThreeVanillaGlowFrameBrightnessLevels() {
        assertEquals(5.0F / 15.0F, GlowItemFrameEmission.FRAME);
        assertEquals(210.0F / 240.0F,
            GlowItemFrameEmission.fromPackedLight(0x00F000D2));
        assertEquals(1.0F, GlowItemFrameEmission.fromPackedLight(0x00F000F0));
    }

    @Test
    void ignoresSkyLightWhenDerivingSemanticEmission() {
        assertEquals(0.0F, GlowItemFrameEmission.fromPackedLight(0x00F00000));
    }
}
