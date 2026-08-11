package com.radiance.compatibility.voxy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class VoxyCaptureLockTest {
    @Test
    void snapshotWaitsForWholeSourceUpdateAndOwnsItsArray() throws Exception {
        long[] input = {1, 1, 1, 1};
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        long before = VoxyCaptureLock.revision();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer =
                    executor.submit(
                            () -> {
                                VoxyCaptureLock.LOCK.writeLock().lock();
                                try {
                                    input[0] = 2;
                                    entered.countDown();
                                    if (!release.await(5, TimeUnit.SECONDS))
                                        throw new AssertionError("writer watchdog");
                                    for (int i = 1; i < input.length; i++) input[i] = 2;
                                    VoxyCaptureLock.changed();
                                } catch (InterruptedException e) {
                                    throw new AssertionError(e);
                                } finally {
                                    VoxyCaptureLock.LOCK.writeLock().unlock();
                                }
                            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var readerEntered = new CountDownLatch(1);
            var reader =
                    executor.submit(
                            () -> {
                                readerEntered.countDown();
                                return VoxyCaptureLock.copy(input);
                            });
            try {
                assertTrue(readerEntered.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> reader.get(100, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            writer.get(5, TimeUnit.SECONDS);
            long[] snapshot = reader.get(5, TimeUnit.SECONDS);
            assertArrayEquals(new long[] {2, 2, 2, 2}, snapshot);
            input[0] = 3;
            assertEquals(2, snapshot[0]);
            assertTrue(VoxyCaptureLock.revision() > before);
        } finally {
            release.countDown();
        }
    }

    @Test
    void unownedMutationIsRejected() {
        assertThrows(IllegalStateException.class, VoxyCaptureLock::changed);
    }
}
