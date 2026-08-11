package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.radiance.render.contract.DrawTargetContract.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class DiscardReportBoundsTest {
    @Test
    void realReportAdmissionIsBoundedUnderConcurrentDistinctLabels() throws Exception {
        Field field = RenderCaptureContract.class.getDeclaredField("REPORTED_DISCARDS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> keys = (Set<String>) field.get(null);
        var saved = new HashSet<>(keys);
        keys.clear();
        var pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<Callable<Integer>>();
            for (int t = 0; t < 16; t++) {
                final int thread = t;
                tasks.add(
                        () -> {
                            int accepted = 0;
                            for (int i = 0; i < 100; i++)
                                if (RenderCaptureContract.claimDiscardReport(
                                        "operation-" + thread + "-" + i,
                                        new Scope(
                                                ScopeKind.WORLD_STAGE,
                                                (thread + "/" + i + "/") + "x".repeat(2000))))
                                    accepted++;
                            return accepted;
                        });
            }
            int accepted = 0;
            for (var future : pool.invokeAll(tasks)) accepted += future.get();
            assertTrue(accepted > 0 && accepted <= 128);
            assertEquals(accepted, keys.size());
            assertTrue(keys.stream().allMatch(k -> k.length() <= 420));
        } finally {
            pool.shutdownNow();
            keys.clear();
            keys.addAll(saved);
        }
    }
}
