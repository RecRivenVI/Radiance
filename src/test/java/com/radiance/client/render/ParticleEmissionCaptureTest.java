package com.radiance.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticleEmissionCaptureTest {
    private static class AmbientParticle {
        int light() { return ParticleEmissionCapture.ambient(this, 12 << 4); }
    }
    private static class GlowingParticle extends AmbientParticle {
        @Override int light() { return super.light() + (3 << 4); }
    }
    @Test void structuralAmbientIsNotEmissionAndOverrideIsPreserved() {
        var smoke = new AmbientParticle();
        var glow = new GlowingParticle();
        assertEquals(0, ParticleEmissionCapture.emissionStep(smoke, smoke::light));
        assertEquals(3, ParticleEmissionCapture.emissionStep(glow, glow::light));
        assertEquals(15, ParticleEmissionCapture.emissionStep(glow, () -> 15 << 4));
    }
    @Test void nestedAndFailedSamplesDoNotLeakToAnotherParticle() {
        var outer = new AmbientParticle();
        var inner = new GlowingParticle();
        assertEquals(0, ParticleEmissionCapture.emissionStep(outer, () -> {
            int light = outer.light();
            assertEquals(3, ParticleEmissionCapture.emissionStep(inner, inner::light));
            assertThrows(IllegalStateException.class, () -> ParticleEmissionCapture.emissionStep(inner,
                () -> { inner.light(); throw new IllegalStateException(); }));
            return light;
        }));
        assertEquals(15, ParticleEmissionCapture.emissionStep(outer, () -> 15 << 4));
    }
}
