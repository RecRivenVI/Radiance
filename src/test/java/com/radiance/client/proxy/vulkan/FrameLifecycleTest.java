package com.radiance.client.proxy.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameLifecycleTest {

    @Test
    void retryLaterNeverMakesAnOldFrameRenderable() {
        FrameLifecycle lifecycle = new FrameLifecycle();
        lifecycle.markAcquired();
        lifecycle.markPresentedOrClosed();

        assertEquals(FrameLifecycle.AcquireOutcome.RETRY_LATER,
            lifecycle.acceptAcquireResult(FrameLifecycle.VK_NOT_READY));
        assertFalse(lifecycle.acquired());
    }

    @Test
    void onlySuccessfulAcquirePublishesAFrame() {
        FrameLifecycle lifecycle = new FrameLifecycle();

        assertEquals(FrameLifecycle.AcquireOutcome.FAILED, lifecycle.acceptAcquireResult(-4));
        assertFalse(lifecycle.acquired());
        assertEquals(FrameLifecycle.AcquireOutcome.ACQUIRED,
            lifecycle.acceptAcquireResult(FrameLifecycle.VK_SUCCESS));
        assertTrue(lifecycle.acquired());
    }
}
