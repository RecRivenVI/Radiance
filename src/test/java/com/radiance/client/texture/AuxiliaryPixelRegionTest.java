package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

final class AuxiliaryPixelRegionTest {
    @Test void copiesOnlySelectedAnimationRectangleAndLeavesGuardsIntact() {
        long source = MemoryUtil.nmemAlloc(8 * 64 * 4);
        long target = MemoryUtil.nmemAlloc(16 + 3 * 2 * 4);
        try {
            for (int i = 0; i < 8 * 64; i++) MemoryUtil.memPutInt(source + i * 4L, i);
            MemoryUtil.memSet(target, 0x5a, 40);
            AuxiliaryPixelRegion.copy(target + 8, 3, 2, 4, source, 8, 64, 4, 2, 37, 0);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++)
                assertEquals((y + 37) * 8 + x + 2, MemoryUtil.memGetInt(target + 8 + (y * 3L + x) * 4));
            assertEquals(0x5a5a5a5a5a5a5a5aL, MemoryUtil.memGetLong(target));
            assertEquals(0x5a5a5a5a5a5a5a5aL, MemoryUtil.memGetLong(target + 32));
        } finally { MemoryUtil.nmemFree(source); MemoryUtil.nmemFree(target); }
    }

    @Test void missingNormalKeepsOpaqueAlphaAndDoesNotNeedSourcePixels() {
        long target = MemoryUtil.nmemAlloc(24);
        try {
            AuxiliaryPixelRegion.copy(target, 3, 2, 4, 0, 0, 0, 0, 0, 0, 0xff000000);
            for (int i = 0; i < 6; i++) assertEquals(0xff000000, MemoryUtil.memGetInt(target + i * 4L));
            AuxiliaryPixelRegion.copy(target, 3, 2, 4, 0, 0, 0, 0, 0, 0, 0);
            for (int i = 0; i < 24; i++) assertEquals(0, MemoryUtil.memGetByte(target + i));
        } finally { MemoryUtil.nmemFree(target); }
    }

    @Test void tiledAndDifferentChannelSourcesMatchPreviousFullAlignment() {
        for (int sourceChannels = 1; sourceChannels <= 4; sourceChannels++) {
            long source = MemoryUtil.nmemAlloc(2 * 3 * sourceChannels);
            long target = MemoryUtil.nmemAlloc(5 * 4 * 4);
            try {
                for (int i = 0; i < 6 * sourceChannels; i++) MemoryUtil.memPutByte(source + i, (byte) (i + 1));
                AuxiliaryPixelRegion.copy(target, 5, 4, 4, source, 2, 3, sourceChannels, 1, 5, 0);
                for (int y = 0; y < 4; y++) for (int x = 0; x < 5; x++) for (int c = 0; c < 4; c++) {
                    int expected = c < sourceChannels ? (((y + 5) % 3 * 2 + (x + 1) % 2) * sourceChannels + c + 1) : 0;
                    assertEquals(expected, Byte.toUnsignedInt(MemoryUtil.memGetByte(target + (y * 5L + x) * 4 + c)));
                }
            } finally { MemoryUtil.nmemFree(source); MemoryUtil.nmemFree(target); }
        }
    }
}
