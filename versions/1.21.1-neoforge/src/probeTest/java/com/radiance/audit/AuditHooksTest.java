package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuditHooksTest {
    @Test
    void boundedUpdateCorrelationDoesNotEnableUnconfiguredWorldInspection() {
        assertFalse(AuditHooks.accepts("CHUNK_HOLE"));
        AuditHooks.chunkWindow(true);
        try {
            assertTrue(AuditHooks.accepts("CHUNK_UPDATE"));
            assertFalse(AuditHooks.accepts("CHUNK_HOLE"));
            assertFalse(AuditHooks.accepts("CHUNK_BUILD"));
            assertFalse(AuditHooks.accepts("CHUNK_PERF"));
        } finally {
            AuditHooks.chunkWindow(false);
        }
        assertFalse(AuditHooks.accepts("CHUNK_UPDATE"));
    }
}
