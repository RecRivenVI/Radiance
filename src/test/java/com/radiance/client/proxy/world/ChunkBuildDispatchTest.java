package com.radiance.client.proxy.world;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
class ChunkBuildDispatchTest {
    @Test void queuedWorkerIsDiscardedAfterOwnerRevisionChangesAndCapacityReturns() {
        var generation = new AtomicInteger(7);
        var running = new AtomicBoolean();
        var capacity = new AtomicInteger();
        var compiled = new AtomicInteger();
        var completed = new AtomicInteger();
        var workers = new ArrayDeque<Runnable>();
        ChunkBuildDispatch.submit(running, capacity, 1,
            () -> ChunkBuildDispatch.currentWork(() -> generation.get() == 7, compiled::incrementAndGet),
            workers::add, completed::incrementAndGet);
        generation.incrementAndGet(); // release/reuse or a newer dependency invalidation
        workers.remove().run();
        assertEquals(0, compiled.get());
        assertEquals(0, capacity.get());
        assertFalse(running.get());
        assertEquals(1, completed.get());
    }
    @Test
    void saturatedWorkerDoesNotPrepareAndOtherLaneMakesProgress() {
        var background = new AtomicInteger();
        var interactive = new AtomicInteger();
        var workers = new ArrayDeque<Runnable>();
        var prepared = new AtomicInteger();
        var completed = new AtomicInteger();
        var running = new AtomicBoolean();
        assertEquals(ChunkBuildDispatch.Result.SUBMITTED,
            ChunkBuildDispatch.submit(running, background, 1, () -> {
                prepared.incrementAndGet();
                return () -> {};
            }, workers::add, completed::incrementAndGet));
        for (int i = 0; i < 20; i++)
            assertEquals(ChunkBuildDispatch.Result.SATURATED,
                ChunkBuildDispatch.submit(new AtomicBoolean(), background, 1, () -> {
                    prepared.incrementAndGet();
                    return () -> {};
                }, workers::add, completed::incrementAndGet));
        assertEquals(1, prepared.get());
        assertEquals(ChunkBuildDispatch.Result.SUBMITTED,
            ChunkBuildDispatch.submit(new AtomicBoolean(), interactive, 1, () -> {
                prepared.incrementAndGet();
                return () -> {};
            }, workers::add, completed::incrementAndGet));
        while (!workers.isEmpty()) workers.remove().run();
        assertEquals(0, background.get());
        assertEquals(0, interactive.get());
        assertFalse(running.get());
        assertEquals(2, completed.get());
        assertEquals(ChunkBuildDispatch.Result.SUBMITTED,
            ChunkBuildDispatch.submit(running, background, 1, () -> {
                prepared.incrementAndGet();
                return () -> {};
            }, Runnable::run, completed::incrementAndGet));
        assertEquals(3, prepared.get());
    }
    @Test
    void rejectedFailedOrUnavailablePreparationReleasesExactlyOnce() {
        var capacity = new AtomicInteger();
        var running = new AtomicBoolean();
        var completed = new AtomicInteger();
        assertThrows(RejectedExecutionException.class,
            () -> ChunkBuildDispatch.submit(running, capacity, 1, () -> () -> {}, task -> {
                throw new RejectedExecutionException();
            }, completed::incrementAndGet));
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        assertThrows(IllegalArgumentException.class,
            () -> ChunkBuildDispatch.submit(running, capacity, 1, () -> {
                throw new IllegalArgumentException();
            }, Runnable::run, completed::incrementAndGet));
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        assertEquals(ChunkBuildDispatch.Result.DEFERRED,
            ChunkBuildDispatch.submit(
                running, capacity, 1, () -> null, Runnable::run, completed::incrementAndGet));
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        var workers = new ArrayDeque<Runnable>();
        ChunkBuildDispatch.submit(running, capacity, 1, () -> () -> {
            throw new IllegalStateException();
        }, workers::add, completed::incrementAndGet);
        assertThrows(IllegalStateException.class, workers.remove()::run);
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        assertEquals(1, completed.get());
    }
    @Test
    void blockedHeadCannotRepeatAndAgedBackgroundCanBypassNewInteraction() {
        var queue = new PriorityQueue<Integer>();
        queue.addAll(List.of(1, 2, 3, 99));
        try (var round = new ChunkBuildDispatch.Round<>(queue, 8)) {
            assertEquals(1, round.poll());
            round.defer(1);
            assertEquals(99, round.pollPreferred(x -> x == 99, Comparator.naturalOrder()));
            assertEquals(2, round.poll());
            assertEquals(3, round.poll());
            assertNull(round.poll());
        }
        assertEquals(List.of(1), List.copyOf(queue));
    }
}
