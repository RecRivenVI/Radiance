package com.radiance.api.audit;

/** Read-only identity of the SERVICE-owned runtime, without exposing bootstrap classes to consumers. */
public final class AuditRuntimeIdentity {
    private AuditRuntimeIdentity() {}
    public static String runtimeDirectory() {
        return String.valueOf(com.radiance.bootstrap.BootstrapState.runtimeDirectory());
    }
}
