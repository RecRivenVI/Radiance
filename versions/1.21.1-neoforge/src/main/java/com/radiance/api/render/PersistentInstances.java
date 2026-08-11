package com.radiance.api.render;

import java.lang.ref.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Submission-side change journal, not a second scene database. It keeps only the last parameter
 * bytes and weak source ownership. Native owns persistent model/instance records. Visibility is a
 * per-view selection, never an object-removal signal. Mutations commit only after native accepts.
 */
public final class PersistentInstances {
    public interface OwnerState {
        boolean radiance$isRetired();
    }

    private static final int MAX_RECORDS = 65536;
    private static final Map<Long, State> STATES = new HashMap<>();
    private static final ReferenceQueue<Object> COLLECTED = new ReferenceQueue<>();
    private static final Map<Owner, Owner> OWNERS = new HashMap<>();
    private static final Set<Long> REMOVED = new LinkedHashSet<>();
    private static final long[] NO_REMOVALS = new long[0];
    private static long changes, selections;

    private static final class Owner extends WeakReference<Object> {
        final int hash;
        final Set<Long> records = new HashSet<>();

        Owner(Object source, ReferenceQueue<Object> queue) {
            super(source, queue);
            hash = System.identityHashCode(source);
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(Object other) {
            return this == other || other instanceof Owner o && get() != null && get() == o.get();
        }
    }

    private static final class State {
        Owner owner;
        String group;
        final byte[] bytes = new byte[com.radiance.render.contract.RenderAbi.RIGID_RECORD_BYTES];
        final ByteBuffer values = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.nativeOrder());

        State(Owner owner, String group) {
            this.owner = owner;
            this.group = group;
        }
    }

    private PersistentInstances() {}

    public static synchronized void removeOwner(Object source) {
        Owner lookup = new Owner(source, null);
        Owner owner = OWNERS.remove(lookup);
        if (owner != null) {
            REMOVED.addAll(owner.records);
            owner.records.forEach(STATES::remove);
        }
    }

    static synchronized void collect() {
        Owner owner;
        while ((owner = (Owner) COLLECTED.poll()) != null) {
            OWNERS.remove(owner);
            REMOVED.addAll(owner.records);
            owner.records.forEach(STATES::remove);
        }
    }

    static synchronized boolean canPersist(long identity, Object owner) {
        return owner != null && (STATES.containsKey(identity) || STATES.size() < MAX_RECORDS);
    }

    static synchronized boolean changed(long identity, ByteBuffer data, int offset, String group) {
        State old = STATES.get(identity);
        if (old == null || !old.group.equals(group)) return true;
        // A CPU source address and local group-table ordinal are not persistent content.
        for (int i = 0; i < old.bytes.length; i++)
            if ((i < 16 || i >= 24 && i < 128 || i >= 132) && old.bytes[i] != data.get(offset + i))
                return true;
        return false;
    }

    static synchronized boolean matches(
            long identity,
            Object source,
            long model,
            int type,
            int texture,
            int vertices,
            double x,
            double y,
            double z,
            int mask,
            org.joml.Matrix4fc transform,
            String group,
            com.radiance.render.contract.DrawStateSnapshot colour,
            long space,
            LocalSurface surface,
            int coordinates) {
        State old = STATES.get(identity);
        if (old == null || old.owner.get() != source || !old.group.equals(group)) return false;
        ByteBuffer data = old.values;
        if (data.getLong(0) != model
                || data.getInt(112) != type
                || data.getInt(116) != texture
                || data.getInt(120) != vertices
                || data.getInt(124) != mask
                || data.getLong(152) != space
                || data.getInt(132) != coordinates
                || Double.doubleToRawLongBits(data.getDouble(24)) != Double.doubleToRawLongBits(x)
                || Double.doubleToRawLongBits(data.getDouble(32)) != Double.doubleToRawLongBits(y)
                || Double.doubleToRawLongBits(data.getDouble(40)) != Double.doubleToRawLongBits(z))
            return false;
        for (int column = 0; column < 4; column++)
            for (int row = 0; row < 4; row++)
                if (Float.floatToRawIntBits(transform.get(column, row))
                        != data.getInt(48 + (column * 4 + row) * 4)) return false;
        return surface.matches(data, com.radiance.render.contract.RenderAbi.RIGID_SURFACE_OFFSET)
                && Float.floatToRawIntBits(colour.red()) == data.getInt(136)
                && Float.floatToRawIntBits(colour.green()) == data.getInt(140)
                && Float.floatToRawIntBits(colour.blue()) == data.getInt(144)
                && Float.floatToRawIntBits(colour.alpha()) == data.getInt(148);
    }

    static synchronized void accepted(
            long identity, Object source, ByteBuffer data, int offset, String group) {
        State old = STATES.get(identity);
        Owner owner =
                old != null && old.owner.get() == source
                        ? old.owner
                        : OWNERS.get(new Owner(source, null));
        if (owner == null) {
            owner = new Owner(source, COLLECTED);
            OWNERS.put(owner, owner);
        }
        if (old != null && old.owner != owner) old.owner.records.remove(identity);
        State state = old == null ? new State(owner, group) : old;
        data.get(offset, state.bytes);
        state.owner = owner;
        state.group = group;
        if (old == null) STATES.put(identity, state);
        owner.records.add(identity);
        REMOVED.remove(identity);
        changes++;
    }

    public static void flushRemovals() {
        synchronized (PersistentInstances.class) {
            collect();
            if (REMOVED.isEmpty()) return;
        }
        try (var batch = new LocalModelBatch(0)) {
            batch.submit();
        }
    }

    public static synchronized boolean known(long id) {
        return STATES.containsKey(id);
    }

    public static void selectOwner(Object source) {
        long[] identities;
        synchronized (PersistentInstances.class) {
            var owner = OWNERS.get(new Owner(source, null));
            if (owner == null) return;
            identities = owner.records.stream().mapToLong(Long::longValue).toArray();
        }
        try (var batch = new LocalModelBatch(0)) {
            for (long identity : identities) batch.select(identity, source);
            batch.submit();
        }
    }

    static synchronized int available() {
        return MAX_RECORDS - STATES.size();
    }

    static synchronized long[] removals() {
        collect();
        if (REMOVED.isEmpty()) return NO_REMOVALS;
        return REMOVED.stream().mapToLong(Long::longValue).toArray();
    }

    static synchronized void refused(long identity) {
        State old = STATES.remove(identity);
        if (old != null) old.owner.records.remove(identity);
    }

    static synchronized void removed(long[] ids) {
        for (long id : ids) REMOVED.remove(id);
    }

    static synchronized void selected(int count) {
        selections += count;
    }

    public static synchronized long[] counters() {
        return new long[] {STATES.size(), changes, selections};
    }

    public static synchronized void invalidate() {
        STATES.clear();
        OWNERS.clear();
        REMOVED.clear();
        while (COLLECTED.poll() != null) {}
        ;
    }
}
