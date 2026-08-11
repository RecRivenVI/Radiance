package com.radiance.audit;

/** Observer controls only; never used to select rendering inputs or features. */
enum ProfileMode {
    FRAME_ONLY(0, false, false),
    TIMED(1, true, false),
    CONTENT_AUDIT(7, true, false),
    PRODUCERS(1, true, true),
    CPU_SAMPLES(1, true, false);

    final int nativeFlags;
    final boolean stages;
    final boolean producers;

    ProfileMode(int nativeFlags, boolean stages, boolean producers) {
        this.nativeFlags = nativeFlags;
        this.stages = stages;
        this.producers = producers;
    }

    boolean captures(FrameTimings.Stage stage) {
        return stages || stage == FrameTimings.Stage.FRAME_OTHER;
    }
}
