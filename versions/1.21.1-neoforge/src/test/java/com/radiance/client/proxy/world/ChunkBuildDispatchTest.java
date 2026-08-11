package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class ChunkBuildDispatchTest {
    @Test
    void unavailableExternalSectionsDoNotStarvePrimaryOrExternalGeometry() {
        record Request(long identity, boolean available) {}
        var queue =
                new SectionBuildQueue<Request>(
                        Request::identity,
                        Comparator.comparingLong(Request::identity),
                        r -> r.identity() >>> 32,
                        r -> 0.0,
                        r -> true);
        // Primary slot 1 and external handle generation=1, slot=1 are independent owners.
        long external = (1L << 32) | 1;
        for (long i = 0; i < 8; i++) queue.add(new Request(i, false));
        queue.add(new Request(external, true));
        queue.add(new Request(external + 1, true));
        var capacity = new AtomicInteger();
        var completed = new ArrayList<Long>();
        for (int frame = 0; frame < 2; frame++) {
            try (var round = new ChunkBuildDispatch.Round<>(queue, 8)) {
                Request next;
                while ((next = round.poll()) != null) {
                    Request request = next;
                    var result =
                            ChunkBuildDispatch.submit(
                                    new AtomicBoolean(),
                                    capacity,
                                    1,
                                    () ->
                                            request.available()
                                                    ? () -> completed.add(request.identity())
                                                    : ChunkBuildDispatch.awaitSource(),
                                    Runnable::run,
                                    () -> {});
                    assertNotEquals(ChunkBuildDispatch.Result.SATURATED, result);
                }
            }
        }
        assertEquals(List.of(external, external + 1), completed);
        assertEquals(0, capacity.get());
        queue.add(new Request(1, true));
        queue.add(new Request(external, true));
        assertEquals(2, queue.size(), "A primary slot must not alias a source-owned handle");
    }

    @Test
    void aMissingSourceIsADependencyNotAnotherExecutableTask() {
        var running = new AtomicBoolean();
        var capacity = new AtomicInteger();
        var workers = new ArrayDeque<Runnable>();
        assertEquals(
                ChunkBuildDispatch.Result.WAITING_FOR_SOURCE,
                ChunkBuildDispatch.submit(
                        running,
                        capacity,
                        1,
                        ChunkBuildDispatch::awaitSource,
                        workers::add,
                        () -> fail("Unavailable work must not complete")));
        assertTrue(workers.isEmpty());
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        // A later real source-arrival event can use the same owner and permit.
        assertEquals(
                ChunkBuildDispatch.Result.SUBMITTED,
                ChunkBuildDispatch.submit(
                        running, capacity, 1, () -> () -> {}, workers::add, () -> {}));
        workers.remove().run();
        assertFalse(running.get());
        assertEquals(0, capacity.get());
    }

    @Test
    void queuedWorkerIsDiscardedAfterOwnerRevisionChangesAndCapacityReturns() {
        var generation = new AtomicInteger(7);
        var running = new AtomicBoolean();
        var capacity = new AtomicInteger();
        var compiled = new AtomicInteger();
        var completed = new AtomicInteger();
        var workers = new ArrayDeque<Runnable>();
        ChunkBuildDispatch.submit(
                running,
                capacity,
                1,
                () ->
                        ChunkBuildDispatch.currentWork(
                                () -> generation.get() == 7, compiled::incrementAndGet),
                workers::add,
                completed::incrementAndGet);
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
        assertEquals(
                ChunkBuildDispatch.Result.SUBMITTED,
                ChunkBuildDispatch.submit(
                        running,
                        background,
                        1,
                        () -> {
                            prepared.incrementAndGet();
                            return () -> {};
                        },
                        workers::add,
                        completed::incrementAndGet));
        for (int i = 0; i < 20; i++)
            assertEquals(
                    ChunkBuildDispatch.Result.SATURATED,
                    ChunkBuildDispatch.submit(
                            new AtomicBoolean(),
                            background,
                            1,
                            () -> {
                                prepared.incrementAndGet();
                                return () -> {};
                            },
                            workers::add,
                            completed::incrementAndGet));
        assertEquals(1, prepared.get());
        assertEquals(
                ChunkBuildDispatch.Result.SUBMITTED,
                ChunkBuildDispatch.submit(
                        new AtomicBoolean(),
                        interactive,
                        1,
                        () -> {
                            prepared.incrementAndGet();
                            return () -> {};
                        },
                        workers::add,
                        completed::incrementAndGet));
        while (!workers.isEmpty()) workers.remove().run();
        assertEquals(0, background.get());
        assertEquals(0, interactive.get());
        assertFalse(running.get());
        assertEquals(2, completed.get());
        assertEquals(
                ChunkBuildDispatch.Result.SUBMITTED,
                ChunkBuildDispatch.submit(
                        running,
                        background,
                        1,
                        () -> {
                            prepared.incrementAndGet();
                            return () -> {};
                        },
                        Runnable::run,
                        completed::incrementAndGet));
        assertEquals(3, prepared.get());
    }

    @Test
    void rejectedFailedOrUnavailablePreparationReleasesExactlyOnce() {
        var capacity = new AtomicInteger();
        var running = new AtomicBoolean();
        var completed = new AtomicInteger();
        assertThrows(
                RejectedExecutionException.class,
                () ->
                        ChunkBuildDispatch.submit(
                                running,
                                capacity,
                                1,
                                () -> () -> {},
                                task -> {
                                    throw new RejectedExecutionException();
                                },
                                completed::incrementAndGet));
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ChunkBuildDispatch.submit(
                                running,
                                capacity,
                                1,
                                () -> {
                                    throw new IllegalArgumentException();
                                },
                                Runnable::run,
                                completed::incrementAndGet));
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        assertEquals(
                ChunkBuildDispatch.Result.DEFERRED,
                ChunkBuildDispatch.submit(
                        running,
                        capacity,
                        1,
                        () -> null,
                        Runnable::run,
                        completed::incrementAndGet));
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        var workers = new ArrayDeque<Runnable>();
        ChunkBuildDispatch.submit(
                running,
                capacity,
                1,
                () ->
                        () -> {
                            throw new IllegalStateException();
                        },
                workers::add,
                completed::incrementAndGet);
        assertThrows(IllegalStateException.class, workers.remove()::run);
        assertFalse(running.get());
        assertEquals(0, capacity.get());
        assertEquals(1, completed.get());
    }

    @Test
    void blockedHeadCannotRepeatAndBackgroundCanBypassNewInteraction() {
        var queue =
                new SectionBuildQueue<Integer>(
                        x -> x, Comparator.naturalOrder(), x -> 0L, x -> 0.0, x -> x == 99);
        queue.addAll(List.of(1, 2, 3, 99));
        try (var round = new ChunkBuildDispatch.Round<>(queue, 8)) {
            assertEquals(1, round.poll());
            round.defer(1);
            assertEquals(99, round.pollBackground());
            assertEquals(2, round.poll());
            assertEquals(3, round.poll());
            assertNull(round.poll());
        }
        assertEquals(List.of(1), List.copyOf(queue));
    }
}
