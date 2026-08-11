package com.radiance.client.proxy.world;

/** Real-time admission limits for the complete section pipeline, not a per-frame job quota. */
final class SectionUpdatePolicy {
    static final long SETTLE_NANOS = 25_000_000L;
    static final long MAX_COALESCE_NANOS = 100_000_000L;
    static final int MAX_OUTSTANDING = 128;
    static final int INTERACTIVE_RESERVE = 4;
    static final long INPUT_BUDGET = 64L * 1024 * 1024;

    private SectionUpdatePolicy() {}

    static boolean settled(long queuedAt, long changedAt, boolean interactive, long now) {
        return interactive
                || now - queuedAt >= MAX_COALESCE_NANOS
                || now - changedAt >= SETTLE_NANOS;
    }

    static boolean canPrepare(int javaOutstanding, long nativeState, boolean interactive) {
        long count = Integer.toUnsignedLong((int) nativeState) + javaOutstanding;
        long bytes = nativeState >>> 32;
        return count < (interactive ? MAX_OUTSTANDING : MAX_OUTSTANDING - INTERACTIVE_RESERVE)
                && (interactive || bytes < INPUT_BUDGET);
    }
}
