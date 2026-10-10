package com.radiance.client.proxy.world;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** A capacity reservation that can be transferred to a submitted chunk build task. */
final class ChunkBuildPermit implements AutoCloseable {
    private final AtomicInteger inFlight;
    private final AtomicBoolean released = new AtomicBoolean();

    private ChunkBuildPermit(AtomicInteger inFlight) {
        this.inFlight = inFlight;
    }

    static ChunkBuildPermit tryAcquire(AtomicInteger inFlight, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Chunk build limit must be positive");
        while (true) {
            int current = inFlight.get();
            if (current >= limit) return null;
            if (inFlight.compareAndSet(current, current + 1)) {
                return new ChunkBuildPermit(inFlight);
            }
        }
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) inFlight.decrementAndGet();
    }
}
