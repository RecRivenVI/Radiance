package com.radiance.client.vertex;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Definition recipes belong to the source; poses and current-view requests never enter this memo.
 */
public final class SourceBindings {
    public interface Owner extends com.radiance.api.render.PersistentInstances.OwnerState {
        SourceBindings radiance$bindings();

        default boolean radiance$isRetired() {
            return radiance$bindings().retired;
        }
    }

    private final Long2ObjectOpenHashMap<Slot> slots = new Long2ObjectOpenHashMap<>();
    private final Map<Object, Object> definitions = new HashMap<>();

    private final Map<Object, Long2LongOpenHashMap> histories = new HashMap<>();
    private Object historyDomain;
    private Long2LongOpenHashMap historySlots;
    private static final java.util.concurrent.atomic.AtomicLong IDENTITIES =
            new java.util.concurrent.atomic.AtomicLong();
    private static long resourceEpoch = 1;
    private long epoch;
    private long slotsSweptAt = Long.MIN_VALUE;
    private boolean retired;

    static final class Slot {
        Object signature;
        Object recipe;
        long used;
    }

    private void current() {
        if (epoch != resourceEpoch) {
            slots.clear();
            definitions.clear();
            histories.clear();
            historySlots = null;
            historyDomain = null;
            epoch = resourceEpoch;
        }
    }

    public static void invalidateResources() {
        ++resourceEpoch;
    }

    long history(Object domain, long slot) {
        current();
        if (historySlots == null || !java.util.Objects.equals(historyDomain, domain)) {
            historySlots = histories.computeIfAbsent(domain, ignored -> new Long2LongOpenHashMap());
            historyDomain = domain;
        }
        long previous = historySlots.get(slot);
        if (previous != 0) return previous;
        long id = IDENTITIES.incrementAndGet();
        if (id <= 0 || id >= 1L << 61)
            throw new IllegalStateException("Source instance identity exhausted");
        long history = Long.MIN_VALUE | 1L << 62 | id;
        historySlots.put(slot, history);
        return history;
    }

    public static long localHistory(Owner owner, long identity, int ordinal) {
        return owner.radiance$bindings()
                .history(
                        com.radiance.api.render.LocalModel.class,
                        (identity << 32) | Integer.toUnsignedLong(ordinal));
    }

    @SuppressWarnings("unchecked")
    public <T> T definition(Object key, java.util.function.Supplier<T> factory) {
        current();
        return (T) definitions.computeIfAbsent(key, unused -> factory.get());
    }

    Slot slot(long identity, long frame) {
        current();
        var result = slots.computeIfAbsent(identity, key -> new Slot());
        result.used = frame;
        if ((frame & 127) == 0 && slotsSweptAt != frame) {
            slots.values().removeIf(slot -> frame - slot.used > 120);
            slotsSweptAt = frame;
        }
        return result;
    }

    public void clear() {
        slots.clear();
        definitions.clear();
        histories.clear();
        historySlots = null;
        historyDomain = null;
    }

    public void retire() {
        retired = true;
        slots.clear();
        definitions.clear();
    }

    public void resume() {
        if (retired) {
            clear();
            retired = false;
        }
    }
}
