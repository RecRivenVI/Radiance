package com.radiance.adapter.vanilla;

import java.util.Set;
import java.util.function.IntSupplier;
import net.minecraft.client.particle.*;

/** Current vanilla methods explicitly replace/add light for glow, rather than sample the world. */
public final class VanillaParticleEmission {
    private VanillaParticleEmission() {}

    private static final Set<Class<?>> GLOW =
            Set.of(
                    AttackSweepParticle.class,
                    DripParticle.class,
                    FlameParticle.class,
                    FlyStraightTowardsParticle.class,
                    FlyTowardsPositionParticle.class,
                    GlowParticle.class,
                    GustParticle.class,
                    HugeExplosionParticle.class,
                    LavaParticle.class,
                    PortalParticle.class,
                    SculkChargeParticle.class,
                    SculkChargePopParticle.class,
                    ShriekParticle.class,
                    SimpleAnimatedParticle.class,
                    SoulParticle.class,
                    TrialSpawnerDetectionParticle.class,
                    VibrationSignalParticle.class);
    private static final ClassValue<Boolean> DECLARED =
            new ClassValue<>() {
                protected Boolean computeValue(Class<?> actual) {
                    for (var type = actual; type != null; type = type.getSuperclass())
                        try {
                            type.getDeclaredMethod("getLightColor", float.class);
                            return GLOW.contains(type)
                                    && com.radiance.mixin_related.SourceCompatibility
                                            .declaredProducer(type);
                        } catch (NoSuchMethodException inherited) {
                        }
                    return false;
                }
            };

    public static int emissionStep(Particle particle, IntSupplier source) {
        return com.radiance.client.render.ParticleEmissionCapture.emissionStep(
                particle, DECLARED.get(particle.getClass()), source);
    }
}
