package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LoadingChainProbeTest {
    @Test
    void frozenRouteCrossesSectionBoundariesAndReturnsWithoutSkippingWaypoints() {
        assertEquals(0.5, LoadingChainProbe.routeX(0));
        assertEquals(256.5, LoadingChainProbe.routeX(32));
        assertEquals(0.5, LoadingChainProbe.routeX(64));
        for (int step = 1; step <= 64; step++)
            assertEquals(
                    8.0,
                    Math.abs(LoadingChainProbe.routeX(step) - LoadingChainProbe.routeX(step - 1)));
        assertThrows(IllegalArgumentException.class, () -> LoadingChainProbe.routeX(65));
    }
}
