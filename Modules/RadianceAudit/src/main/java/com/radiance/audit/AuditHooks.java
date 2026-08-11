package com.radiance.audit;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AuditHooks {
    private static final boolean SCREEN_EFFECTS_ENABLED =
        enabled("RADIANCE_AUDIT_SCREEN_EFFECTS", "radiance.audit.screenEffects");
    private static final boolean DRAW_DETAILS_ENABLED =
        enabled("RADIANCE_AUDIT_DRAW_DETAILS", "radiance.audit.drawDetails");
    private static final boolean CHUNK_DIAGNOSTICS_ENABLED =
        enabled("RADIANCE_AUDIT_CHUNKS", "radiance.audit.chunks");
    private static final ThreadLocal<ArrayDeque<Boolean>> SCOPES =
        ThreadLocal.withInitial(ArrayDeque::new);
    private static final long CONTINUOUS_SAMPLE_INTERVAL_NS = 5_000_000_000L;
    private static final ConcurrentHashMap<String, AtomicLong> LAST_CONTINUOUS_SAMPLE =
        new ConcurrentHashMap<>();

    private AuditHooks() {
    }

    private static boolean enabled(String environmentName, String propertyName) {
        String environmentValue = System.getenv(environmentName);
        if ("1".equals(environmentValue) || "true".equalsIgnoreCase(environmentValue)) {
            return true;
        }
        return Boolean.getBoolean(propertyName);
    }

    private static boolean disabled(String environmentName, String propertyName) {
        String environmentValue = System.getenv(environmentName);
        if ("0".equals(environmentValue) || "false".equalsIgnoreCase(environmentValue)) {
            return true;
        }
        return "false".equalsIgnoreCase(System.getProperty(propertyName));
    }

    public static long enter(String category, String source, String detail) {
        boolean accepted = accepts(category);
        SCOPES.get().push(accepted);
        if (!accepted) return 0L;
        long id = AuditLedger.INSTANCE.begin(category, source, detail, true);
        if (id == 0L) { SCOPES.get().pop(); SCOPES.get().push(false); }
        return id;
    }

    /**
     * Opens a normal producer scope for the first invocation and then at most once every five
     * seconds. Camera and GUI effects can remain active for thousands of frames; recording every
     * invocation perturbs the renderer and buries the useful takeover result in ledger traffic.
     */
    public static long enterContinuous(String category, String source, String detail) {
        boolean accepted = accepts(category) && sampleContinuous(category, source);
        SCOPES.get().push(accepted);
        if (!accepted) return 0L;
        long id = AuditLedger.INSTANCE.begin(category, source, detail, true);
        if (id == 0L) { SCOPES.get().pop(); SCOPES.get().push(false); }
        return id;
    }

    public static void exit(String source) {
        ArrayDeque<Boolean> scopes = SCOPES.get();
        if (scopes.isEmpty() || !scopes.pop()) return;
        AuditLedger.INSTANCE.exitObserved(source);
    }

    public static void event(String category, String source, String detail, String state,
        String destination) {
        if (!accepts(category)) return;
        long id = AuditLedger.INSTANCE.begin(category, source, detail, false);
        AuditLedger.INSTANCE.transition(id, state, destination, detail, true);
    }

    public static void continuousEvent(String category, String source, String detail, String state,
        String destination) {
        if (!accepts(category) || !sampleContinuous(category, source)) return;
        long id = AuditLedger.INSTANCE.begin(category, source, detail, false);
        AuditLedger.INSTANCE.transition(id, state, destination, detail, true);
    }

    private static boolean sampleContinuous(String category, String source) {
        long now = System.nanoTime();
        AtomicLong last = LAST_CONTINUOUS_SAMPLE.computeIfAbsent(category + '\u0000' + source,
            ignored -> new AtomicLong(Long.MIN_VALUE));
        while (true) {
            long previous = last.get();
            if (previous != Long.MIN_VALUE && now - previous < CONTINUOUS_SAMPLE_INTERVAL_NS) {
                return false;
            }
            if (last.compareAndSet(previous, now)) return true;
        }
    }

    public static boolean accepts(String category) {
        if (!AuditConfiguration.enabled()) return false;
        if ("SCREEN_EFFECT".equals(category)) return SCREEN_EFFECTS_ENABLED;
        if (category.startsWith("CHUNK_")) return CHUNK_DIAGNOSTICS_ENABLED;
        return switch (category) {
            case "BUFFER_DRAW", "RENDER_TYPE_DRAW", "PERSISTENT_BUFFER_DRAW" ->
                DRAW_DETAILS_ENABLED;
            default -> true;
        };
    }

    static boolean routesToCurrentIntent() {
        ArrayDeque<Boolean> scopes = SCOPES.get();
        return scopes.isEmpty() || Boolean.TRUE.equals(scopes.peek());
    }
}
