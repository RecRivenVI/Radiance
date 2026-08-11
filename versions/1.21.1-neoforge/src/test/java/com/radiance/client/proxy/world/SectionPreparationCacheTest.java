package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SectionPreparationCacheTest {
    @Test
    void oneRoundSharesOnlyTheSameSourceRevisionAndKeepsOldSnapshotsIntact() {
        AtomicInteger creates = new AtomicInteger();
        var cache = new SectionPreparationCache<>(() -> new int[] {creates.incrementAndGet()});
        int[] first = cache.get(3);
        for (int i = 0; i < 50; i++) assertSame(first, cache.get(3));
        int[] next = cache.get(4);
        assertNotSame(first, next);
        assertEquals(1, first[0]);
        assertEquals(2, next[0]);
        var anotherRound =
                new SectionPreparationCache<>(() -> new int[] {creates.incrementAndGet()});
        assertNotSame(next, anotherRound.get(4));
    }

    @Test
    void failedReplacementDoesNotAcknowledgeAnInvalidation() {
        AtomicInteger creates = new AtomicInteger();
        var cache =
                new SectionPreparationCache<>(
                        () -> {
                            if (creates.incrementAndGet() == 2) throw new IllegalStateException();
                            return new Object();
                        });
        Object first = cache.get(3);
        assertThrows(IllegalStateException.class, () -> cache.get(4));
        assertNotSame(first, cache.get(4));
        assertEquals(3, creates.get());
    }
}
