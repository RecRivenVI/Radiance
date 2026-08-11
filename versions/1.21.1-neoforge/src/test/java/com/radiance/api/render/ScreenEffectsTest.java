package com.radiance.api.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenEffectsTest {
    @Test
    void combinationsHaveRealProducerOrderingAndIndependentSizes() {
        var passes =
                List.of(
                        new ScreenEffects.Pass(ScreenEffects.Program.CREEPER, 1, List.of(0)),
                        new ScreenEffects.Pass(ScreenEffects.Program.INVERT, 2, List.of(1)));
        var plan =
                new ScreenEffects.Plan(
                        ScreenEffects.Role.GUI_POST,
                        List.of(new ScreenEffects.Size(2, 2), ScreenEffects.Size.full()),
                        passes,
                        2);
        assertEquals(2, plan.output());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScreenEffects.Plan(
                                plan.role(),
                                plan.targets(),
                                List.of(passes.get(1), passes.get(0)),
                                2));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScreenEffects.Plan(
                                plan.role(),
                                plan.targets(),
                                List.of(
                                        new ScreenEffects.Pass(
                                                ScreenEffects.Program.INVERT, 1, List.of(1))),
                                1));
    }

    @Test
    void nativeCannotReceiveMalformedProgramOrNonFiniteParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScreenEffects.SpirvProgram("probe:bad", new byte[21], 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScreenEffects.Pass(
                                ScreenEffects.Program.BLUR,
                                1,
                                List.of(0),
                                RenderResources.Sampling.LINEAR,
                                1,
                                0,
                                Float.NaN,
                                1));
    }
}
