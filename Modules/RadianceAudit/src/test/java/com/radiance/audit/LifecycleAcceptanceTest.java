package com.radiance.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LifecycleAcceptanceTest {
    @Test
    void diagnosticIsDisabledUnlessARecognizedCaseIsExplicitlyNamed() {
        assertEquals(LifecycleAcceptance.Case.DISABLED, LifecycleAcceptance.parseCase(null));
        assertEquals(LifecycleAcceptance.Case.DISABLED, LifecycleAcceptance.parseCase(""));
        assertEquals(LifecycleAcceptance.Case.DISABLED, LifecycleAcceptance.parseCase("anything"));
        assertEquals(LifecycleAcceptance.Case.G0, LifecycleAcceptance.parseCase("g0"));
        assertEquals(LifecycleAcceptance.Case.G1_RUNTIME_FATAL,
                LifecycleAcceptance.parseCase("G1-runtime-fatal"));
        assertEquals(LifecycleAcceptance.Case.G2_BOUNDARY,
                LifecycleAcceptance.parseCase("G2_BOUNDARY"));
    }
}
