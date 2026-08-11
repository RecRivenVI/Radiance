package com.radiance.client.render;

import java.util.function.IntUnaryOperator;

/**
 * Keeps queued GuiGraphics batches inside their actual source role, including exceptional exits.
 */
public final class GuiDrawScope {
    private GuiDrawScope() {}

    public static void run(int role, Runnable flush, IntUnaryOperator setRole, Runnable draw) {
        flush.run();
        int previous = setRole.applyAsInt(role);
        Throwable failure = null;
        try {
            draw.run();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            try {
                flush.run();
            } catch (RuntimeException | Error cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            }
            try {
                setRole.applyAsInt(previous);
            } catch (RuntimeException | Error cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            }
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
        }
    }
}
