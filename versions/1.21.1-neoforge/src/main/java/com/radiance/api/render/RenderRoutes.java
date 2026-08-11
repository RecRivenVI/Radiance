package com.radiance.api.render;

import java.util.*;

/**
 * On-demand CPU routing census. Disabled means no per-draw storage, logging or string construction.
 */
public final class RenderRoutes {
    private static volatile boolean enabled;
    private static volatile long epoch;
    private static final Map<Key, Counts> COUNTS = new LinkedHashMap<>();

    public record Key(String family, String source, String route, String domain) {}

    public record Row(
            Key key,
            long requested,
            long submitted,
            long rejected,
            String executor,
            String reason) {}

    private static final class Counts {
        long requested, submitted, rejected;
        String executor = "", reason = "";
    }

    public static final class Ticket {
        private final Key key;
        private final long window;
        private boolean submitted, rejected, closed;
        private String executor = "", reason = "";

        private Ticket(Key key) {
            this.key = key;
            this.window = epoch;
        }

        public void submitted(String executor) {
            this.submitted = true;
            this.executor = bounded(executor);
        }

        public void rejected(String reason) {
            this.rejected = true;
            this.reason = bounded(reason);
        }

        public void close() {
            if (closed) return;
            closed = true;
            record(this);
        }
    }

    private RenderRoutes() {}

    public static synchronized void start() {
        ++epoch;
        COUNTS.clear();
        enabled = true;
    }

    public static void stop() {
        enabled = false;
    }

    public static boolean active() {
        return enabled;
    }

    public static Ticket request(String family, String source, String route, String domain) {
        if (!enabled) return null;
        return new Ticket(
                new Key(bounded(family), bounded(source), bounded(route), bounded(domain)));
    }

    private static String bounded(String value) {
        return value == null ? "" : value.substring(0, Math.min(160, value.length()));
    }

    private static synchronized void record(Ticket ticket) {
        if (!enabled || ticket.window != epoch) return;
        Key key = ticket.key;
        if (!COUNTS.containsKey(key) && COUNTS.size() >= 128)
            key = new Key("OTHER", "bounded detail capacity", "UNCLASSIFIED", "MIXED");
        Counts count = COUNTS.computeIfAbsent(key, k -> new Counts());
        count.requested++;
        if (ticket.submitted) count.submitted++;
        if (ticket.rejected || !ticket.submitted) {
            count.rejected++;
            count.reason =
                    ticket.reason.isEmpty()
                            ? "No CPU consumer acknowledgment in this request"
                            : ticket.reason;
        }
        if (!ticket.executor.isEmpty()) count.executor = ticket.executor;
    }

    public static synchronized List<Row> snapshot() {
        return COUNTS.entrySet().stream()
                .map(
                        e ->
                                new Row(
                                        e.getKey(),
                                        e.getValue().requested,
                                        e.getValue().submitted,
                                        e.getValue().rejected,
                                        e.getValue().executor,
                                        e.getValue().reason))
                .toList();
    }
}
