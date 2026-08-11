package com.radiance.client.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ParticleEmissionCaptureTest {
    private static class AmbientParticle {
        int light() {
            return ParticleEmissionCapture.ambient(this, 12 << 4);
        }
    }

    private static class GlowingParticle extends AmbientParticle {
        @Override
        int light() {
            return super.light() + (3 << 4);
        }
    }

    @Test
    void structuralAmbientIsNotEmissionAndOverrideIsPreserved() {
        var smoke = new AmbientParticle();
        var glow = new GlowingParticle();
        assertEquals(0, ParticleEmissionCapture.emissionStep(smoke, true, smoke::light));
        assertEquals(3, ParticleEmissionCapture.emissionStep(glow, true, glow::light));
        assertEquals(15, ParticleEmissionCapture.emissionStep(glow, true, () -> 15 << 4));
        assertEquals(0, ParticleEmissionCapture.emissionStep(smoke, false, () -> 15728880));
        assertEquals(15, ParticleEmissionCapture.emissionStep(glow, true, () -> 15 << 20));
        assertEquals(12 << 4, smoke.light());
    }

    @Test
    void nestedAndFailedSamplesDoNotLeakToAnotherParticle() {
        var outer = new AmbientParticle();
        var inner = new GlowingParticle();
        assertEquals(
                0,
                ParticleEmissionCapture.emissionStep(
                        outer,
                        true,
                        () -> {
                            int light = outer.light();
                            assertEquals(
                                    3,
                                    ParticleEmissionCapture.emissionStep(
                                            inner, true, inner::light));
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            ParticleEmissionCapture.emissionStep(
                                                    inner,
                                                    true,
                                                    () -> {
                                                        inner.light();
                                                        throw new IllegalStateException();
                                                    }));
                            return light;
                        }));
        assertEquals(15, ParticleEmissionCapture.emissionStep(outer, true, () -> 15 << 4));
    }
}
