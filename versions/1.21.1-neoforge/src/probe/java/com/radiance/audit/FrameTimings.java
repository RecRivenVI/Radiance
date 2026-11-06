package com.radiance.audit;

import java.util.IdentityHashMap;
import java.util.function.LongSupplier;
import net.minecraft.world.entity.Entity;

/** A render-thread tree, not the sum of overlapping inclusive method timers. */
public final class FrameTimings {
    public enum Stage {
        FRAME_OTHER,
        TICK,
        GAME_RENDER_OTHER,
        WORLD_RENDER_OTHER,
        WORLD_LIGHT_TEXTURE,
        WORLD_CAMERA_SETUP,
        WORLD_CAMERA_FOV,
        WORLD_OUTLINE_CHECK,
        WORLD_FRUSTUM_SETUP,
        WORLD_SCENE_BEGIN,
        WORLD_AUDIT_OUTCOMES,
        WORLD_AFTER_RENDER,
        WORLD_SOURCE_UPDATE,
        WORLD_SOURCE_BEGIN,
        WORLD_SOURCE_BLOCK_ENTITIES,
        WORLD_BLOCK_ENTITY_PUBLICATION,
        WORLD_BLOCK_ENTITY_RESIDENT_SUBMIT,
        WORLD_SOURCE_EFFECTS,
        ENTITIES,
        BLOCK_ENTITIES,
        BLOCK_ENTITY_STABLE_INPUTS,
        BLOCK_ENTITY_SELECT,
        PARTICLES,
        HAND,
        WEATHER,
        DEBUG_GEOMETRY,
        GEOMETRY_MARSHAL,
        CHUNK_SCHEDULE,
        AUX_TEXTURE,
        SUBMIT_PRESENT,
        ACQUIRE,
        CHUNK_COMPILE,
        LEVEL_SETUP,
        LIGHT_QUEUE,
        LIGHT_PACKET_APPLY,
        LIGHT_CHUNK_ENABLE,
        LIGHT_PROPAGATION,
        LIGHT_SECTION_NOTIFY,
        FLYWHEEL_BEGIN,
        FLYWHEEL_DRAW,
        SABLE_UPDATE,
        SABLE_SINGLE_BLOCKS,
        SABLE_BLOCK_ENTITIES,
        RENDER_STAGE_CALLBACKS,
        CLOUDS,
        TEXTURE_MAPPING,
        WORLD_MESH_CLOSE,
        LEVEL_RENDER_OTHER,
        CAMERA_PICK,
        WORLD_UNIFORM,
        GEOMETRY_FACE_STATE,
        GEOMETRY_ALLOCATE,
        GEOMETRY_FREE,
        GEOMETRY_CLOSE,
        FACE_SETUP,
        FACE_CLEAR,
        FACE_BACKUP,
        FACE_RESTORE,
        ENTITY_SOURCE_STATE,
        ENTITY_POSE_EXTRACT,
        ENTITY_PART_BINDINGS,
        ENTITY_BATCH_PACK,
        ENTITY_OUTPUT,
        ENTITY_SOURCE_LOOKUP
    }

    public final long[] inclusive = new long[Stage.values().length];
    public final long[] self = new long[Stage.values().length];
    public long generatedPbrVertices, generatedPbrBytes;
    public long optionalSourceLookups;
    public long lightQueueBefore, lightQueueAfter, lightQueueDrained;
    public long lightPackets, lightChunks, lightNotifies, lightPropagationNodes;
    public long blockEntityVisits, blockEntityStable, blockEntityCapture, entities;
    public long[] blockEntityRest;
    public final IdentityHashMap<Entity, Boolean> persistentEntityMatches = new IdentityHashMap<>();
    private final LongSupplier clock;
    private Span top;

    public FrameTimings(LongSupplier clock) {
        this.clock = clock;
    }

    public Span enter(Stage stage) {
        return new Span(stage);
    }

    public final class Span implements AutoCloseable {
        private final Stage stage;
        private final Span parent;
        private final long start;
        private long children;
        private boolean closed;

        private Span(Stage stage) {
            this.stage = stage;
            this.parent = top;
            this.start = clock.getAsLong();
            top = this;
        }

        @Override
        public void close() {
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
