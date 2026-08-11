package com.radiance.api.audit;

/**
 * Optional observer implemented by the separately installed Radiance Audit mod.
 *
 * <p>The production renderer never depends on an implementation. When no sink is registered all
 * bridge calls are no-ops and no intent identifiers are allocated.</p>
 */
public interface RenderAuditSink {
    default int apiVersion() { return RenderAuditBridge.API_VERSION; }
    boolean accepts(String category);

    long beginIntent(String category, String source, String detail);

    long currentIntentId();

    void transition(long intentId, String state, String destination, String detail,
        boolean terminal);

    void frameBoundary(long frameToken, String state, String detail);

    /** Records an aggregate-friendly event without allocating an intent or writing one row per unit. */
    default void counter(String category, String event, long amount, String detail) {
    }
}
