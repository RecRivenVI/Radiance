package com.radiance.compatibility.simulated;

import java.util.ArrayList;
import java.util.List;

/** Runs every restoration and keeps the original draw/compile failure as the primary error. */
public final class SimulatedRenderRecovery {
    private final List<Runnable> restorations = new ArrayList<>();

    public void add(Runnable restoration) { restorations.add(restoration); }

    public void run(Runnable draw) {
        Throwable failure = null;
        try {
            draw.run();
        } catch (RuntimeException | Error error) {
            failure = error;
        }
        for (Runnable restoration : restorations) {
            try {
                restoration.run();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }
}
