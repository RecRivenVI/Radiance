package com.radiance.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Completes the ordinary production-frame readback before a bounded fixture exits. */
final class NativeSceneReplayProbe {
    private static long requested;

    private NativeSceneReplayProbe() {}

    static boolean ready() {
        String capture = System.getenv("RADIANCE_SCENE_CAPTURE");
        if (capture == null || capture.isBlank()) return true;
        Path root = Path.of(capture);
        try {
            if (Files.exists(root.resolve("capture-error.txt")))
                throw new IllegalStateException(
                        Files.readString(root.resolve("capture-error.txt")));
            if (Files.exists(root.resolve("scene.json"))) return true;
            if (requested == 0) {
                requested = System.nanoTime();
                Files.writeString(root.resolve("capture.request"), "fixture-completed\n");
            }
            if (System.nanoTime() - requested > 150_000_000_000L)
                throw new IllegalStateException("Native scene readback did not publish a snapshot");
            return false;
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
