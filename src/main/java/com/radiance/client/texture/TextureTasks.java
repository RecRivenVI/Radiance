package com.radiance.client.texture;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Ownership of CPU work before it reaches the native texture queue. The supplied monitor
 * also guards native allocation/release/use, so checking a ticket cannot race its use. */
public final class TextureTasks {
    public record Owner(int id, long generation) {}
    private static final class State {
        final Owner owner;
        long imageVersion;
        State(Owner owner) { this.owner = owner; }
    }

    private final Object monitor;
    private final Map<Integer, State> owners = new HashMap<>();
    private final Set<Task> pending = new LinkedHashSet<>();
    private long generation;

    public TextureTasks(Object monitor) { this.monitor = monitor; }

    public Owner register(int id) {
        synchronized (monitor) {
            if (id <= 0 || owners.containsKey(id)) throw new IllegalStateException("Texture already owned: " + id);
            Owner owner = new Owner(id, Math.incrementExact(generation));
            generation = owner.generation();
            owners.put(id, new State(owner));
            return owner;
        }
    }

    public Owner owner(int id) {
        synchronized (monitor) { State state = owners.get(id); return state == null ? null : state.owner; }
    }

    public boolean use(Owner owner, Runnable action) {
        synchronized (monitor) {
            if (!current(owner)) return false;
            action.run();
            return true;
        }
    }

    public boolean release(Owner owner, Runnable release) {
        synchronized (monitor) {
            if (!current(owner)) return false;
            owners.remove(owner.id());
            try { cancelMatching(owner, false); }
            catch (Throwable failure) {
                try { release.run(); } catch (Throwable nativeFailure) { failure.addSuppressed(nativeFailure); }
                throw failure;
            }
            release.run();
            return true;
        }
    }

    public void replaceImage(int id) {
        synchronized (monitor) {
            State state = owners.get(id);
            if (state == null) throw new IllegalStateException("Texture not owned: " + id);
            state.imageVersion = Math.incrementExact(state.imageVersion);
            cancelMatching(state.owner, true);
        }
    }

    public void invalidateUploads() {
        synchronized (monitor) {
            for (State state : owners.values()) state.imageVersion = Math.incrementExact(state.imageVersion);
            cancel(task -> task.imageSensitive);
        }
    }

    public void close() {
        synchronized (monitor) {
            owners.clear();
            cancel(task -> true);
        }
    }

    private boolean current(Owner owner) {
        State state = owner == null ? null : owners.get(owner.id());
        return state != null && state.owner.equals(owner);
    }

    private void cancelMatching(Owner owner, boolean imagesOnly) {
        cancel(task -> task.owner.equals(owner) && (!imagesOnly || task.imageSensitive));
    }

    private void cancel(java.util.function.Predicate<Task> matches) {
        Throwable failure = null;
        for (Task task : pending.toArray(Task[]::new)) {
            if (!matches.test(task)) continue;
            try { task.close(); }
            catch (Throwable problem) {
                if (failure == null) failure = problem;
                else if (failure != problem) failure.addSuppressed(problem);
            }
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException("Texture task cleanup failed", failure);
    }

    /** Used by both glyph and NativeImage scheduling. Cleanup runs exactly once, even if the
     * render queue rejects or abandons a task, or invalidation precedes queue execution. */
    public Task submit(Owner owner, boolean imageSensitive, Consumer<Runnable> executor,
                       Runnable action, Runnable cleanup) {
        synchronized (monitor) {
            if (!current(owner)) { cleanup.run(); return null; }
            Task task = new Task(owner, owners.get(owner.id()).imageVersion, imageSensitive, action, cleanup);
            pending.add(task);
            try { executor.accept(task); }
            catch (Throwable failure) {
                try { task.close(); } catch (Throwable closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
            return task;
        }
    }

    public final class Task implements Runnable, AutoCloseable {
        private final Owner owner;
        private final long imageVersion;
        private final boolean imageSensitive;
        private Runnable action;
        private Runnable cleanup;
        private Task(Owner owner, long imageVersion, boolean imageSensitive, Runnable action, Runnable cleanup) {
            this.owner = owner; this.imageVersion = imageVersion; this.imageSensitive = imageSensitive;
            this.action = action; this.cleanup = cleanup;
        }
        @Override public void run() {
            synchronized (monitor) {
                if (action == null) return;
                Runnable work = action;
                action = null;
                pending.remove(this);
                try {
                    if (current(owner) && (!imageSensitive || owners.get(owner.id()).imageVersion == imageVersion))
                        work.run();
                } catch (Throwable failure) {
                    try { close(); } catch (Throwable closeFailure) { failure.addSuppressed(closeFailure); }
                    throw failure;
                } finally { close(); }
            }
        }
        @Override public void close() {
            synchronized (monitor) {
                action = null;
                pending.remove(this);
                Runnable disposer = cleanup;
                cleanup = null;
                if (disposer != null) disposer.run();
            }
        }
    }
}
