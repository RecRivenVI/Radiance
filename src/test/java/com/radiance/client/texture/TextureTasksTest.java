package com.radiance.client.texture;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises the same submit/invalidate/execute boundary used by NativeImage and glyphs;
 * the sink stands in for JNI so stale writes and releases are observable without a renderer. */
final class TextureTasksTest {
    private final TextureTasks tasks = new TextureTasks(new Object());
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final Map<Integer, Integer> pixels = new HashMap<>();
    private final AtomicInteger closes = new AtomicInteger();

    private TextureTasks.Owner allocate(int id) { pixels.put(id, 0); return tasks.register(id); }
    private TextureTasks.Task upload(TextureTasks.Owner owner, int value) {
        return tasks.submit(owner, true, queue::add, () -> pixels.put(owner.id(), value), closes::incrementAndGet);
    }
    private void release(TextureTasks.Owner owner) { tasks.release(owner, () -> pixels.remove(owner.id())); }
    private void drain() { while (!queue.isEmpty()) queue.remove().run(); }

    @Test void oldQueuedUploadCannotWriteReusedName() {
        var old = allocate(7);
        upload(old, 99);
        release(old);
        var next = allocate(7);
        upload(next, 42);
        drain();
        assertEquals(42, pixels.get(7));
        assertEquals(next, tasks.owner(7));
        assertEquals(2, closes.get());
    }

    @Test void delayedReleaseAndCancellationCannotTouchNewOwner() {
        var old = allocate(3);
        var pending = upload(old, 5);
        tasks.submit(old, false, queue::add, () -> release(old), () -> {});
        release(old);
        var next = allocate(3);
        pixels.put(3, 88);
        pending.close();
        drain();
        assertEquals(next, tasks.owner(3));
        assertEquals(88, pixels.get(3));
        assertEquals(1, closes.get());
        assertFalse(tasks.release(old, () -> fail("old release reached JNI")));
    }

    @Test void imageReplacementRejectsOldUploadButNotOwnerRelease() {
        var owner = allocate(2);
        upload(owner, 9);
        tasks.replaceImage(2);
        upload(owner, 17);
        drain();
        assertEquals(17, pixels.get(2));
        assertTrue(tasks.release(owner, () -> pixels.remove(2)));
    }

    @Test void reloadCancelsPreviousTasksAndAdmitsNewVersion() {
        var owner = allocate(2);
        upload(owner, 9);
        tasks.invalidateUploads();
        upload(owner, 17);
        drain();
        assertEquals(17, pixels.get(2));
        assertEquals(2, closes.get());
    }

    @Test void shutdownCleansAbandonedQueueAndLateTasksAreNoOps() {
        var owner = allocate(2);
        upload(owner, 9);
        tasks.close();
        assertEquals(1, closes.get());
        allocate(2);
        drain();
        assertEquals(0, pixels.get(2));
        assertFalse(tasks.use(owner, () -> fail("closed owner was used")));
    }

    @Test void queueRejectionCleansExactlyOnce() {
        var owner = allocate(4);
        assertThrows(IllegalStateException.class, () -> tasks.submit(owner, true,
            ignored -> { throw new IllegalStateException("queue rejected"); },
            () -> fail("rejected upload executed"), closes::incrementAndGet));
        tasks.close();
        assertEquals(1, closes.get());
    }

    @Test void normalUploadAndFailedWorkBothReleasePayload() {
        var owner = allocate(1);
        upload(owner, 23);
        drain();
        assertEquals(23, pixels.get(1));
        assertThrows(IllegalArgumentException.class, () -> tasks.submit(owner, true, Runnable::run,
            () -> { throw new IllegalArgumentException("native failure"); }, closes::incrementAndGet));
        assertEquals(2, closes.get());
    }

    @Test void failingCleanupStillRetiresEveryTaskAndNativeOwner() {
        var owner = allocate(1);
        tasks.submit(owner, true, queue::add, () -> fail("stale upload"),
            () -> { closes.incrementAndGet(); throw new IllegalStateException("payload cleanup"); });
        upload(owner, 9);
        var error = assertThrows(IllegalStateException.class, () -> release(owner));
        assertEquals("payload cleanup", error.getMessage());
        assertNull(tasks.owner(1));
        assertNull(pixels.get(1));
        assertEquals(2, closes.get());
        var next = allocate(1);
        upload(next, 37);
        drain();
        assertEquals(37, pixels.get(1));
    }

    @Test void validationAndUseCannotRaceRelease() throws Exception {
        var owner = allocate(1);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var work = threads.submit(() -> tasks.submit(owner, true, Runnable::run, () -> {
                entered.countDown();
                try { assertTrue(finish.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                pixels.put(1, 6);
            }, closes::incrementAndGet));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var release = threads.submit(() -> release(owner));
            finish.countDown();
            work.get(5, TimeUnit.SECONDS);
            release.get(5, TimeUnit.SECONDS);
            assertNull(pixels.get(1));
        }
        assertEquals(1, closes.get());
    }
}
