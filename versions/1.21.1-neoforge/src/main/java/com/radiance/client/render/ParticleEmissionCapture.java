package com.radiance.client.render;

import java.util.function.IntSupplier;

/**
 * Evaluates a reviewed source glow rule with ambient light excluded, never infers from full UV2.
 */
public final class ParticleEmissionCapture {
    private static final ThreadLocal<Sample> CURRENT = new ThreadLocal<>();

    private static final class Sample {
        final Object particle;

        Sample(Object particle) {
            this.particle = particle;
        }
    }

    private ParticleEmissionCapture() {}

    // Called around Particle.getLightColor's complete body, including other mixins.
    // An override that never calls the base implementation supplies its own light.
    public static int ambient(Object particle, int packedLight) {
        Sample sample = CURRENT.get();
        if (sample != null && sample.particle == particle) return 0;
        return packedLight;
    }

    public static boolean suppressAmbient(Object particle) {
        var sample = CURRENT.get();
        return sample != null && sample.particle == particle;
    }

    public static int emissionStep(Object particle, boolean reviewedIntent, IntSupplier light) {
        if (!reviewedIntent) {
            light.getAsInt();
            return 0;
        }
        Sample previous = CURRENT.get();
        Sample sample = new Sample(particle);
        CURRENT.set(sample);
        try {
            int result = light.getAsInt();
            return Math.clamp(Math.max((result >> 4) & 15, (result >> 20) & 15), 0, 15);
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
