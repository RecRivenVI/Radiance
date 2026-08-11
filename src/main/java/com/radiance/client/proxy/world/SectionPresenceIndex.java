package com.radiance.client.proxy.world;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Presence only: never filters by camera, distance, geometry layers or block-entity animation. */
final class SectionPresenceIndex<S, C> {
    record Entry<S, C>(S section, C compiled) {}
    private final Map<S, Integer> owners = new IdentityHashMap<>();
    private final Map<Integer, Entry<S, C>> active = new TreeMap<>();
    private List<Entry<S, C>> snapshot = List.of();
    private boolean changed;

    synchronized void reset(List<S> sections) {
        owners.clear();
        active.clear();
        for (int i = 0; i < sections.size(); i++) owners.put(sections.get(i), i);
        snapshot = List.of();
        changed = false;
    }

    synchronized void publish(S section, C compiled, boolean present) {
        Integer slot = owners.get(section);
        if (slot == null) return; // External sections and retired world owners have separate paths.
        if (present) active.put(slot, new Entry<>(section, compiled));
        else active.remove(slot);
        changed = true;
    }

    synchronized void remove(S section) {
        Integer slot = owners.get(section);
        if (slot != null && active.remove(slot) != null) changed = true;
    }

    synchronized List<Entry<S, C>> snapshot() {
        if (changed) {
            snapshot = List.copyOf(active.values());
            changed = false;
        }
        return snapshot;
    }
}
