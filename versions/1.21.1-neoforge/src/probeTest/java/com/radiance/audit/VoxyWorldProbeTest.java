package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VoxyWorldProbeTest {
    @Test
    void disconnectProgressFramesCannotRecursivelyDisconnectAgain() throws Exception {
        var phase = VoxyWorldProbe.class.getDeclaredField("step");
        phase.setAccessible(true);
        int previous = phase.getInt(null);
        try {
            phase.setInt(null, 5);
            var calls = new AtomicInteger();
            assertTrue(
                    VoxyWorldProbe.disconnectOnce(
                            () -> {
                                calls.incrementAndGet();
                                assertFalse(
                                        VoxyWorldProbe.disconnectOnce(
                                                () -> fail("Recursive disconnect")));
                            }));
            assertEquals(1, calls.get());
            assertEquals(6, phase.getInt(null));
            assertFalse(VoxyWorldProbe.disconnectOnce(() -> fail("Repeated disconnect")));
        } finally {
            phase.setInt(null, previous);
        }
    }
}
