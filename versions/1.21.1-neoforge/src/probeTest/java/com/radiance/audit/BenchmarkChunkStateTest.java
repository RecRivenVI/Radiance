package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BenchmarkChunkStateTest {
    static final class State {
        private static final ArrayDeque<Integer> columnQueue = new ArrayDeque<>();
        private static final ArrayDeque<Integer> rebuildQueue = new ArrayDeque<>();
        private static final AtomicInteger importantBuildsInFlight = new AtomicInteger();
        private static final AtomicInteger normalBuildsInFlight = new AtomicInteger();
        private static int storageSizeX = 65, storageSizeY = 24, storageSizeZ = 65;
        private static boolean initialSeedPending;
    }

    @Test
    void boundaryReadsLiveQueuesAndDistinguishesSettledStateWithoutConsumingWork()
            throws Exception {
        State.columnQueue.clear();
        State.rebuildQueue.clear();
        State.columnQueue.add(7);
        State.rebuildQueue.add(9);
        State.normalBuildsInFlight.set(3);
        Properties before = new Properties();
        BenchmarkChunkState.captureJava(State.class, before);
        assertEquals("1", before.getProperty("chunk.java.columnQueue"));
        assertEquals("1", before.getProperty("chunk.java.rebuildQueue"));
        assertEquals("3", before.getProperty("chunk.java.normalBuildsInFlight"));
        assertEquals("101400", before.getProperty("chunk.java.slots"));
        assertEquals(7, State.columnQueue.peek());
        assertEquals(9, State.rebuildQueue.peek());
        State.columnQueue.clear();
        State.rebuildQueue.clear();
        State.normalBuildsInFlight.set(0);
        Properties after = new Properties();
        BenchmarkChunkState.captureJava(State.class, after);
        assertEquals("0", after.getProperty("chunk.java.columnQueue"));
        assertEquals("0", after.getProperty("chunk.java.normalBuildsInFlight"));
        assertEquals("3", before.getProperty("chunk.java.normalBuildsInFlight"));
    }

    @Test
    void missingContractIsReportedRatherThanInventingZeroWork() {
        assertThrows(
                ReflectiveOperationException.class,
                () -> BenchmarkChunkState.captureJava(Object.class, new Properties()));
    }
}
