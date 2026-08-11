package com.radiance.audit;

/** Read once per JVM; diagnostic configuration never mutates renderer options. */
public final class AuditConfiguration {
    private static final boolean ENABLED =
            !"off".equalsIgnoreCase(System.getProperty("radiance.audit.mode", "basic"));

    private AuditConfiguration() {}

    public static boolean enabled() {
        return ENABLED;
    }

    static int nativeFlags(
            boolean timing, boolean allocations, boolean capture, boolean lifecycle) {
        return (timing ? 2 : 0) | (allocations ? 1 : 0) | (capture ? 4 : 0) | (lifecycle ? 8 : 0);
    }
}
