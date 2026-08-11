package com.radiance.audit.benchmark.adapter;

/** Loader entry point only; common instrumentation lives in the explicit startup companion. */
public final class PortableBenchmarkMod {
    public static void initialize() {
        boolean requested=Boolean.getBoolean("radiance.audit.benchmark");
        boolean active=Boolean.getBoolean("radiance.audit.benchmark.active");
        if(requested && !active)throw new IllegalStateException("Radiance Audit benchmark requires its matching -javaagent companion before window creation");
        System.getLogger("Radiance Audit/benchmark").log(System.Logger.Level.INFO,
            "Portable benchmark adapter loaded; startup companion active="+active+"; fork-native metrics unavailable");
    }
}
