package com.radiance.adapter.vanilla;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VanillaEnvironmentTest {
    @Test
    void textureAndBiomeColorsAreConvertedToLinearBeforeAveraging() {
        assertEquals(0, VanillaEnvironment.linear(0));
        assertEquals(1, VanillaEnvironment.linear(1));
        assertEquals(.21404114, VanillaEnvironment.linear(.5), 1e-7);
        double blackWhiteAverage =
                (VanillaEnvironment.linear(0) + VanillaEnvironment.linear(1)) * .5;
        assertEquals(.5, blackWhiteAverage);
        assertNotEquals(VanillaEnvironment.linear(.5), blackWhiteAverage, .01);
    }
}
