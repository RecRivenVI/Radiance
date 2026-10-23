package com.radiance.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregate census, enabled only for the fixed correctness route; not a per-draw trace. */
final class DrawingCensus {
    static final boolean ENABLED = Boolean.getBoolean("radiance.audit.drawingCensus");
    static final DrawingCensus INSTANCE = new DrawingCensus();

    record Key(
            long scene,
            String window,
            String family,
            String source,
            String conclusion,
            String target) {}

    static final class Counts {
        long requests, consumed, unowned, duplicate, unknown;
    }

    private static final class Pending {
        final Key key;
        int consumers;
        boolean unknown;

        Pending(Key key) {
            this.key = key;
        }
    }

    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private final Map<Key, Counts> counts = new LinkedHashMap<>();
    private long sequence;
    private String window = "R0-loading";
    private long nextScene;
    private final Map<Object, Long> scenes = new java.util.WeakHashMap<>();

    synchronized long scene(Object level) {
        return level == null ? 0 : scenes.computeIfAbsent(level, k -> ++nextScene);
    }

    synchronized void window(String name) {
        window = name;
    }

    synchronized long begin(String family, String source, String conclusion, String target) {
        return begin(0, family, source, conclusion, target);
    }

    synchronized long begin(
            long scene, String family, String source, String conclusion, String target) {
        long id = ++sequence;
        Pending request = new Pending(new Key(scene, window, family, source, conclusion, target));
        request.unknown = "UNKNOWN".equals(conclusion);
        pending.put(id, request);
        return id;
    }

    synchronized void event(long id, String event, String detail) {
        Pending request = pending.get(id);
        if (request == null) throw new IllegalStateException("Unregistered drawing request: " + id);
        switch (event) {
            case "CONSUMED" -> request.consumers++;
            case "UNKNOWN" -> request.unknown = true;
            case "END" -> {
                Counts total = counts.computeIfAbsent(request.key, k -> new Counts());
                total.requests++;
                total.consumed += request.consumers > 0 ? 1 : 0;
                total.unowned += request.consumers == 0 ? 1 : 0;
                total.duplicate += Math.max(0, request.consumers - 1);
                total.unknown += request.unknown ? 1 : 0;
                pending.remove(id);
            }
            default -> throw new IllegalArgumentException(event);
        }
    }

    synchronized Map<Key, Counts> snapshot() {
        return new LinkedHashMap<>(counts);
    }

    synchronized int pendingCount() {
        return pending.size();
    }
}
