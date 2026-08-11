package com.radiance.client.render;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.TreeMap;

/** Keeps final-world consumers after the native world image has been fused. */
public final class AfterWorldRender {
    private static final ThreadLocal<TreeMap<Integer, ArrayDeque<Runnable>>> PENDING = new ThreadLocal<>();

    private AfterWorldRender() {}

    public static boolean isActive() {
        return PENDING.get() != null;
    }

    public static void begin() {
        if (PENDING.get() != null) throw new IllegalStateException("World post queue already active");
        PENDING.set(new TreeMap<>());
    }

    public static boolean defer(Runnable action) {
        return defer(0, action);
    }

    public static boolean defer(int order, Runnable action) {
        var queue = PENDING.get();
        if (queue == null) return false;
        queue.computeIfAbsent(order, ignored -> new ArrayDeque<>()).addLast(Objects.requireNonNull(action));
        return true;
    }

    public static void flush() {
        var queue = PENDING.get();
        PENDING.remove();
        if (queue != null) {
            for (var actions : queue.values()) {
                while (!actions.isEmpty()) actions.removeFirst().run();
            }
        }
    }

    public static void cancel() {
        PENDING.remove();
    }
}
