package com.radiance.client.proxy.world;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Presence only: never filters by camera, distance, geometry layers or block-entity animation. */
final class SectionPresenceIndex<S, C> {
    private record Owner(int slot) {}

    record Entry<S, C>(S section, C compiled, Owner owner) {}

    private final Map<S, Owner> owners = new IdentityHashMap<>();
    private final Map<Integer, Entry<S, C>> active = new TreeMap<>();
    private List<Entry<S, C>> snapshot = List.of();
    private boolean changed;

    synchronized void reset(List<S> sections) {
        owners.clear();
        active.clear();
        for (int i = 0; i < sections.size(); i++) owners.put(sections.get(i), new Owner(i));
        snapshot = List.of();
        changed = false;
    }

    synchronized void publish(S section, C compiled, boolean present) {
        Owner owner = owners.get(section);
        if (owner == null)
            return; // External sections and retired world owners have separate paths.
        if (present) active.put(owner.slot, new Entry<>(section, compiled, owner));
        else active.remove(owner.slot);
        changed = true;
    }

    synchronized void remove(S section) {
        Owner owner = owners.get(section);
        if (owner == null) return;
        // Reset/reposition ends ownership; ordinary recompilation does not. A frame may
        // finish reading its immutable old compiled list after a worker publishes the new one.
        owners.put(section, new Owner(owner.slot));
        if (active.remove(owner.slot) != null) changed = true;
    }

    synchronized boolean isCurrent(Entry<S, C> entry) {
        return owners.get(entry.section) == entry.owner;
    }

    synchronized List<Entry<S, C>> snapshot() {
        if (changed) {
            snapshot = List.copyOf(active.values());
            changed = false;
        }
        return snapshot;
    }
}
