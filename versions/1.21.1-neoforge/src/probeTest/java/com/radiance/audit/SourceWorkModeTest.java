package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SourceWorkModeTest {
    @Test
    void frameOnlyExcludesNewSourceCountersWithoutDisablingFunctionalEvidence() throws Exception {
        var active = FrameProfiler.class.getDeclaredField("active");
        active.setAccessible(true);
        var mode = FrameProfiler.class.getDeclaredField("mode");
        mode.setAccessible(true);
        Object previousActive = active.get(null), previousMode = mode.get(null);
        try {
            active.set(null, true);
            mode.set(null, ProfileMode.FRAME_ONLY);
            assertFalse(FrameProfiler.sourceWorkEnabled());
            mode.set(null, ProfileMode.TIMED);
            assertTrue(FrameProfiler.sourceWorkEnabled());
            active.set(null, false);
            mode.set(null, ProfileMode.FRAME_ONLY);
            assertTrue(FrameProfiler.sourceWorkEnabled());
        } finally {
            active.set(null, previousActive);
            mode.set(null, previousMode);
        }
    }
}
