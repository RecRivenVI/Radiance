package com.radiance.api.render;

/** A declared unsupported source draw. Resource failures and renderer bugs are different errors. */
public final class UnsupportedDraw extends UnsupportedOperationException {
    public UnsupportedDraw(String reason) {
        super(reason);
    }

    public static void run(String operation, Runnable draw) {
        try {
            draw.run();
        } catch (UnsupportedDraw refusal) {
            com.radiance.client.render.RenderCaptureContract.reportDiscard(
                    operation, refusal.getMessage());
        }
    }
}
