package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SectionBuildQueueTest {
    record Request(int id, int distance, long age, boolean interactive) {}

    static final Comparator<Request> PRIORITY =
            Comparator.comparing(Request::interactive)
                    .reversed()
                    .thenComparingInt(Request::distance)
                    .thenComparingLong(Request::age);

    private SectionBuildQueue<Request> queue() {
        return new SectionBuildQueue<>(
                Request::id,
                PRIORITY,
                Comparator.comparingLong(Request::age),
                r -> !r.interactive());
    }

    @Test
    void indexedPriorityCancellationAndAgeMatchIndependentReference() {
        var queue = queue();
        var reference = new TreeMap<Integer, Request>();
        Random random = new Random(0x6c68756e6bL);
        for (int turn = 0; turn < 10000; turn++) {
            int id = random.nextInt(300);
            switch (random.nextInt(4)) {
                case 0 -> {
                    var request = new Request(id, random.nextInt(30), turn, random.nextBoolean());
                    reference.putIfAbsent(id, request);
                    queue.offer(request);
                }
                case 1 -> {
                    reference.remove(id);
                    queue.removeSection(id);
                }
                case 2 -> {
                    var expected = reference.values().stream().min(PRIORITY).orElse(null);
                    assertEquals(expected, queue.poll());
                    if (expected != null) reference.remove(expected.id());
                }
                default -> {
                    long before = turn - 50;
                    var expected =
                            reference.values().stream()
                                    .filter(r -> !r.interactive())
                                    .min(Comparator.comparingLong(Request::age))
                                    .orElse(null);
                    if (expected == null || expected.age() >= before)
                        expected = reference.values().stream().min(PRIORITY).orElse(null);
                    assertEquals(expected, queue.pollAged(r -> r.age() < before));
                    if (expected != null) reference.remove(expected.id());
                }
            }
            assertEquals(reference.size(), queue.size());
            assertEquals(reference.values().stream().min(PRIORITY).orElse(null), queue.peek());
        }
        queue.clear();
        assertTrue(queue.isEmpty());
    }

    @Test
    void largeBacklogSelectionAndPromotionDoNotScanIt() {
        AtomicInteger comparisons = new AtomicInteger();
        Comparator<Request> measured =
                (a, b) -> {
                    comparisons.incrementAndGet();
                    return PRIORITY.compare(a, b);
                };
        var queue =
                new SectionBuildQueue<>(
                        Request::id,
                        measured,
                        (a, b) -> {
                            comparisons.incrementAndGet();
                            return Long.compare(a.age(), b.age());
                        },
                        r -> !r.interactive());
        for (int i = 0; i < 20000; i++) queue.offer(new Request(i, 20000 - i, i, false));
        comparisons.set(0);
        queue.removeSection(12345);
        queue.offer(new Request(12345, 99999, 20001, true));
        assertEquals(12345, queue.poll().id());
        assertEquals(0, queue.pollAged(r -> true).id());
        assertTrue(
                comparisons.get() < 300,
                "selection/cancellation should follow heap height, not backlog length");
    }

    @Test
    void deferredOldRequestCannotReplaceNewPromotionAndCompletionOffersAreThreadSafe()
            throws Exception {
        var queue = queue();
        Request old = new Request(1, 10, 0, false);
        queue.offer(old);
        try (var round = new ChunkBuildDispatch.Round<>(queue, 8)) {
            assertEquals(old, round.poll());
            round.defer(old);
            queue.offer(new Request(1, 1, 1, true));
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
                                        queue.offer(new Request(base + i, i, base + i, false));
                                }));
            }
            for (var result : results) result.get();
        }
        assertEquals(2000, queue.size());
        List<Request> drained = new ArrayList<>();
        Request request;
        while ((request = queue.poll()) != null) drained.add(request);
        assertEquals(2000, drained.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    void movingCameraRebuildsBothIndexesWithoutLosingAgeOrPromotion() {
        var queue = queue();
        for (int i = 0; i < 200; i++) queue.offer(new Request(i, i, i, false));
        assertThrows(
                IllegalStateException.class,
                () ->
                        queue.reprioritize(
                                r -> {
                                    if (r.id() == 20) throw new IllegalStateException();
                                    return r;
                                }));
        assertEquals(200, queue.size());
        assertEquals(0, queue.peek().id());
        queue.reprioritize(
                r ->
                        r.id() == 100
                                ? null
                                : new Request(r.id(), 200 - r.distance(), r.age(), r.id() == 50));
        assertEquals(199, queue.size());
        assertEquals(50, queue.poll().id());
        assertEquals(0, queue.pollAged(r -> true).id());
        assertEquals(199, queue.poll().id());
        queue.removeSection(80);
        assertEquals(195, queue.size());
        queue.clear();
        queue.offer(new Request(80, 0, 999, true));
        assertEquals(80, queue.poll().id());
    }
}
