package com.radiance.client.proxy.world;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/** One request per section; nearby background sources take turns without scanning the backlog. */
final class SectionBuildQueue<T> extends AbstractQueue<T> {
    private final ToLongFunction<T> identity;
    private final Predicate<T> background;
    private final Map<Long, Entry> entries = new HashMap<>();
    private final Heap priority;
    private final Comparator<T> priorityOrder;
    private final ToLongFunction<T> sourceOf;
    private final ToDoubleFunction<T> bandOf;
    private final Map<Long, Source> sources = new HashMap<>();
    private final TreeMap<Double, TreeMap<Long, Source>> bands = new TreeMap<>();
    private long lastSource = Long.MAX_VALUE;

    private final class Entry {
        final long id;
        T value;
        int priorityIndex = -1;
        int sourceIndex = -1;
        Source source;

        Entry(long id, T value) {
            this.id = id;
            this.value = value;
        }
    }

    private final class Source {
        final long id;
        final Heap heap = new Heap(true);
        double band;
        boolean indexed;

        Source(long id) {
            this.id = id;
        }
    }

    private final class Heap {
        final ArrayList<Entry> values = new ArrayList<>();
        final boolean source;

        Heap(boolean source) {
            this.source = source;
        }

        void position(Entry entry, int index) {
            if (source) entry.sourceIndex = index;
            else entry.priorityIndex = index;
        }

        void place(int index, Entry entry) {
            values.set(index, entry);
            position(entry, index);
        }

        boolean before(Entry a, Entry b) {
            return priorityOrder.compare(a.value, b.value) < 0;
        }

        void up(int index) {
            Entry entry = values.get(index);
            while (index > 0) {
                int parent = (index - 1) / 2;
                if (!before(entry, values.get(parent))) break;
                place(index, values.get(parent));
                index = parent;
            }
            place(index, entry);
        }

        void down(int index) {
            Entry entry = values.get(index);
            while (index * 2 + 1 < values.size()) {
                int child = index * 2 + 1;
                if (child + 1 < values.size() && before(values.get(child + 1), values.get(child)))
                    child++;
                if (!before(values.get(child), entry)) break;
                place(index, values.get(child));
                index = child;
            }
            place(index, entry);
        }

        void add(Entry entry) {
            int index = values.size();
            values.add(entry);
            position(entry, index);
            up(index);
        }

        void remove(int index) {
            Entry removed = values.get(index);
            Entry tail = values.remove(values.size() - 1);
            position(removed, -1);
            if (index >= values.size()) return;
            place(index, tail);
            if (index > 0 && before(tail, values.get((index - 1) / 2))) up(index);
            else down(index);
        }
    }

    SectionBuildQueue(
            ToLongFunction<T> identity,
            Comparator<T> priority,
            ToLongFunction<T> source,
            ToDoubleFunction<T> band,
            Predicate<T> background) {
        this.identity = identity;
        this.background = background;
        this.priorityOrder = priority;
        this.sourceOf = source;
        this.bandOf = band;
        this.priority = new Heap(false);
    }

    private void unindex(Source source) {
        if (!source.indexed) return;
        var ring = bands.get(source.band);
        ring.remove(source.id);
        if (ring.isEmpty()) bands.remove(source.band);
        source.indexed = false;
    }

    private void index(Source source) {
        if (source.heap.values.isEmpty()) {
            sources.remove(source.id);
            return;
        }
        source.band = bandOf.applyAsDouble(source.heap.values.getFirst().value);
        bands.computeIfAbsent(source.band, ignored -> new TreeMap<>()).put(source.id, source);
        source.indexed = true;
    }

    @Override
    public synchronized boolean offer(T value) {
        Objects.requireNonNull(value);
        long id = identity.applyAsLong(value);
        // A deferred round must not overwrite a newer request enqueued while it was absent.
        if (entries.containsKey(id)) return true;
        Entry entry = new Entry(id, value);
        entries.put(id, entry);
        priority.add(entry);
        if (background.test(value)) {
            entry.source = sources.computeIfAbsent(sourceOf.applyAsLong(value), Source::new);
            unindex(entry.source);
            entry.source.heap.add(entry);
            index(entry.source);
        }
        return true;
    }

    private T take(Entry entry) {
        entries.remove(entry.id);
        priority.remove(entry.priorityIndex);
        if (entry.source != null) {
            unindex(entry.source);
            entry.source.heap.remove(entry.sourceIndex);
            index(entry.source);
        }
        return entry.value;
    }

    synchronized void removeSection(long section) {
        Entry entry = entries.get(section);
        if (entry != null) take(entry);
    }

    synchronized void update(long section, UnaryOperator<T> update) {
        Entry entry = entries.get(section);
        if (entry == null) return;
        T next = Objects.requireNonNull(update.apply(entry.value));
        if (identity.applyAsLong(next) != section)
            throw new IllegalArgumentException("Section identity changed during reprioritization");
        take(entry);
        offer(next);
    }

    private Entry next(boolean preferBackground) {
        if (priority.values.isEmpty()) return null;
        Entry entry = priority.values.getFirst();
        if ((preferBackground || background.test(entry.value)) && !bands.isEmpty()) {
            var ring = bands.firstEntry().getValue();
            var source = ring.higherEntry(lastSource);
            if (source == null) source = ring.firstEntry();
            entry = source.getValue().heap.values.getFirst();
        }
        return entry;
    }

    synchronized T poll(boolean preferBackground) {
        Entry entry = next(preferBackground);
        if (entry == null) return null;
        // Advance on attempts as well: one capacity/source-blocked owner cannot monopolize a round.
        if (entry.source != null) lastSource = entry.source.id;
        return take(entry);
    }

    @Override
    public synchronized T poll() {
        return poll(false);
    }

    @Override
    public synchronized T peek() {
        Entry entry = next(false);
        return entry == null ? null : entry.value;
    }

    @Override
    public synchronized int size() {
        return entries.size();
    }

    @Override
    public synchronized Iterator<T> iterator() {
        return entries.values().stream().map(entry -> entry.value).toList().iterator();
    }

    synchronized void reprioritize(UnaryOperator<T> update) {
        var updated = new ArrayList<T>(entries.size());
        for (Entry entry : entries.values()) {
            T value = update.apply(entry.value);
            if (value != null && identity.applyAsLong(value) != entry.id)
                throw new IllegalArgumentException(
                        "Section identity changed during reprioritization");
            updated.add(value);
        }
        priority.values.clear();
        sources.clear();
        bands.clear();
        var iterator = entries.values().iterator();
        int index = 0;
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            T value = updated.get(index++);
            if (value == null) {
                iterator.remove();
                continue;
            }
            entry.value = value;
            entry.priorityIndex = priority.values.size();
            priority.values.add(entry);
            entry.sourceIndex = -1;
            entry.source = null;
            if (background.test(value)) {
                entry.source = sources.computeIfAbsent(sourceOf.applyAsLong(value), Source::new);
                entry.sourceIndex = entry.source.heap.values.size();
                entry.source.heap.values.add(entry);
            }
        }
        for (int i = priority.values.size() / 2 - 1; i >= 0; i--) priority.down(i);
        for (Source source : sources.values()) {
            for (int i = source.heap.values.size() / 2 - 1; i >= 0; i--) source.heap.down(i);
            index(source);
        }
    }

    @Override
    public synchronized void clear() {
        entries.clear();
        priority.values.clear();
        sources.clear();
        bands.clear();
        lastSource = Long.MAX_VALUE;
    }
}
