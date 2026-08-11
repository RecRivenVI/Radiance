package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.radiance.compatibility.sable.RadianceSableStagingBuffer;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class RadianceVertexArrayTest {

    @Test
    void convertsSableUnsignedByteIndicesToVulkanUint16WithoutChangingOrder() {
        ByteBuffer bytes = MemoryUtil.memAlloc(6);
        ByteBuffer converted = null;
        try {
            bytes.put(new byte[]{0, 1, 2, 2, 3, 0}).flip();
            converted = RadianceVertexArray.convertUnsignedByteIndices(bytes);
            short[] actual = new short[6];
            converted.asShortBuffer().get(actual);
            assertArrayEquals(new short[]{0, 1, 2, 2, 3, 0}, actual);
        } finally {
            if (converted != null) MemoryUtil.memFree(converted);
            MemoryUtil.memFree(bytes);
        }
    }

    @Test
    void sableStagingReservationsAreFourByteAlignedAndMustBeCopied() {
        RadianceSableStagingBuffer staging = new RadianceSableStagingBuffer(32);
        try {
            long first = staging.reserve(3);
            long second = staging.reserve(4);
            assertEquals(4, second - first);
            assertEquals(8, staging.getUsedSize());
            assertThrows(IllegalStateException.class, staging::updateFencedAreas);
        } finally {
            staging.free();
        }
    }
}
