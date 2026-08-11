package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ExternalSectionBuildSchedulerTest {
    @Test
    void capacityIsAcquiredBeforePreparationAndDeferredWorkResumes() {
        AtomicInteger inFlight = new AtomicInteger();
        ExternalSectionBuildScheduler<String> scheduler =
            new ExternalSectionBuildScheduler<>(inFlight, 1, 4);
        ExternalSectionBuildScheduler.State first = scheduler.createState();
        ExternalSectionBuildScheduler.State second = scheduler.createState();
        Queue<Runnable> workers = new ArrayDeque<>();
        AtomicInteger preparations = new AtomicInteger();
        List<String> completed = new ArrayList<>();

        scheduler.request(1, "first", first);
        scheduler.request(2, "second", second);
        assertEquals(1, scheduler.schedule(ticket -> {
            preparations.incrementAndGet();
            return () -> completed.add(ticket.owner());
        }, workers::add, failingHandler()));
        assertEquals(1, preparations.get());
        assertEquals(1, inFlight.get());

        assertEquals(0, scheduler.schedule(ticket -> {
            preparations.incrementAndGet();
            return () -> completed.add(ticket.owner());
        }, workers::add, failingHandler()));
        assertEquals(1, preparations.get(), "saturated scheduling must not create another region");

        workers.remove().run();
        assertEquals(0, inFlight.get());
        assertEquals(1, scheduler.schedule(ticket -> {
            preparations.incrementAndGet();
            return () -> completed.add(ticket.owner());
        }, workers::add, failingHandler()));
        workers.remove().run();

        assertEquals(2, preparations.get());
        assertEquals(2, completed.size());
        assertTrue(completed.contains("first"));
        assertTrue(completed.contains("second"));
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void submissionRejectionAndTaskFailureRestoreCapacityAndRequestState() {
        AtomicInteger inFlight = new AtomicInteger();
        ExternalSectionBuildScheduler<String> scheduler =
            new ExternalSectionBuildScheduler<>(inFlight, 1, 1);
        ExternalSectionBuildScheduler.State state = scheduler.createState();
        List<ExternalSectionBuildScheduler.FailurePhase> failures = new ArrayList<>();
        scheduler.request(1, "section", state);

        assertEquals(0, scheduler.schedule(ticket -> {
            throw new IllegalStateException("injected preparation failure");
        }, task -> { }, (ticket, phase, failure) -> failures.add(phase)));
        assertEquals(List.of(ExternalSectionBuildScheduler.FailurePhase.PREPARE), failures);
        assertEquals(0, inFlight.get());
        assertFalse(state.isRunning());
        assertEquals(1, scheduler.pendingCount());

        assertEquals(0, scheduler.schedule(ticket -> () -> { }, task -> {
            throw new RejectedExecutionException("injected");
        }, (ticket, phase, failure) -> failures.add(phase)));
        assertEquals(List.of(ExternalSectionBuildScheduler.FailurePhase.PREPARE,
            ExternalSectionBuildScheduler.FailurePhase.SUBMIT), failures);
        assertEquals(0, inFlight.get());
        assertFalse(state.isRunning());
        assertEquals(1, scheduler.pendingCount());

        Queue<Runnable> workers = new ArrayDeque<>();
        assertEquals(1, scheduler.schedule(ticket -> () -> {
            throw new IllegalStateException("injected worker failure");
        }, workers::add, (ticket, phase, failure) -> failures.add(phase)));
        workers.remove().run();
        assertEquals(List.of(ExternalSectionBuildScheduler.FailurePhase.PREPARE,
            ExternalSectionBuildScheduler.FailurePhase.SUBMIT,
            ExternalSectionBuildScheduler.FailurePhase.RUN), failures);
        assertEquals(0, inFlight.get());
        assertFalse(state.isRunning());
        assertEquals(1, scheduler.pendingCount());

        assertEquals(1, scheduler.schedule(ticket -> () -> { }, workers::add, failingHandler()));
        workers.remove().run();
        assertEquals(0, scheduler.pendingCount());
        assertEquals(0, inFlight.get());
    }

    @Test
    void cancellationAndOwnerReplacementCannotPublishAnOldTask() {
        AtomicInteger inFlight = new AtomicInteger();
        ExternalSectionBuildScheduler<String> scheduler =
            new ExternalSectionBuildScheduler<>(inFlight, 1, 2);
        ExternalSectionBuildScheduler.State oldState = scheduler.createState();
        Queue<Runnable> workers = new ArrayDeque<>();
        List<String> published = new ArrayList<>();

        scheduler.request(7, "old", oldState);
        assertEquals(1, scheduler.schedule(ticket -> () -> published.add(ticket.owner()),
            workers::add, failingHandler()));
        scheduler.cancel(7, oldState);

        ExternalSectionBuildScheduler.State newState = scheduler.createState();
        scheduler.request(7, "new", newState);
        workers.remove().run();
        assertTrue(published.isEmpty(), "cancelled generation must not run its prepared build");

        assertEquals(1, scheduler.schedule(ticket -> () -> published.add(ticket.owner()),
            workers::add, failingHandler()));
        workers.remove().run();
        assertEquals(List.of("new"), published);
        assertEquals(0, inFlight.get());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void diagnosticFailureCannotLeakCapacityOrRunningState() {
        AtomicInteger inFlight = new AtomicInteger();
        ExternalSectionBuildScheduler<String> scheduler =
            new ExternalSectionBuildScheduler<>(inFlight, 1, 1);
        ExternalSectionBuildScheduler.State state = scheduler.createState();
        scheduler.request(1, "section", state);

        assertThrows(AssertionError.class, () -> scheduler.schedule(ticket -> {
            throw new IllegalStateException("injected preparation failure");
        }, task -> { }, failingHandler()));
        assertEquals(0, inFlight.get());
        assertFalse(state.isRunning());
        assertEquals(1, scheduler.pendingCount());

        Queue<Runnable> workers = new ArrayDeque<>();
        assertEquals(1, scheduler.schedule(ticket -> () -> {
            throw new IllegalStateException("injected worker failure");
        }, workers::add, failingHandler()));
        assertThrows(AssertionError.class, () -> workers.remove().run());
        assertEquals(0, inFlight.get());
        assertFalse(state.isRunning());
        assertEquals(1, scheduler.pendingCount());
    }

    private static <T> ExternalSectionBuildScheduler.FailureHandler<T> failingHandler() {
        return (ticket, phase, failure) -> {
            throw new AssertionError("Unexpected " + phase + " failure", failure);
        };
    }
}
