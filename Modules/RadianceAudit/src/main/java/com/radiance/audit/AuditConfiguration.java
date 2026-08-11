package com.radiance.audit;

/** Read once per JVM; diagnostic configuration never mutates renderer options. */
public final class AuditConfiguration {
    private static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("radiance.audit.mode", "basic"));
    private AuditConfiguration() {}
    public static boolean enabled() { return ENABLED; }
}
