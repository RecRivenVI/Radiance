package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SectionBuildQueueTest {
    record Request(int id, int distance, long age, boolean interactive, long source) {}

    static final Comparator<Request> PRIORITY =
            Comparator.comparing(Request::interactive)
                    .reversed()
                    .thenComparingInt(Request::distance)
                    .thenComparingLong(Request::age)
                    .thenComparingInt(Request::id);

    private SectionBuildQueue<Request> queue() {
        return new SectionBuildQueue<>(
                Request::id,
                PRIORITY,
                Request::source,
                r -> Math.floor(Math.sqrt(r.distance())),
                r -> !r.interactive());
    }

    @Test
    void nearbySourcesRotateAndDistantOwnersCannotJumpTheFrontier() {
        var queue = queue();
        for (int i = 0; i < 40; i++) queue.offer(new Request(i, 1, i, false, 8));
        queue.offer(new Request(100, 2, 100, false, 0));
        queue.offer(new Request(101, 180, -1, false, 4));
        assertEquals(100, queue.poll().id());
        assertEquals(8, queue.poll().source());
        queue.offer(new Request(102, 2, 102, false, 0));
        assertEquals(102, queue.poll().id());
        for (int i = 0; i < 39; i++) assertEquals(8, queue.poll().source());
        assertEquals(101, queue.poll().id());
    }

    @Test
    void indexedSelectionCancellationAndBackgroundSlotsMatchIndependentReference() {
        var queue = queue();
        var reference = new TreeMap<Integer, Request>();
        Random random = new Random(0x6c68756e6bL);
        long lastSource = Long.MAX_VALUE;
        for (int turn = 0; turn < 10000; turn++) {
            int id = random.nextInt(300);
            switch (random.nextInt(4)) {
                case 0 -> {
                    var request =
                            new Request(
                                    id,
                                    random.nextInt(100),
                                    turn,
                                    random.nextBoolean(),
                                    random.nextInt(9));
                    reference.putIfAbsent(id, request);
                    queue.offer(request);
                }
                case 1 -> {
                    reference.remove(id);
                    queue.removeSection(id);
                }
                default -> {
                    boolean background = random.nextBoolean();
                    Request expected = reference.values().stream().min(PRIORITY).orElse(null);
                    if (expected != null && (background || !expected.interactive())) {
                        var heads = new TreeMap<Long, Request>();
                        for (var candidate : reference.values())
                            if (!candidate.interactive())
                                heads.merge(
                                        candidate.source(),
                                        candidate,
                                        (a, b) -> PRIORITY.compare(a, b) <= 0 ? a : b);
                        double band =
                                heads.values().stream()
                                        .mapToDouble(r -> Math.floor(Math.sqrt(r.distance())))
                                        .min()
                                        .orElse(Double.POSITIVE_INFINITY);
                        heads.values().removeIf(r -> Math.floor(Math.sqrt(r.distance())) != band);
                        if (!heads.isEmpty()) {
                            var chosen = heads.higherEntry(lastSource);
                            if (chosen == null) chosen = heads.firstEntry();
                            expected = chosen.getValue();
                        }
                    }
                    assertEquals(expected, queue.poll(background));
                    if (expected != null) {
                        reference.remove(expected.id());
                        if (!expected.interactive()) lastSource = expected.source();
                    }
                }
            }
            assertEquals(reference.size(), queue.size());
        }
        queue.clear();
        assertTrue(queue.isEmpty());
    }

    @Test
    void largeBacklogSelectionAndPromotionFollowHeapHeight() {
        AtomicInteger comparisons = new AtomicInteger();
        Comparator<Request> measured =
                (a, b) -> {
                    comparisons.incrementAndGet();
                    return PRIORITY.compare(a, b);
                };
        var queue =
                new SectionBuildQueue<Request>(
                        Request::id,
                        measured,
                        Request::source,
                        r -> Math.floor(Math.sqrt(r.distance())),
                        r -> !r.interactive());
        for (int i = 0; i < 20000; i++) queue.offer(new Request(i, 20000 - i, i, false, i % 3));
        comparisons.set(0);
        queue.removeSection(12345);
        queue.offer(new Request(12345, 99999, 20001, true, 0));
        assertEquals(12345, queue.poll().id());
        assertNotNull(queue.poll(true));
        assertTrue(comparisons.get() < 300, "Selection must not scan queued sections");
    }

    @Test
    void deferredOldRequestCannotReplacePromotionAndOffersAreThreadSafe() throws Exception {
        var queue = queue();
        var old = new Request(1, 10, 0, false, 0);
        queue.offer(old);
        try (var round = new ChunkBuildDispatch.Round<>(queue, 8)) {
            assertEquals(old, round.poll());
            round.defer(old);
            queue.offer(new Request(1, 1, 1, true, 0));
        }
        assertTrue(queue.poll().interactive());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = new ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 2; worker++) {
                int base = worker * 1000;
                results.add(
                        executor.submit(
                                () -> {
                                    for (int i = 0; i < 1000; i++)
                                        queue.offer(
                                                new Request(base + i, i, base + i, false, base));
                                }));
            }
            for (var result : results) result.get();
        }
        assertEquals(2000, queue.size());
        var ids = new HashSet<Integer>();
        Request request;
        while ((request = queue.poll()) != null) assertTrue(ids.add(request.id()));
        assertEquals(2000, ids.size());
    }

    @Test
    void movingCameraRebuildsBothIndexesWithoutLosingAgeOrPromotion() {
        var queue = queue();
        for (int i = 0; i < 200; i++) queue.offer(new Request(i, i, i, false, 0));
        assertThrows(
                IllegalStateException.class,
                () ->
                        queue.reprioritize(
                                r -> {
                                    if (r.id() == 20) throw new IllegalStateException();
                                    return r;
                                }));
        assertEquals(200, queue.size());
        queue.reprioritize(
                r ->
                        r.id() == 100
                                ? null
                                : new Request(
                                        r.id(), 200 - r.distance(), r.age(), r.id() == 50, 0));
        assertEquals(50, queue.poll().id());
        var next = queue.poll(true);
        assertEquals(199, next.id());
        assertEquals(199, next.age());
        queue.update(0, r -> new Request(r.id(), 0, r.age(), false, 1));
        assertEquals(0, queue.poll(true).id());
        queue.removeSection(80);
        queue.clear();
        assertTrue(queue.isEmpty());
    }
}
