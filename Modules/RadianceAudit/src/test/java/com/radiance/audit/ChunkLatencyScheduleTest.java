package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkLatencyScheduleTest {
    @Test void defaultSchedulePreservesHistoricalRunsAndBurstStartsAtReload() {
        assertEquals(12_000_000_000L,ChunkLatencyProbe.operationDueNs(0,2000,1500));
        assertEquals(42_000_000_000L,ChunkLatencyProbe.operationDueNs(12,2000,1500));
        assertEquals(58_500_000_000L,ChunkLatencyProbe.operationDueNs(23,2000,1500));
        assertEquals(40_000_000_000L,ChunkLatencyProbe.operationDueNs(12,0,100));
        assertEquals(41_100_000_000L,ChunkLatencyProbe.operationDueNs(23,0,100));
    }
    @Test void invalidSchedulesCannotSilentlyDriveAnUnboundedProbe() {
        assertThrows(IllegalArgumentException.class,()->ChunkLatencyProbe.operationDueNs(24,0,100));
        assertThrows(IllegalArgumentException.class,()->ChunkLatencyProbe.operationDueNs(12,-1,100));
        assertThrows(IllegalArgumentException.class,()->ChunkLatencyProbe.operationDueNs(12,0,0));
    }
}
