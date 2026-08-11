package com.radiance.client.proxy.vulkan;

final class FrameLifecycle {

    static final int VK_SUCCESS = 0;
    static final int VK_NOT_READY = 1;

    enum AcquireOutcome {
        ACQUIRED,
        RETRY_LATER,
        FAILED
    }

    private boolean acquired;

    AcquireOutcome acceptAcquireResult(int result) {
        acquired = result == VK_SUCCESS;
        if (acquired) {
            return AcquireOutcome.ACQUIRED;
        }
        return result == VK_NOT_READY ? AcquireOutcome.RETRY_LATER : AcquireOutcome.FAILED;
    }

    boolean acquired() {
        return acquired;
    }

    void markAcquired() {
        acquired = true;
    }

    void markPresentedOrClosed() {
        acquired = false;
    }
}
