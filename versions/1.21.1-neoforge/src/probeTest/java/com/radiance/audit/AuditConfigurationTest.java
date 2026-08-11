package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuditConfigurationTest {
    @Test
    void regularTimingDoesNotEnableAllocationTagMapsOrCapture() {
        assertEquals(2, AuditConfiguration.nativeFlags(true, false, false, false));
        assertEquals(10, AuditConfiguration.nativeFlags(true, false, false, true));
        assertEquals(15, AuditConfiguration.nativeFlags(true, true, true, true));
        assertEquals(0, AuditConfiguration.nativeFlags(false, false, false, false));
    }
}
