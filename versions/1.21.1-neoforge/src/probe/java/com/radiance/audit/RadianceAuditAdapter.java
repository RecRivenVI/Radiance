package com.radiance.audit;

import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.api.audit.RenderAuditSink;

final class RadianceAuditAdapter implements RenderAuditSink {
    private static final RadianceAuditAdapter INSTANCE = new RadianceAuditAdapter();
    private static final java.util.concurrent.atomic.LongAdder PACKED_RECORDS =
            new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder EARLY_REUSED =
            new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder PACKED_BINDINGS =
            new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.ConcurrentMap<
                    String, java.util.concurrent.atomic.LongAdder>
            SOURCE_WORK = new java.util.concurrent.ConcurrentHashMap<>();

    static java.util.Map<String, Long> sourceWork() {
        var result = new java.util.TreeMap<String, Long>();
        SOURCE_WORK.forEach((key, value) -> result.put(key, value.sum()));
        return result;
    }

    private RadianceAuditAdapter() {}

    static void register() {
        try {
            RenderAuditBridge.register(INSTANCE);
            String result = System.getProperty("validation.result");
            if (result != null)
                Runtime.getRuntime()
                        .addShutdownHook(
                                new Thread(
                                        () -> {
                                            try {
                                                java.nio.file.Files.writeString(
                                                        java.nio.file.Path.of(result)
                                                                .resolve("source-work.json"),
                                                        new com.google.gson.Gson()
                                                                        .toJson(
                                                                                java.util.Map.of(
                                                                                        "counts",
                                                                                        sourceWork(),
                                                                                        "scope",
                                                                                        "Whole process, all views; not a timed-window counter"))
                                                                + "\n");
                                                java.nio.file.Files.writeString(
                                                        java.nio.file.Path.of(result)
                                                                .resolve("rigid-packing.json"),
                                                        new com.google.gson.Gson()
                                                                        .toJson(
                                                                                java.util.Map.of(
                                                                                        "packedRecords",
                                                                                        PACKED_RECORDS
                                                                                                .sum(),
                                                                                        "earlyReused",
                                                                                        EARLY_REUSED
                                                                                                .sum(),
                                                                                        "packedBindings",
                                                                                        PACKED_BINDINGS
                                                                                                .sum(),
                                                                                        "scope",
                                                                                        "Whole process, all views; not a timed-window counter"))
                                                                + "\n");
                                            } catch (java.io.IOException failure) {
                                                com.mojang.logging.LogUtils.getLogger()
                                                        .error(
                                                                "Cannot retain rigid packing counters",
                                                                failure);
                                            }
                                        },
                                        "Radiance rigid packing counters"));
        } catch (RuntimeException | LinkageError failure) {
            com.mojang.logging.LogUtils.getLogger()
                    .error(
                            "Radiance audit API is incompatible; observer was not attached",
                            failure);
        }
    }

    @Override
    public boolean accepts(String category) {
        if ("SOURCE".equals(category) && !FrameProfiler.sourceWorkEnabled()) return false;
        if (!AuditHooks.accepts(category)) return false;
        if ("WORLD_MESH".equals(category)) {
            return "1".equals(System.getenv("RADIANCE_AUDIT_DRAW_DETAILS"));
        }
        return !"SHADER_DRAW_STATE".equals(category)
                || "1".equals(System.getenv("RADIANCE_AUDIT_SHADER_DETAILS"));
    }

    @Override
    public long beginIntent(String category, String source, String detail) {
        return AuditLedger.INSTANCE.begin(category, source, detail, false);
    }

    @Override
    public long currentIntentId() {
        // A sampled-out nested producer must mask its sampled parent. Otherwise a draw made by
        // the nested producer is falsely recorded as consumption of the outer operation.
        return AuditHooks.routesToCurrentIntent() ? AuditLedger.INSTANCE.currentIntentId() : 0L;
    }

    @Override
    public void transition(
            long intentId, String state, String destination, String detail, boolean terminal) {
        AuditLedger.INSTANCE.transition(intentId, state, destination, detail, terminal);
    }

    @Override
    public void frameBoundary(long frameToken, String state, String detail) {
        AuditLedger.INSTANCE.frameBoundary(frameToken, state, detail);
    }

    @Override
    public void counter(String category, String event, long amount, String detail) {
        if ("SOURCE".equals(category)) {
            SOURCE_WORK
                    .computeIfAbsent(event, key -> new java.util.concurrent.atomic.LongAdder())
                    .add(amount);
        } else if ("CHUNK_UPDATE".equals(category)) {
            org.slf4j.LoggerFactory.getLogger("RadianceAudit")
                    .info("CHUNK_UPDATE event={} value={} {}", event, amount, detail);
        } else if ("CHUNK_BUILD".equals(category)) {
            ChunkBuildCounter.INSTANCE.record(event, amount, detail);
        } else if ("CHUNK_PERF".equals(category)) {
            ChunkBuildCounter.INSTANCE.recordPerformance(event, amount);
        } else if ("RIGID".equals(category)) {
            switch (event) {
                case "PACKED_RECORDS" -> PACKED_RECORDS.add(amount);
                case "EARLY_REUSED" -> EARLY_REUSED.add(amount);
                case "PACKED_BINDINGS" -> PACKED_BINDINGS.add(amount);
            }
        }
    }

    @Override
    public long beginDrawing(String family, String source, String conclusion, String target) {
        return DrawingCensus.ENABLED
                ? DrawingCensus.INSTANCE.begin(
                        DrawingCensus.INSTANCE.scene(
                                net.minecraft.client.Minecraft.getInstance().level),
                        family,
                        source,
                        conclusion,
                        target)
                : 0;
    }

    @Override
    public void drawingEvent(long request, String event, String detail) {
        DrawingCensus.INSTANCE.event(request, event, detail);
    }
}
