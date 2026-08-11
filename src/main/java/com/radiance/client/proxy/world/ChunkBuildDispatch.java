package com.radiance.client.proxy.world;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Actual preparation/admission boundary used by the primary section scheduler.
 */
final class ChunkBuildDispatch {
    static Runnable currentWork(java.util.function.BooleanSupplier current, Runnable work) {
        return () -> { if (current.getAsBoolean()) work.run(); };
    }
    enum Result { SATURATED, DEFERRED, SUBMITTED }
    static Result submit(AtomicBoolean running, AtomicInteger capacity, int limit,
        Supplier<Runnable> prepare, Executor executor, Runnable completed) {
        if (!running.compareAndSet(false, true))
            return Result.SATURATED;
        ChunkBuildPermit permit = ChunkBuildPermit.tryAcquire(capacity, limit);
        if (permit == null) {
            running.set(false);
            return Result.SATURATED;
        }
        boolean submitted = false;
        try {
            Runnable work = prepare.get();
            if (work == null)
                return Result.DEFERRED;
            executor.execute(() -> {
                try {
                    work.run();
                } finally {
                    permit.close();
                    running.set(false);
                    completed.run();
                }
            });
            submitted = true;
            return Result.SUBMITTED;
        } finally {
            if (!submitted) {
                permit.close();
                running.set(false);
            }
        }
    }

    /**
     * Capacity-blocked items are returned only after the round; they cannot
     * monopolize it.
     */
    static final class Round<T> implements AutoCloseable {
        private final Queue<T> queue;
        private final List<T> deferred = new ArrayList<>();
        private int remaining;
        Round(Queue<T> queue, int maximum) {
            this.queue = queue;
            remaining = Math.min(queue.size(), maximum);
        }
        T poll() {
            return remaining-- > 0 ? queue.poll() : null;
        }
        T pollPreferred(java.util.function.Predicate<T> eligible, Comparator<T> age) {
            if (remaining <= 0)
                return null;
            T chosen = queue.stream().filter(eligible).min(age).orElse(null);
            if (chosen != null && queue.remove(chosen)) {
                remaining--;
                return chosen;
            }
            return poll();
        }
        void defer(T item) {
            deferred.add(item);
        }
        @Override
        public void close() {
            queue.addAll(deferred);
            deferred.clear();
        }
    }
}
