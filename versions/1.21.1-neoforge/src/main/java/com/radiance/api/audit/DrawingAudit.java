package com.radiance.api.audit;

/** Optional request census. A consumer acknowledges CPU submission, never GPU completion. */
public final class DrawingAudit {
    private static final Scope DISABLED = new Scope(0, null);
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private DrawingAudit() {}

    public static long[] loadingCounts() {
        return com.radiance.bootstrap.BootstrapState.drawingCounts();
    }

    public static Scope begin(String family, String source, String conclusion, String target) {
        if ("EMPTY".equals(family)) return DISABLED;
        long id = RenderAuditBridge.beginDrawing(family, source, conclusion, target);
        if (id == 0) return DISABLED;
        Scope scope = new Scope(id, CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    public static void consumed(String executor) {
        Scope scope = CURRENT.get();
        if (scope != null) RenderAuditBridge.drawingEvent(scope.id, "CONSUMED", executor);
    }

    public static void unknown(String reason) {
        Scope scope = CURRENT.get();
        if (scope != null) RenderAuditBridge.drawingEvent(scope.id, "UNKNOWN", reason);
    }

    /** Separate source records can share one native batch; each is acknowledged after its copy. */
    public static final class Tickets implements AutoCloseable {
        private java.util.List<Long> requests;

        public void add(String family, String source, String conclusion, String target) {
            long id = RenderAuditBridge.beginDrawing(family, source, conclusion, target);
            if (id == 0) return;
            if (requests == null) requests = new java.util.ArrayList<>();
            requests.add(id);
        }

        public void consumed(String executor) {
            if (requests != null)
                for (long id : requests) RenderAuditBridge.drawingEvent(id, "CONSUMED", executor);
        }

        public void close() {
            if (requests != null)
                for (long id : requests) RenderAuditBridge.drawingEvent(id, "END", "");
        }
    }

    public static final class Scope implements AutoCloseable {
        private final long id;
        private final Scope parent;

        private Scope(long id, Scope parent) {
            this.id = id;
            this.parent = parent;
        }

        @Override
        public void close() {
            if (id == 0) return;
            RenderAuditBridge.drawingEvent(id, "END", "");
            if (parent == null) CURRENT.remove();
            else CURRENT.set(parent);
        }
    }
}
