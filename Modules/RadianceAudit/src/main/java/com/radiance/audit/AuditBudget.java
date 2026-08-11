package com.radiance.audit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Capacity accounting is separate from rendering; overflow makes evidence incomplete. */
final class AuditBudget {
    private final int maxIntents;
    private final long maxBytes;
    private final AtomicInteger open = new AtomicInteger();
    private final AtomicLong bytes = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    AuditBudget(int maxIntents, long maxBytes) { this.maxIntents = maxIntents; this.maxBytes = maxBytes; }
    boolean acquireIntent() {
        for (;;) {
            int current = open.get();
            if (current >= maxIntents) { dropped.incrementAndGet(); return false; }
            if (open.compareAndSet(current, current + 1)) return true;
        }
    }
    void releaseIntent() { open.decrementAndGet(); }
    boolean reserveBytes(long count) {
        for (;;) {
            long current = bytes.get();
            if (count < 0 || count > maxBytes - current) return false;
            if (bytes.compareAndSet(current, current + count)) return true;
        }
    }
    long droppedIntents() { return dropped.get(); }
}
