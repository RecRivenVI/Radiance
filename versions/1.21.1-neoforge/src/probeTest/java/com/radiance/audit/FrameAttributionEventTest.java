package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrameAttributionEventTest {
    @Test
    void frameIdentityAndDurationSurviveJfrExport(@TempDir Path directory) throws Exception {
        Path output = directory.resolve("frame.jfr");
        try (var recording = new Recording()) {
            recording.enable(FrameAttributionEvent.class).withThreshold(java.time.Duration.ZERO);
            recording.start();
            var event = new FrameAttributionEvent();
            event.frameToken = 713;
            event.mode = "CPU_SAMPLES";
            event.begin();
            event.measuredWallNs = 123456;
            event.renderThreadCpuNs = -1;
            event.end();
            event.commit();
            recording.stop();
            recording.dump(output);
        }
        var events =
                RecordingFile.readAllEvents(output).stream()
                        .filter(e -> e.getEventType().getName().equals("radiance.RenderFrame"))
                        .toList();
        assertEquals(1, events.size());
        var event = events.getFirst();
        assertEquals(713, event.getLong("frameToken"));
        assertEquals(123456, event.getLong("measuredWallNs"));
        assertEquals(-1, event.getLong("renderThreadCpuNs"));
        assertFalse(event.getStartTime().isAfter(event.getEndTime()));
        assertEquals(17, ProfileMode.CPU_SAMPLES.nativeFlags);
        assertEquals(17, ProfileMode.ALLOCATION_TIMED.nativeFlags);
        assertEquals(1, ProfileMode.TIMED.nativeFlags);
    }
}
