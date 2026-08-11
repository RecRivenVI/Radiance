package com.radiance.api.audit;

import java.util.Objects;

/** Optional observer boundary. Disabled calls allocate no events; observer failures detach it. */
public final class RenderAuditBridge {
    public static final int API_VERSION = 1;
    private static volatile RenderAuditSink sink;
    private RenderAuditBridge() {}

    public static boolean enabled() { return sink != null; }

    public static synchronized void register(RenderAuditSink observer) {
        Objects.requireNonNull(observer, "observer");
        if (observer.apiVersion() != API_VERSION) throw new IllegalArgumentException("Unsupported Radiance audit API version");
        if (sink != null && sink != observer) throw new IllegalStateException("A render audit sink is already registered");
        sink = observer;
    }
    public static synchronized void unregister(RenderAuditSink observer) {
        if (sink == observer) sink = null;
    }
    private static void failed(RenderAuditSink observer, Throwable error) {
        synchronized (RenderAuditBridge.class) {
            if (sink != observer) return;
            sink = null;
        }
        try {
            System.getLogger("Radiance/Audit").log(System.Logger.Level.ERROR,
                "Diagnostic observer detached after failure; renderer state was not changed", error);
        } catch (Throwable loggingFailure) {
            // The diagnostic is already disabled; reporting cannot rethrow into rendering/close.
        }
    }
    public static long beginIntent(String category, String source, String detail) {
        RenderAuditSink observer = sink;
        if (observer == null) return 0L;
        try { return observer.accepts(category) ? observer.beginIntent(category, source, detail) : 0L; }
        catch (Throwable error) { failed(observer, error); return 0L; }
    }
    public static boolean accepts(String category) {
        RenderAuditSink observer = sink;
        if (observer == null) return false;
        try { return observer.accepts(category); }
        catch (Throwable error) { failed(observer, error); return false; }
    }
    public static long currentIntentId() {
        RenderAuditSink observer = sink;
        if (observer == null) return 0L;
        try { return observer.currentIntentId(); }
        catch (Throwable error) { failed(observer, error); return 0L; }
    }
    public static void transition(long intentId, String state, String destination, String detail, boolean terminal) {
        RenderAuditSink observer = sink;
        if (observer == null) return;
        try {
            long resolved = intentId == 0L ? observer.currentIntentId() : intentId;
            if (resolved != 0L) observer.transition(resolved, state, destination, detail, terminal);
        } catch (Throwable error) { failed(observer, error); }
    }
    public static void frameBoundary(long frameToken, String state, String detail) {
        RenderAuditSink observer = sink;
        if (observer == null) return;
        try { observer.frameBoundary(frameToken, state, detail); }
        catch (Throwable error) { failed(observer, error); }
    }
    public static void counter(String category, String event, long amount, String detail) {
        RenderAuditSink observer = sink;
        if (observer == null) return;
        try { if (observer.accepts(category)) observer.counter(category, event, amount, detail); }
        catch (Throwable error) { failed(observer, error); }
    }
}
