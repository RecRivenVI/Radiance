package com.radiance.client.proxy.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SectionBuildRetryTest {
    @Test
    void failedRevisionBacksOffButNewRevisionCanProceed() {
        var retry = new SectionBuildRetry();
        long now = TimeUnit.SECONDS.toNanos(1);
        assertTrue(retry.allows(7, now));
        assertTrue(retry.failed(7, now));
        assertFalse(retry.allows(7, now + TimeUnit.MILLISECONDS.toNanos(249)));
        assertTrue(retry.allows(7, now + TimeUnit.MILLISECONDS.toNanos(250)));
        assertTrue(retry.allows(8, now));
        assertFalse(retry.failed(8, now + 1));
        assertEquals(250, retry.delayMillis(now + 1));
    }

    @Test
    void repeatedFailureIsBoundedAndSuccessResetsAdmission() {
        var retry = new SectionBuildRetry();
        long now = TimeUnit.SECONDS.toNanos(1);
        for (int i = 0; i < 20; i++) {
            retry.failed(9, now);
            long delay = retry.delayMillis(now);
            assertTrue(delay >= 250 && delay <= 30_000);
            now += TimeUnit.MILLISECONDS.toNanos(delay);
        }
        assertEquals(30_000, retry.delayMillis(now - TimeUnit.SECONDS.toNanos(30)));
        retry.succeeded();
        assertTrue(retry.allows(9, 0));
        assertTrue(retry.failed(9, now));
        assertEquals(250, retry.delayMillis(now));
    }
}
