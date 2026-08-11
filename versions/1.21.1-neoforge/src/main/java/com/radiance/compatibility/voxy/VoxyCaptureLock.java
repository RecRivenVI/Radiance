package com.radiance.compatibility.voxy;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Serializes source mutations with short array snapshots, never with mesh generation or GPU work.
 */
public final class VoxyCaptureLock {
    public static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static volatile long revision;

    private VoxyCaptureLock() {}

    public static long revision() {
        return revision;
    }

    public static void changed() {
        if (!LOCK.isWriteLockedByCurrentThread())
            throw new IllegalStateException("Unowned Voxy mutation");
        revision++;
    }

    public static long[] copy(long[] data) {
        LOCK.readLock().lock();
        try {
            return data.clone();
        } finally {
            LOCK.readLock().unlock();
        }
    }
}
