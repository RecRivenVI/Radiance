package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SectionUpdatePolicyTest {
    @Test
    void coalescesBurstsButInteractionNeverWaitsAndBackgroundHasRealTimeBound() {
        long start = 10_000_000_000L;
        assertFalse(SectionUpdatePolicy.settled(start, start, false, start + 24_000_000));
        assertTrue(SectionUpdatePolicy.settled(start, start, false, start + 25_000_000));
        assertTrue(SectionUpdatePolicy.settled(start, start, true, start));
        for (int ms = 1; ms < 100; ms++)
            assertFalse(
                    SectionUpdatePolicy.settled(
                            start, start + ms * 1_000_000L, false, start + ms * 1_000_000L));
        assertTrue(
                SectionUpdatePolicy.settled(
                        start, start + 100_000_000, false, start + 100_000_000));
    }

    @Test
    void accountsForAllNativeStagesAndReservesInteractionWithoutUnboundedBypass() {
        long smallBacklog = 32;
        assertTrue(SectionUpdatePolicy.canPrepare(54, smallBacklog, false));
        assertFalse(SectionUpdatePolicy.canPrepare(54, 70, false));
        assertTrue(SectionUpdatePolicy.canPrepare(54, 70, true));
        assertFalse(SectionUpdatePolicy.canPrepare(54, 74, true));
        long fullBytes = SectionUpdatePolicy.INPUT_BUDGET << 32;
        assertFalse(SectionUpdatePolicy.canPrepare(0, fullBytes, false));
        assertTrue(SectionUpdatePolicy.canPrepare(0, fullBytes, true));
        assertFalse(SectionUpdatePolicy.canPrepare(0, fullBytes | 128, true));
        assertTrue(SectionUpdatePolicy.canPrepare(0, 0, false));
    }
}
