package com.radiance.client.proxy.world;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded admission and latest-request coalescing for external section builds.
 *
 * <p>The caller transfers an acquired permit to the submitted task. Expensive preparation only
 * runs after admission, and every failure path restores both the permit and the request state.</p>
 */
final class ExternalSectionBuildScheduler<T> {
    enum FailurePhase {
        PREPARE,
        SUBMIT,
        RUN
    }

    @FunctionalInterface
    interface Preparer<T> {
        PreparedBuild prepare(Ticket<T> ticket) throws Exception;
    }

    @FunctionalInterface
    interface PreparedBuild {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface Submitter {
        void submit(Runnable task);
    }

    @FunctionalInterface
    interface FailureHandler<T> {
        void failed(Ticket<T> ticket, FailurePhase phase, Exception failure);
    }

    static final class State {
        private final AtomicLong requestedRevision = new AtomicLong();
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        boolean isRunning() {
            return running.get();
        }
    }

    static final class Ticket<T> {
        private final long key;
        private final T owner;
        private final State state;
        private final long revision;

        private Ticket(long key, T owner, State state, long revision) {
            this.key = key;
            this.owner = owner;
            this.state = state;
            this.revision = revision;
        }

        long key() {
            return key;
        }

        T owner() {
            return owner;
        }

        long revision() {
            return revision;
        }

        boolean isCurrent() {
            return !state.cancelled.get() && state.requestedRevision.get() == revision;
        }
    }

    private final ConcurrentHashMap<Long, Ticket<T>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight;
    private final int capacity;
    private final int frameBudget;

    ExternalSectionBuildScheduler(AtomicInteger inFlight, int capacity, int frameBudget) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        if (frameBudget < 1) throw new IllegalArgumentException("Frame budget must be positive");
        this.inFlight = inFlight;
        this.capacity = capacity;
        this.frameBudget = frameBudget;
    }

    State createState() {
        return new State();
    }

    void request(long key, T owner, State state) {
        if (state.cancelled.get()) return;
        long revision = state.requestedRevision.incrementAndGet();
        pending.put(key, new Ticket<>(key, owner, state, revision));
    }

    void cancel(long key, State state) {
        state.cancelled.set(true);
        state.requestedRevision.incrementAndGet();
        pending.computeIfPresent(key,
            (ignored, request) -> request.state == state ? null : request);
    }

    void clearPending() {
        pending.clear();
    }

    int schedule(Preparer<T> preparer, Submitter submitter, FailureHandler<T> failures) {
        int admitted = 0;
        int visited = 0;
        Set<Long> visitedKeys = new HashSet<>();
        for (Ticket<T> request : pending.values()) {
            if (!visitedKeys.add(request.key)) continue;
            if (visited++ >= frameBudget) break;
            if (!request.isCurrent() || !request.state.running.compareAndSet(false, true)) {
                continue;
            }

            ChunkBuildPermit permit = ChunkBuildPermit.tryAcquire(inFlight, capacity);
            if (permit == null) {
                request.state.running.set(false);
                break;
            }
            if (!pending.remove(request.key, request)) {
                permit.close();
                request.state.running.set(false);
                continue;
            }

            PreparedBuild prepared;
            try {
                prepared = request.isCurrent() ? preparer.prepare(request) : null;
            } catch (Exception failure) {
                restore(request, permit);
                failures.failed(request, FailurePhase.PREPARE, failure);
                continue;
            }
            if (prepared == null) {
                restore(request, permit);
                continue;
            }

            try {
                submitter.submit(() -> run(request, prepared, permit, failures));
                admitted++;
            } catch (RuntimeException failure) {
                restore(request, permit);
                failures.failed(request, FailurePhase.SUBMIT, failure);
            }
        }
        return admitted;
    }

    int pendingCount() {
        return pending.size();
    }

    private void run(Ticket<T> request, PreparedBuild prepared, ChunkBuildPermit permit,
        FailureHandler<T> failures) {
        boolean completed = false;
        Exception taskFailure = null;
        try {
            if (request.isCurrent()) {
                prepared.run();
                completed = true;
            }
        } catch (Exception failure) {
            taskFailure = failure;
        } finally {
            permit.close();
            request.state.running.set(false);
            if (!completed || !request.isCurrent()) {
                requeueLatest(request);
            }
        }
        if (taskFailure != null) {
            failures.failed(request, FailurePhase.RUN, taskFailure);
        }
    }

    private void restore(Ticket<T> request, ChunkBuildPermit permit) {
        permit.close();
        request.state.running.set(false);
        requeueLatest(request);
    }

    private void requeueLatest(Ticket<T> request) {
        if (request.state.cancelled.get()) return;
        long latest = request.state.requestedRevision.get();
        pending.compute(request.key, (ignored, current) ->
            current == null || current.revision < latest
                ? new Ticket<>(request.key, request.owner, request.state, latest)
                : current);
    }
}
