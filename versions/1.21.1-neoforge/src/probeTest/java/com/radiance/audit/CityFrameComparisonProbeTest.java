package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class CityFrameComparisonProbeTest {
    @Test
    void profilingPlansSeparateCalibrationFromHeavyAttribution() {
        assertArrayEquals(new ProfileMode[0], CityFrameComparisonProbe.captureModes("functional"));
        assertArrayEquals(
                new ProfileMode[] {ProfileMode.FRAME_ONLY},
                CityFrameComparisonProbe.captureModes("throughput"));
        assertArrayEquals(
                new ProfileMode[] {ProfileMode.TIMED},
                CityFrameComparisonProbe.captureModes("standard"));
        assertArrayEquals(
                new ProfileMode[] {ProfileMode.FRAME_ONLY, ProfileMode.TIMED},
                CityFrameComparisonProbe.captureModes("takeover"));
        assertArrayEquals(
                new ProfileMode[] {ProfileMode.FRAME_ONLY, ProfileMode.TIMED, ProfileMode.JFR_ONLY},
                CityFrameComparisonProbe.captureModes("vanilla"));
        assertEquals(0, ProfileMode.JFR_ONLY.nativeFlags);
        assertFalse(ProfileMode.JFR_ONLY.captures(FrameTimings.Stage.ENTITIES));
        assertArrayEquals(
                new ProfileMode[] {ProfileMode.PRODUCERS},
                CityFrameComparisonProbe.captureModes("producers"));
        var calibration = CityFrameComparisonProbe.captureModes("calibration");
        assertEquals(6, calibration.length);
        assertEquals(
                3,
                java.util.Arrays.stream(calibration)
                        .filter(m -> m == ProfileMode.FRAME_ONLY)
                        .count());
        assertFalse(ProfileMode.FRAME_ONLY.captures(FrameTimings.Stage.FACE_RESTORE));
        assertTrue(ProfileMode.FRAME_ONLY.captures(FrameTimings.Stage.FRAME_OTHER));
        assertEquals(1, ProfileMode.TIMED.nativeFlags);
        assertEquals(7, ProfileMode.CONTENT_AUDIT.nativeFlags);
        assertTrue(ProfileMode.PRODUCERS.producers);
        assertThrows(
                IllegalArgumentException.class,
                () -> CityFrameComparisonProbe.captureModes("unknown"));
    }

    private static Properties empty() {
        var state = new Properties();
        for (String key :
                new String[] {
                    "columnQueue", "rebuildQueue", "importantBuildsInFlight", "normalBuildsInFlight"
                }) state.setProperty("chunk.java." + key, "0");
        state.setProperty("chunk.java.initialSeedPending", "false");
        state.setProperty(
                "chunk.native", "queued_builds=0; pending_batch_builds=0; active_gpu_batches=0");
        return state;
    }

    @Test
    void requiresAllJavaAndNativeWorkToDrain() {
        assertTrue(CityFrameComparisonProbe.quiet(empty()));
        for (String key :
                new String[] {
                    "columnQueue", "rebuildQueue", "importantBuildsInFlight", "normalBuildsInFlight"
                }) {
            var state = empty();
            state.setProperty("chunk.java." + key, "1");
            assertFalse(CityFrameComparisonProbe.quiet(state), key);
        }
        var state = empty();
        state.setProperty(
                "chunk.native", "queued_builds=0; pending_batch_builds=0; active_gpu_batches=1");
        assertFalse(CityFrameComparisonProbe.quiet(state));
    }

    @Test
    void missingOrDisabledCountersNeverMeanQuiet() {
        assertFalse(CityFrameComparisonProbe.quiet(new Properties()));
        var state = empty();
        state.setProperty("chunk.native", "disabled (set RADIANCE_CHUNK_PERF=1)");
        assertFalse(CityFrameComparisonProbe.quiet(state));
        state = empty();
        state.setProperty("chunk.java.initialSeedPending", "true");
        assertFalse(CityFrameComparisonProbe.quiet(state));
    }
}
