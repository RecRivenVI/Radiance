package com.radiance.audit;

import java.util.concurrent.atomic.AtomicLongArray;

/** Bounded counters only: no section identities, vertices, ledger or per-event disk writes. */
public final class FlowProfileCounters {
    private static final String[] EVENTS = {
        "LOADED_COLUMN",
        "DIRTY_SOURCE",
        "DIRTY_NEIGHBOR",
        "DIRTY_INTERACTIVE",
        "ENQUEUED",
        "COALESCED_RUNNING",
        "COALESCED_QUEUED",
        "NEIGHBOR_REFRESH",
        "COLUMN_ENQUEUED",
        "COLUMN_SCANNED",
        "EMPTY",
        "COMPILED",
        "STALE_DISCARDED",
        "PIPELINE_DEFERRED",
        "SOURCE_WAIT",
        "NATIVE_SUBMITTED",
        "PUBLISHED"
    };
    private static final AtomicLongArray counts = new AtomicLongArray(EVENTS.length);
    private static final ThreadLocal<Integer> neighbors = ThreadLocal.withInitial(() -> 0);

    private FlowProfileCounters() {}

    public static void neighbor(boolean entering) {
        if (entering) neighbors.set(neighbors.get() + 1);
        else if (neighbors.get() == 1) neighbors.remove();
        else neighbors.set(neighbors.get() - 1);
    }

    public static void dirty(boolean interactive) {
        event(
                interactive
                        ? "DIRTY_INTERACTIVE"
                        : neighbors.get() > 0 ? "DIRTY_NEIGHBOR" : "DIRTY_SOURCE",
                1);
    }

    public static void event(String name, long amount) {
        if (!FrameProfiler.flowAuditEnabled()) return;
        for (int i = 0; i < EVENTS.length; i++) {
            if (EVENTS[i].equals(name)) {
                counts.addAndGet(i, amount);
                return;
            }
        }
    }

    static void reset() {
        for (int i = 0; i < EVENTS.length; i++) counts.set(i, 0);
    }

    static void drain(StringBuilder rows, long frame) {
        for (int i = 0; i < EVENTS.length; i++) {
            long count = counts.getAndSet(i, 0);
            if (count != 0)
                rows.append(frame)
                        .append(",5,java.chunk.")
                        .append(EVENTS[i])
                        .append(',')
                        .append(count)
                        .append(",0\n");
        }
    }
}
