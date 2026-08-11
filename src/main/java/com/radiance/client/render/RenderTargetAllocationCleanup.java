package com.radiance.client.render;

import java.util.function.IntConsumer;

/** Releases a partially-created render target without abandoning later resources after one cleanup failure. */
public final class RenderTargetAllocationCleanup {

    private RenderTargetAllocationCleanup() {
    }

    public static void release(int framebufferId, int colorTextureId, int depthTextureId,
        Runnable unbindFramebuffer, IntConsumer releaseTexture, IntConsumer deleteFramebuffer) {
        Throwable failure = null;
        if (framebufferId >= 0) {
            failure = run(unbindFramebuffer, failure);
        }
        if (depthTextureId >= 0) {
            failure = run(() -> releaseTexture.accept(depthTextureId), failure);
        }
        if (colorTextureId >= 0) {
            failure = run(() -> releaseTexture.accept(colorTextureId), failure);
        }
        if (framebufferId >= 0) {
            failure = run(() -> deleteFramebuffer.accept(framebufferId), failure);
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private static Throwable run(Runnable action, Throwable priorFailure) {
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            if (priorFailure == null) {
                return failure;
            }
            priorFailure.addSuppressed(failure);
        }
        return priorFailure;
    }
}
