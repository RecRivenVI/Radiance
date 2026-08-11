package com.radiance.client.render;

import java.util.function.IntSupplier;

/** Separates a particle's light override from its actual (possibly modded) ambient light. */
public final class ParticleEmissionCapture {
    private static final ThreadLocal<Sample> CURRENT = new ThreadLocal<>();
    private static final class Sample {
        final Object particle;
        int ambient;
        Sample(Object particle) { this.particle = particle; }
    }

    private ParticleEmissionCapture() {}

    // Called around Particle.getLightColor's complete body, including other mixins.
    // An override that never calls the base implementation supplies its own light.
    public static int ambient(Object particle, int packedLight) {
        Sample sample = CURRENT.get();
        if (sample != null && sample.particle == particle) sample.ambient = packedLight;
        return packedLight;
    }

    public static int emissionStep(Object particle, IntSupplier light) {
        Sample previous = CURRENT.get();
        Sample sample = new Sample(particle);
        CURRENT.set(sample);
        try {
            int result = light.getAsInt();
            return Math.clamp(((result >> 4) & 15) - ((sample.ambient >> 4) & 15), 0, 15);
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
