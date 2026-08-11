package com.radiance.audit;

import com.mojang.logging.LogUtils;
import com.radiance.client.proxy.world.ChunkProxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;

/** Aggregates the high-volume chunk pipeline events emitted by Radiance. */
public final class ChunkBuildCounter {
    public static final ChunkBuildCounter INSTANCE = new ChunkBuildCounter();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long REPORT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);

    private final Object resetLock = new Object();
    private final Map<String, LongAdder> counters = new LinkedHashMap<>();
    private final Map<String, LongAdder> performanceCounters = new LinkedHashMap<>();
    private volatile long generation;
    private volatile long lastReportNanos;
    private volatile String resetDetail = "not initialized";

    private ChunkBuildCounter() {
    }

    public void record(String event, long amount, String detail) {
        if ("RESET".equals(event)) {
            synchronized (resetLock) {
                generation++;
                counters.clear();
                performanceCounters.clear();
                resetDetail = detail == null ? "" : detail;
                lastReportNanos = 0;
            }
            maybeReport(true);
            return;
        }
        synchronized (resetLock) {
            counters.computeIfAbsent(event, ignored -> new LongAdder()).add(amount);
        }
        // Important chunk rebuilds can block the render thread before the first frame. Emit the
        // aggregate from whichever producer is still making progress so that this stall remains
        // observable without changing queue behavior.
        maybeReport(true);
    }

    public void maybeReport() {
        maybeReport(true);
    }

    public void recordPerformance(String event, long amount) {
        synchronized (resetLock) {
            performanceCounters.computeIfAbsent(event, ignored -> new LongAdder()).add(amount);
        }
        maybeReport(true);
    }

    private void maybeReport(boolean includeNativeReady) {
        long now = System.nanoTime();
        if (now - lastReportNanos < REPORT_INTERVAL_NANOS) return;

        long currentGeneration;
        StringBuilder values = new StringBuilder();
        synchronized (resetLock) {
            if (now - lastReportNanos < REPORT_INTERVAL_NANOS) return;
            lastReportNanos = now;
            currentGeneration = generation;
            values.append(resetDetail);
            counters.forEach((name, value) -> values.append("; ")
                .append(name.toLowerCase()).append('=').append(value.sum()));
            performanceCounters.forEach((name, value) -> values.append("; perf_")
                .append(name.toLowerCase()).append('=').append(value.sum()));
            performanceCounters.clear();
        }

        if (includeNativeReady) {
            int nativeReady;
            try {
                nativeReady = ChunkProxy.countReadyChunksNative();
            } catch (RuntimeException | LinkageError exception) {
                nativeReady = -1;
            }
            values.append("; native_ready=").append(nativeReady);
            try {
                values.append("; native_perf={")
                    .append(ChunkProxy.performanceSnapshotNative()).append('}');
            } catch (RuntimeException | LinkageError exception) {
                values.append("; native_perf=unavailable");
            }
        }
        String detail = values.toString();
        LOGGER.info("Chunk build counters [{}]: {}", currentGeneration, detail);
        AuditLedger.INSTANCE.counterSnapshot(currentGeneration, detail);
    }
}
