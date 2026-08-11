package com.radiance.render.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SourceColorTest {
    @Test
    void decodesTheByteTheSourceActuallyWrites() {
        for (int integer = -1024; integer <= 66000; integer++) {
            assertEquals(Byte.toUnsignedInt((byte) integer) / 255.0F, SourceColor.channel(integer));
        }
    }

    @Test
    void directFloatAndCapturedIntegerHaveTheSameMeaning() {
        for (float value : new float[] {-1, 0, 0.37F, 1, 31, 102, 204, 255}) {
            assertEquals(SourceColor.channel((int) (value * 255)), SourceColor.channel(value));
        }
        assertEquals(52 / 255.0F, SourceColor.channel(204.0F));
        assertEquals(225 / 255.0F, SourceColor.channel(31.0F));
        assertEquals(154 / 255.0F, SourceColor.channel(102.0F));
    }
}
