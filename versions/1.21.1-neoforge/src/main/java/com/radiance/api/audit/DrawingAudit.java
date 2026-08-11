package com.radiance.api.audit;

/** Optional request census. A consumer acknowledges CPU submission, never GPU completion. */
public final class DrawingAudit {
    private static final Scope DISABLED = new Scope(0, null, null);
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private DrawingAudit() {}

    public static long[] loadingCounts() {
        return com.radiance.bootstrap.BootstrapState.drawingCounts();
    }

    public static Scope begin(String family, String source, String conclusion, String target) {
        if ("EMPTY".equals(family)) return DISABLED;
        long id = RenderAuditBridge.beginDrawing(family, source, conclusion, target);
        var route =
                com.radiance.api.render.RenderRoutes.request(family, source, conclusion, target);
        if (id == 0 && route == null) return DISABLED;
        Scope scope = new Scope(id, CURRENT.get(), route);
        CURRENT.set(scope);
        return scope;
    }

    public static void consumed(String executor) {
        Scope scope = CURRENT.get();
        if (scope != null) {
            if (scope.id != 0) RenderAuditBridge.drawingEvent(scope.id, "CONSUMED", executor);
            if (scope.route != null) scope.route.submitted(executor);
        }
    }

    public static void unknown(String reason) {
        Scope scope = CURRENT.get();
        if (scope != null) {
            if (scope.id != 0) RenderAuditBridge.drawingEvent(scope.id, "UNKNOWN", reason);
            if (scope.route != null) scope.route.rejected(reason);
        }
    }

    /** Separate source records can share one native batch; each is acknowledged after its copy. */
    public static final class Tickets implements AutoCloseable {
        private java.util.List<Long> requests;
        private java.util.List<com.radiance.api.render.RenderRoutes.Ticket> routes;

        public void add(String family, String source, String conclusion, String target) {
            long id = RenderAuditBridge.beginDrawing(family, source, conclusion, target);
            var route =
                    com.radiance.api.render.RenderRoutes.request(
                            family, source, conclusion, target);
            if (route != null) {
                if (routes == null) routes = new java.util.ArrayList<>();
                routes.add(route);
            }
            if (id == 0) return;
            if (requests == null) requests = new java.util.ArrayList<>();
            requests.add(id);
        }

        public void consumed(String executor) {
            if (routes != null) routes.forEach(t -> t.submitted(executor));
            if (requests != null)
                for (long id : requests) RenderAuditBridge.drawingEvent(id, "CONSUMED", executor);
        }

        public void close() {
            if (routes != null) routes.forEach(com.radiance.api.render.RenderRoutes.Ticket::close);
            if (requests != null)
                for (long id : requests) RenderAuditBridge.drawingEvent(id, "END", "");
        }
    }

    public static final class Scope implements AutoCloseable {
        private final long id;
        private final Scope parent;
        private final com.radiance.api.render.RenderRoutes.Ticket route;

        private Scope(long id, Scope parent, com.radiance.api.render.RenderRoutes.Ticket route) {
            this.id = id;
            this.parent = parent;
            this.route = route;
        }

        @Override
        public void close() {
            if (id == 0 && route == null) return;
            if (id != 0) RenderAuditBridge.drawingEvent(id, "END", "");
            if (route != null) route.close();
            if (parent == null) CURRENT.remove();
            else CURRENT.set(parent);
        }
    }
}
