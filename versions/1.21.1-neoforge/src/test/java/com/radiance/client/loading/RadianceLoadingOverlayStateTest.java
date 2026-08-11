package com.radiance.client.loading;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RadianceLoadingOverlayStateTest {
    @Test
    void nestedScreenKeepsItsOwnFadeIncludingTheOverlayRemovalFrame() {
        for (float timer : new float[] {1.0F, 1.25F, 1.5F, 1.75F, 2.0F, 2.25F}) {
            float ownAlpha = Math.min(timer - 1.0F, 1.0F);
            float[] shaderAlpha = {Math.max(2.0F - timer, 0.0F)};
            List<Float> screenDraws = new ArrayList<>();
            RadianceLoadingOverlay.renderBackground(
                    timer,
                    () -> shaderAlpha[0] = 1.0F,
                    () -> screenDraws.add(ownAlpha * shaderAlpha[0]),
                    () -> fail("A fading overlay must draw the actual underlying screen"));
            assertEquals(List.of(ownAlpha), screenDraws, "timer=" + timer);
        }
    }

    @Test
    void loadingBeforeTheFadeOnlyClearsItsBackground() {
        for (float timer : new float[] {-1.0F, 0.0F, 0.999F}) {
            List<String> events = new ArrayList<>();
            RadianceLoadingOverlay.renderBackground(
                    timer,
                    () -> events.add("neutral"),
                    () -> events.add("screen"),
                    () -> events.add("clear"));
            assertEquals(List.of("clear"), events);
        }
    }

    @Test
    void nestedScreenFailureStillRestoresTheOuterOverlayState() {
        List<String> events = new ArrayList<>();
        var failure = new IllegalStateException("screen render failed");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RadianceLoadingOverlay.renderWithStateReset(
                                        () ->
                                                RadianceLoadingOverlay.renderBackground(
                                                        1.5F,
                                                        () -> events.add("neutral"),
                                                        () -> {
                                                            events.add("screen");
                                                            throw failure;
                                                        },
                                                        () -> fail("Unexpected clear")),
                                        () -> events.add("reset"))));
        assertEquals(List.of("neutral", "screen", "reset"), events);
    }

    @Test
    void bothOrdinaryAndEarlyReturnFramesResetBeforeTheNextDraw() {
        for (boolean earlyReturn : new boolean[] {false, true}) {
            List<String> events = new ArrayList<>();
            RadianceLoadingOverlay.renderWithStateReset(
                    () -> {
                        events.add("overlay");
                        if (earlyReturn) return;
                        events.add("reload-completion");
                    },
                    () -> events.add("default-blend/disabled/white"));
            events.add("first-button");
            assertEquals("default-blend/disabled/white", events.get(events.size() - 2));
            assertEquals(earlyReturn ? 3 : 4, events.size());
        }
    }

    @Test
    void cleanupCannotReplaceTheOriginalRenderingFailure() {
        var original = new IllegalStateException("first render error");
        var cleanup = new IllegalStateException("renderer already stopped");
        assertSame(
                original,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RadianceLoadingOverlay.renderWithStateReset(
                                        () -> {
                                            throw original;
                                        },
                                        () -> {
                                            throw cleanup;
                                        })));
        assertArrayEquals(new Throwable[] {cleanup}, original.getSuppressed());
    }

    @Test
    void resetRunsOnceOnFailureAndItsOwnFailureIsNotHidden() {
        int[] resets = {0};
        assertThrows(
                AssertionError.class,
                () ->
                        RadianceLoadingOverlay.renderWithStateReset(
                                () -> {
                                    throw new AssertionError("render");
                                },
                                () -> resets[0]++));
        assertEquals(1, resets[0]);
        assertThrows(
                IllegalStateException.class,
                () ->
                        RadianceLoadingOverlay.renderWithStateReset(
                                () -> {},
                                () -> {
                                    throw new IllegalStateException("reset");
                                }));
    }
}
