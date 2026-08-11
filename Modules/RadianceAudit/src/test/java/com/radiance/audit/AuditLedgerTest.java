package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditLedgerTest {
    @TempDir Path directory;
    @Test void realLedgerKeepsAsyncIntentAcrossFramesAndReleasesCapacityExactlyOnce() throws Exception {
        AuditBudget budget = new AuditBudget(1, 8192);
        AuditLedger ledger = new AuditLedger(directory, budget);
        long first = ledger.begin("chunk", "producer", "", false);
        ledger.frameBoundary(1, "END", "");
        assertEquals(1, ledger.openCount());
        assertEquals(0, ledger.begin("chunk", "deferred", "", false));
        assertEquals(1, budget.droppedIntents());
        ledger.transition(first, "TRANSLATED", "PT", "", true);
        ledger.transition(first, "TRANSLATED", "PT", "duplicate", true);
        assertNotEquals(0, ledger.begin("chunk", "next", "", false));
        assertEquals(0, ledger.begin("chunk", "still-full", "", false));
        ledger.close(); ledger.close();
        long size = Files.size(directory.resolve("ledger.jsonl"));
        assertEquals(0, ledger.begin("chunk", "after-close", "", false));
        assertEquals(size, Files.size(directory.resolve("ledger.jsonl")));
        assertTrue(Files.readString(directory.resolve("ledger.jsonl")).contains("UNKNOWN"));
    }
    @Test void realWriterNeverExceedsBudgetIncludingShutdownRecords() throws Exception {
        AuditLedger ledger = new AuditLedger(directory, new AuditBudget(2, 700));
        ledger.begin("chunk", "producer", "x".repeat(5000), false);
        ledger.close();
        assertTrue(Files.size(directory.resolve("ledger.jsonl")) <= 700);
    }
    @Test void activeExperimentsNeedBothExplicitRequestAndIsolatedMarker() {
        assertFalse(ExperimentAccess.allowed(true, true, false));
        assertFalse(ExperimentAccess.allowed(true, false, true));
        assertFalse(ExperimentAccess.allowed(false, true, true));
        assertTrue(ExperimentAccess.allowed(true, true, true));
    }
}
