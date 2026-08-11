package com.radiance.compatibility.veil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class VeilScreenEffectAdapterTest {
    private static final float EPSILON = 1.0e-6F;

    @Test
    void preservesTheUniformsWrittenByTheActualVeilCall() {
        var parameters = VeilScreenEffectAdapter.ScreenQuadParameters.from(
            new float[] {1.25F, -0.5F, 2.0F, 1.125F},
            new float[] {0.7F, 0.7F, 0.7F, 0.1F});

        assertEquals(1.25F, parameters.uStart(), EPSILON);
        assertEquals(-0.5F, parameters.vStart(), EPSILON);
        assertEquals(2.0F, parameters.uSpan(), EPSILON);
        assertEquals(1.125F, parameters.vSpan(), EPSILON);
        assertEquals(0.1F, parameters.alpha(), EPSILON);
        assertEquals(0.7F, parameters.brightness(), EPSILON);
    }

    @Test
    void rejectsIncompleteUniformSnapshots() {
        assertThrows(IllegalArgumentException.class,
            () -> VeilScreenEffectAdapter.ScreenQuadParameters.from(
                new float[] {0.0F, 0.0F}, new float[4]));
    }

    @Test
    void readsVeilValuesFromTheCpuShadowInsteadOfOpenGl() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(4 * Float.BYTES)
            .order(ByteOrder.nativeOrder());
        bytes.putFloat(0, 1.25F);
        bytes.putFloat(Float.BYTES, -0.5F);
        bytes.putFloat(2 * Float.BYTES, 2.0F);
        bytes.putFloat(3 * Float.BYTES, 0.1F);

        float[] values = VeilScreenEffectAdapter.readVec4(() -> bytes.duplicate());

        assertEquals(1.25F, values[0], EPSILON);
        assertEquals(-0.5F, values[1], EPSILON);
        assertEquals(2.0F, values[2], EPSILON);
        assertEquals(0.1F, values[3], EPSILON);
    }

    @Test
    void rejectsAnIncompleteCpuUniformShadow() {
        assertNull(VeilScreenEffectAdapter.readVec4(
            () -> ByteBuffer.allocate(3 * Float.BYTES)));
    }
}
