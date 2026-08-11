package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class SubmissionStringsTest {
    @Test
    void repeatedLayersShareBytesUntilSynchronousConsumerReturns() {
        AtomicInteger allocations = new AtomicInteger();
        List<ByteBuffer> freed = new ArrayList<>();
        SubmissionStrings strings = new SubmissionStrings(s -> {
            allocations.incrementAndGet();
            return MemoryUtil.memUTF8(s, true);
        }, b -> { freed.add(b); MemoryUtil.memFree(b); });
        List<String> copiedByConsumer = new ArrayList<>();
        try (strings) {
            for (int i = 0; i < 1000; ++i) {
                String value = i % 2 == 0 ? "entity_cutout/地衣" : "";
                long pointer = strings.address(value);
                assertEquals(pointer, strings.address(new String(value)));
                copiedByConsumer.add(MemoryUtil.memUTF8(pointer));
            }
            assertEquals(2, allocations.get());
            assertTrue(freed.isEmpty());
        }
        strings.close();
        assertEquals(2, freed.size());
        assertEquals("entity_cutout/地衣", copiedByConsumer.getFirst());
        assertEquals("", copiedByConsumer.getLast());
        assertThrows(IllegalStateException.class, () -> strings.address("late"));
    }

    @Test
    void failureAndNestedSubmissionsHaveIndependentOwnership() {
        AtomicInteger allocations = new AtomicInteger(), releases = new AtomicInteger();
        try (var outer = new SubmissionStrings()) {
            long live = outer.address("owner");
            assertThrows(OutOfMemoryError.class, () -> {
                try (var inner = new SubmissionStrings(s -> {
                    if (allocations.incrementAndGet() == 2) throw new OutOfMemoryError("injected");
                    return MemoryUtil.memUTF8(s, true);
                }, b -> { releases.incrementAndGet(); MemoryUtil.memFree(b); })) {
                    assertNotEquals(live, inner.address("owner"));
                    inner.address("second");
                }
            });
            assertEquals(1, releases.get());
            assertEquals("owner", MemoryUtil.memUTF8(live));
        }
        try (var next = new SubmissionStrings()) {
            assertEquals("new frame", MemoryUtil.memUTF8(next.address("new frame")));
        }
    }
}
