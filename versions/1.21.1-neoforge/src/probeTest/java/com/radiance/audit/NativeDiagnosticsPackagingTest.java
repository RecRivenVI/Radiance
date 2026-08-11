package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class NativeDiagnosticsPackagingTest {
    @Test
    void collectorIsAvailableToTheProbeResourceLookup() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        try (var stream =
                NativeDiagnostics.class.getResourceAsStream(
                        "/META-INF/natives/windows-x86_64/radiance-audit-native.dll")) {
            assertNotNull(stream, "The native collector must be in the probe runtime resources");
            assertEquals('M', stream.read());
            assertEquals('Z', stream.read());
        }
    }
}
