package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileCaptureControlTest {
    @TempDir Path directory;

    @Test
    void consumesEachRequestIdentityOnceAndKeepsTheSceneLabel() throws IOException {
        Path file = directory.resolve("request.properties");
        assertNull(ProfileCaptureControl.read(file, "", 100));
        Files.writeString(file, "id=first\nseconds=60\nlabel=many-lights\nrequestedAt=200\n");
        assertEquals(
                new ProfileCaptureControl.Request("first", 60, "many-lights"),
                ProfileCaptureControl.read(file, "", 100));
        assertNull(ProfileCaptureControl.read(file, "first", 100));
        assertNull(ProfileCaptureControl.read(file, "", 201));
        Files.writeString(file, "id=second\nseconds=30\nlabel=translucency\nrequestedAt=300\n");
        assertEquals("translucency", ProfileCaptureControl.read(file, "first", 100).label());
    }

    @Test
    void rejectsInvalidDurationsLabelsAndOversizedRequests() throws IOException {
        Path file = directory.resolve("request.properties");
        for (String seconds : new String[] {"0", "121", "-1", "no"}) {
            Files.writeString(
                    file, "id=test\nseconds=" + seconds + "\nlabel=scene\nrequestedAt=200\n");
            assertThrows(IOException.class, () -> ProfileCaptureControl.read(file, "", 100));
        }
        Files.writeString(file, "id=test\nseconds=30\nlabel=../other\nrequestedAt=200\n");
        assertThrows(IOException.class, () -> ProfileCaptureControl.read(file, "", 100));
        Files.writeString(file, "x".repeat(1025));
        assertThrows(IOException.class, () -> ProfileCaptureControl.read(file, "", 100));
    }
}
