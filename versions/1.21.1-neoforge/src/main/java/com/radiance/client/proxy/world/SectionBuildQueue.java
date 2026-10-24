package com.radiance.client.proxy.world;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

/** One queued request per section, indexed both by rendering priority and background age. */
final class SectionBuildQueue<T> extends AbstractQueue<T> {
    private final ToIntFunction<T> identity;
    private final Predicate<T> background;
    private final Map<Integer, Entry> entries = new HashMap<>();
    private final Heap priority;
    private final Heap age;

    private final class Entry {
        final int id;
        T value;
        int priorityIndex = -1;
        int ageIndex = -1;

        Entry(int id, T value) {
            this.id = id;
            this.value = value;
        }
    }

    private final class Heap {
        final ArrayList<Entry> values = new ArrayList<>();
        final Comparator<T> order;
        final boolean aged;

        Heap(Comparator<T> order, boolean aged) {
            this.order = order;
            this.aged = aged;
        }

        void position(Entry entry, int index) {
            if (aged) entry.ageIndex = index;
            else entry.priorityIndex = index;
        }

        void place(int index, Entry entry) {
            values.set(index, entry);
            position(entry, index);
        }

        boolean before(Entry a, Entry b) {
            return order.compare(a.value, b.value) < 0;
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
            ToIntFunction<T> identity,
            Comparator<T> priority,
            Comparator<T> age,
            Predicate<T> background) {
        this.identity = identity;
        this.background = background;
        this.priority = new Heap(priority, false);
        this.age = new Heap(age, true);
    }

    @Override
    public synchronized boolean offer(T value) {
        Objects.requireNonNull(value);
        int id = identity.applyAsInt(value);
        // A deferred round must not overwrite a newer request enqueued while it was absent.
        if (entries.containsKey(id)) return true;
        Entry entry = new Entry(id, value);
        entries.put(id, entry);
        priority.add(entry);
        if (background.test(value)) age.add(entry);
        return true;
    }

    private T take(Entry entry) {
        entries.remove(entry.id);
        priority.remove(entry.priorityIndex);
        if (entry.ageIndex >= 0) age.remove(entry.ageIndex);
        return entry.value;
    }

    synchronized void removeSection(int section) {
        Entry entry = entries.get(section);
        if (entry != null) take(entry);
    }

    synchronized T pollAged(Predicate<T> eligible) {
        if (!age.values.isEmpty() && eligible.test(age.values.getFirst().value))
            return take(age.values.getFirst());
        return poll();
    }

    @Override
    public synchronized T poll() {
        return priority.values.isEmpty() ? null : take(priority.values.getFirst());
    }

    @Override
    public synchronized T peek() {
        return priority.values.isEmpty() ? null : priority.values.getFirst().value;
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
            if (value != null && identity.applyAsInt(value) != entry.id)
                throw new IllegalArgumentException(
                        "Section identity changed during reprioritization");
            updated.add(value);
        }
        priority.values.clear();
        age.values.clear();
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
            entry.ageIndex = -1;
            if (background.test(value)) {
                entry.ageIndex = age.values.size();
                age.values.add(entry);
            }
        }
        for (Heap heap : java.util.List.of(priority, age))
            for (int i = heap.values.size() / 2 - 1; i >= 0; i--) heap.down(i);
    }

    @Override
    public synchronized void clear() {
        entries.clear();
        priority.values.clear();
        age.values.clear();
    }
}
