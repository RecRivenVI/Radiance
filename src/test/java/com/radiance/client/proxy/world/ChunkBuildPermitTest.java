package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChunkBuildPermitTest {
    @Test
    void enforcesCapacityBeforeExpensiveBuildPreparation() {
        AtomicInteger inFlight = new AtomicInteger();
        ChunkBuildPermit first = ChunkBuildPermit.tryAcquire(inFlight, 2);
        ChunkBuildPermit second = ChunkBuildPermit.tryAcquire(inFlight, 2);
        assertNotNull(first);
        assertNotNull(second);
        assertNull(ChunkBuildPermit.tryAcquire(inFlight, 2));
        assertEquals(2, inFlight.get());

        first.close();
        try (ChunkBuildPermit replacement = ChunkBuildPermit.tryAcquire(inFlight, 2)) {
            assertNotNull(replacement);
            assertEquals(2, inFlight.get());
        }
        second.close();
        assertEquals(0, inFlight.get());
    }

    @Test
    void releaseIsIdempotentAcrossSubmissionFailureAndTaskCleanup() {
        AtomicInteger inFlight = new AtomicInteger();
        ChunkBuildPermit permit = ChunkBuildPermit.tryAcquire(inFlight, 1);
        assertNotNull(permit);
        permit.close();
        permit.close();
        assertEquals(0, inFlight.get());
    }
}
