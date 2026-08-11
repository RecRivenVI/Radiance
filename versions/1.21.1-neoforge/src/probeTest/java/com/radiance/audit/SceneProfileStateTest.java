package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class SceneProfileStateTest {
    @Test
    void preservesCameraAndHorizonIndependentlyUsingLocaleIndependentColumns() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            var state =
                    new SceneProfileState(
                            "minecraft:overworld",
                            23.519,
                            -66.06624,
                            -1.006,
                            86,
                            -16.5f,
                            -66.06624,
                            -64,
                            -64,
                            320,
                            42,
                            6000,
                            0,
                            0);
            String[] values = state.csv().split(",");
            assertEquals(SceneProfileState.HEADER.split(",").length, values.length);
            assertEquals("-66.066240000", values[2]);
            assertEquals("-64.000000000", values[7]);
            assertEquals("-64", values[8]);
            assertEquals("320", values[9]);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void absenceIsExplicitAndDimensionCannotInjectColumns() {
        assertTrue(SceneProfileState.absent().csv().startsWith("none,NaN,NaN,NaN,"));
        assertEquals(14, SceneProfileState.absent().csv().split(",").length);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SceneProfileState("bad,key", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }
}
