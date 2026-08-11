package com.radiance.audit;

import java.nio.file.Files;
import java.nio.file.Path;

/** Active probes require both an explicit JVM option and an isolated-instance marker. */
public final class ExperimentAccess {
    private static final boolean PERMITTED = allowed(AuditConfiguration.enabled(),
        Boolean.getBoolean("radiance.audit.experiments"),
        Files.isRegularFile(Path.of(System.getProperty("user.dir")).resolve(".radiance-audit-test-instance")));
    private ExperimentAccess() {}
    static boolean allowed(boolean enabled, boolean requested, boolean isolated) {
        return enabled && requested && isolated;
    }
    public static boolean permitted() { return PERMITTED; }
    public static String getenv(String name) { return PERMITTED ? System.getenv(name) : null; }
    public static java.util.Map<String, String> getenv() { return PERMITTED ? System.getenv() : java.util.Map.of(); }
}
