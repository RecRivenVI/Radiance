package com.radiance.audit;

import java.util.function.LongSupplier;

/** A render-thread tree, not the sum of overlapping inclusive method timers. */
public final class FrameTimings {
    public enum Stage { FRAME_OTHER, TICK, GAME_RENDER_OTHER, WORLD_RENDER_OTHER,
        ENTITIES, BLOCK_ENTITIES, PARTICLES, HAND, WEATHER, DEBUG_GEOMETRY,
        GEOMETRY_MARSHAL, CHUNK_SCHEDULE, AUX_TEXTURE, SUBMIT_PRESENT, ACQUIRE, CHUNK_COMPILE,
        LEVEL_SETUP, LIGHT_UPDATES, FLYWHEEL_BEGIN, FLYWHEEL_DRAW, SABLE_UPDATE,
        SABLE_SINGLE_BLOCKS, SABLE_BLOCK_ENTITIES, RENDER_STAGE_CALLBACKS, CLOUDS,
        TEXTURE_MAPPING, WORLD_MESH_CLOSE, LEVEL_RENDER_OTHER, CAMERA_PICK, WORLD_UNIFORM,
        GEOMETRY_FACE_STATE, GEOMETRY_ALLOCATE, GEOMETRY_FREE, GEOMETRY_CLOSE,
        FACE_SETUP, FACE_CLEAR, FACE_BACKUP, FACE_RESTORE }
    public final long[] inclusive = new long[Stage.values().length];
    public final long[] self = new long[Stage.values().length];
    private final LongSupplier clock;
    private Span top;
    public FrameTimings(LongSupplier clock) { this.clock = clock; }
    public Span enter(Stage stage) { return new Span(stage); }
    public final class Span implements AutoCloseable {
        private final Stage stage;
        private final Span parent;
        private final long start;
        private long children;
        private boolean closed;
        private Span(Stage stage) {
            this.stage = stage; this.parent = top; this.start = clock.getAsLong(); top = this;
        }
        @Override public void close() {
            if (closed) return;
            if (top != this) throw new IllegalStateException("Unbalanced profile scope");
            closed = true;
            long elapsed = Math.max(0, clock.getAsLong() - start);
            inclusive[stage.ordinal()] += elapsed;
            self[stage.ordinal()] += Math.max(0, elapsed - children);
            top = parent;
            if (parent != null) parent.children += elapsed;
        }
    }
}
