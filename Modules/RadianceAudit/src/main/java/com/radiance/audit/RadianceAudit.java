package com.radiance.audit;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/** Optional client observer. Installation alone never changes game state or library settings. */
@Mod(value = "radiance_audit", dist = Dist.CLIENT)
public final class RadianceAudit {
    public RadianceAudit() {
        if (!AuditConfiguration.enabled()) return;
        boolean attached = ModList.get().isLoaded("radiance");
        AuditLedger.INSTANCE.initialize(attached);
        if (attached) { RadianceAuditAdapter.register(); FrameProfiler.register(); }
        if (attached && ExperimentAccess.permitted()) {
            FgBoundaryProbe.register();
            ChunkLatencyProbe.register();
        }
    }
}
