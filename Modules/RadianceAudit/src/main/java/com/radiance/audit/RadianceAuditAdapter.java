package com.radiance.audit;

import com.radiance.api.audit.RenderAuditBridge;
import com.radiance.api.audit.RenderAuditSink;

final class RadianceAuditAdapter implements RenderAuditSink {
    private static final RadianceAuditAdapter INSTANCE = new RadianceAuditAdapter();

    private RadianceAuditAdapter() {
    }

    static void register() {
        try {
            RenderAuditBridge.register(INSTANCE);
        } catch (RuntimeException | LinkageError failure) {
            com.mojang.logging.LogUtils.getLogger().error("Radiance audit API is incompatible; observer was not attached", failure);
        }
    }

    @Override
    public boolean accepts(String category) {
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
        return AuditHooks.routesToCurrentIntent()
            ? AuditLedger.INSTANCE.currentIntentId() : 0L;
    }

    @Override
    public void transition(long intentId, String state, String destination, String detail,
        boolean terminal) {
        AuditLedger.INSTANCE.transition(intentId, state, destination, detail, terminal);
    }

    @Override
    public void frameBoundary(long frameToken, String state, String detail) {
        AuditLedger.INSTANCE.frameBoundary(frameToken, state, detail);
    }

    @Override
    public void counter(String category, String event, long amount, String detail) {
        if ("CHUNK_UPDATE".equals(category)) {
            org.slf4j.LoggerFactory.getLogger("RadianceAudit").info("CHUNK_UPDATE event={} value={} {}",event,amount,detail);
        } else if ("CHUNK_BUILD".equals(category)) {
            ChunkBuildCounter.INSTANCE.record(event, amount, detail);
        } else if ("CHUNK_PERF".equals(category)) {
            ChunkBuildCounter.INSTANCE.recordPerformance(event, amount);
        }
    }
}
