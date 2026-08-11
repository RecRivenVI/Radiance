package com.radiance.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ARGBTest {

    @Test
    void packsAndExtractsAllChannels() {
        int color = ARGB.color(0x12, 0x34, 0x56, 0x78);

        assertEquals(0x12, ARGB.alpha(color));
        assertEquals(0x34, ARGB.red(color));
        assertEquals(0x56, ARGB.green(color));
        assertEquals(0x78, ARGB.blue(color));
    }

    @Test
    void exposesNormalizedChannelValues() {
        int color = ARGB.color(255, 128, 64, 0);

        assertEquals(1.0F, ARGB.alphaFloat(color));
        assertEquals(128.0F / 255.0F, ARGB.redFloat(color));
        assertEquals(64.0F / 255.0F, ARGB.greenFloat(color));
        assertEquals(0.0F, ARGB.blueFloat(color));
    }
}
