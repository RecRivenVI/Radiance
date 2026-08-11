package com.radiance.client.render;

import com.radiance.render.contract.LineContract.SourceKey;
import java.util.Objects;

/** Explicit registrations of capture entry points; never derived from an object's class name. */
public final class LineSources {
    public static final SourceKey ENTITY = new SourceKey(1, "vanilla/entity_capture");
    public static final SourceKey BLOCK_ENTITY = new SourceKey(2, "vanilla/block_entity_capture");
    public static final SourceKey SABLE = new SourceKey(3, "sable/sublevel_capture");
    public static final SourceKey SIMULATED_LEVER = new SourceKey(4, "simulated/lever_outline");
    public static final SourceKey SIMULATED_ROPE = new SourceKey(5, "simulated/rope_outline");
    public static final SourceKey NEOFORGE_EVENT = new SourceKey(6, "neoforge/world_event");
    public static final SourceKey VEIL_EVENT = new SourceKey(7, "veil/world_event");
    public static final SourceKey CREATE_HIGHLIGHT = new SourceKey(8, "create/highlight_capture");
    public static final SourceKey CREATE_EVENT = new SourceKey(9, "create/world_event");
    public static final SourceKey DEBUG = new SourceKey(10, "vanilla/debug_capture");
    public static final SourceKey MODEL_OUTLINE = new SourceKey(11, "vanilla/model_outline_edges");
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private LineSources() {}

    public static com.radiance.render.contract.LineContract.WidthPolicy eventWidth(
            SourceKey source) {
        // Event selection/bounds tools use the existing world-debug physical scale.
        // Ordinary entity/BER producers keep their explicitly supplied ordinary scale.
        return source.equals(NEOFORGE_EVENT)
                        || source.equals(VEIL_EVENT)
                        || source.equals(CREATE_EVENT)
                        || source.equals(CREATE_HIGHLIGHT)
                        || source.equals(DEBUG)
                        || source.equals(MODEL_OUTLINE)
                ? com.radiance.render.contract.LineContract.WidthPolicy.DEBUG
                : com.radiance.render.contract.LineContract.WidthPolicy.ORDINARY;
    }

    public static SourceKey current(SourceKey fallback) {
        Scope current = CURRENT.get();
        return current == null ? fallback : current.entered;
    }

    public static Scope enter(SourceKey source) {
        Scope scope = new Scope(CURRENT.get(), Objects.requireNonNull(source));
        CURRENT.set(scope);
        return scope;
    }

    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final SourceKey entered;
        private final Thread owner = Thread.currentThread();
        private boolean closed;

        private Scope(Scope previous, SourceKey entered) {
            this.previous = previous;
            this.entered = entered;
        }

        @Override
        public void close() {
            if (closed) return;
            if (Thread.currentThread() != owner)
                throw new IllegalStateException("Foreign line source scope");
            if (CURRENT.get() != this)
                throw new IllegalStateException("Line sources must close in LIFO order");
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
            closed = true;
        }
    }
}
