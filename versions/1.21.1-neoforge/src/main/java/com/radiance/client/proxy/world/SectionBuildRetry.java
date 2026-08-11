package com.radiance.client.proxy.world;

import java.util.concurrent.TimeUnit;

/** A failed current revision retains its publication and retries with bounded backoff. */
final class SectionBuildRetry {
    private long failedGeneration = -1, retryAt, reportAt;
    private int attempts;

    synchronized boolean allows(long generation, long now) {
        return generation != failedGeneration || now >= retryAt;
    }

    synchronized boolean failed(long generation, long now) {
        if (generation != failedGeneration) attempts = 0;
        failedGeneration = generation;
        attempts = Math.min(attempts + 1, 8);
        long delay =
                Math.min(
                        TimeUnit.MILLISECONDS.toNanos(250) << (attempts - 1),
                        TimeUnit.SECONDS.toNanos(30));
        retryAt = now + delay;
        boolean report = reportAt == 0 || now >= reportAt;
        if (report) reportAt = now + TimeUnit.SECONDS.toNanos(30);
        return report;
    }

    synchronized long delayMillis(long now) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, retryAt - now));
    }

    synchronized void succeeded() {
        failedGeneration = -1;
        retryAt = reportAt = 0;
        attempts = 0;
    }
}
