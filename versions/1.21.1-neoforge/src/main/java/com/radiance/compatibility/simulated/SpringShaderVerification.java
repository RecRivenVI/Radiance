package com.radiance.compatibility.simulated;

import java.util.Map;
import java.util.WeakHashMap;

/** Veil-free lifecycle gate shared by the original raster program and world lowering. */
public final class SpringShaderVerification {
    // ProgramModel identity, not native ID: native shader registration may reuse
    // the same handle on recompilation of the same source key.
    private static final Map<Object, Boolean> VERIFIED = new WeakHashMap<>();
    private static final Map<Object, Object> ACTIVE = new WeakHashMap<>();

    private SpringShaderVerification() {}

    public static synchronized void verified(Object model) {
        VERIFIED.put(java.util.Objects.requireNonNull(model), Boolean.TRUE);
    }

    public static synchronized void attached(Object owner, Object model) {
        if (!VERIFIED.containsKey(model))
            throw new IllegalStateException(
                    "Simulated spring shader attached without source verification");
        ACTIVE.put(java.util.Objects.requireNonNull(owner), model);
    }

    public static synchronized void released(Object owner, Object model) {
        if (ACTIVE.get(owner) == model) ACTIVE.remove(owner);
    }

    public static synchronized void requireActive() {
        if (ACTIVE.isEmpty())
            throw new IllegalStateException(
                    "Original Simulated spring shader is not verified and active");
    }
}
