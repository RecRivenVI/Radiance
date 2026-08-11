package com.radiance.client.proxy.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RendererShutdownTest {
    @Test
    void crashBeforeRendererInitializationDoesNotCallNativeClose() {
        AtomicInteger closes = new AtomicInteger();

        Throwable failure = RendererShutdown.closeIfInitialized(false, closes::incrementAndGet);

        assertNull(failure);
        assertEquals(0, closes.get());
    }

    @Test
    void initializedRendererIsClosedAtTheFatalExitBoundary() {
        AtomicInteger closes = new AtomicInteger();

        Throwable failure = RendererShutdown.closeIfInitialized(true, closes::incrementAndGet);

        assertNull(failure);
        assertEquals(1, closes.get());
    }

    @Test
    void closeFailureIsReportedWithoutReplacingTheOriginalCrashPath() {
        Error expected = new AssertionError("close failed");

        Throwable failure = RendererShutdown.closeIfInitialized(true, () -> {
            throw expected;
        });

        assertSame(expected, failure);
    }
}
