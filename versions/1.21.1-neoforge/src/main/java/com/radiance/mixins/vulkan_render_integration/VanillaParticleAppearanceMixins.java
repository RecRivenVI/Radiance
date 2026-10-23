package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.ParticleQuadTakeover;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Original colour, lifetime alpha and twinkle predicates precede shared billboard parameters. */
public final class VanillaParticleAppearanceMixins {
    private VanillaParticleAppearanceMixins() {}

    @Mixin(DustColorTransitionParticle.class)
    public abstract static class Dust extends Particle implements ParticleQuadTakeover.Source {
        protected Dust(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        @Invoker("lerpColors")
        protected abstract void radiance$colors(float partial);

        public void radiance$submitParameters(VertexConsumer out, Camera camera, float partial) {
            radiance$colors(partial);
            ((ParticleQuadTakeover.Source) this).radiance$billboardParameters(out, camera, partial);
        }
    }

    @Mixin(FlyTowardsPositionParticle.class)
    public abstract static class Flying extends Particle implements ParticleQuadTakeover.Source {
        protected Flying(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        @Shadow @Final private Particle.LifetimeAlpha lifetimeAlpha;

        public void radiance$submitParameters(VertexConsumer out, Camera camera, float partial) {
            setAlpha(lifetimeAlpha.currentAlphaForAge(age, lifetime, partial));
            ((ParticleQuadTakeover.Source) this).radiance$billboardParameters(out, camera, partial);
        }
    }

    @Mixin(FireworkParticles.OverlayParticle.class)
    public abstract static class Flash extends Particle implements ParticleQuadTakeover.Source {
        protected Flash(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        public void radiance$submitParameters(VertexConsumer out, Camera camera, float partial) {
            setAlpha(.6F - (age + partial - 1) * .25F * .5F);
            ((ParticleQuadTakeover.Source) this).radiance$billboardParameters(out, camera, partial);
        }
    }

    @Mixin(targets = "net.minecraft.client.particle.FireworkParticles$SparkParticle")
    public abstract static class Spark extends Particle implements ParticleQuadTakeover.Source {
        protected Spark(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        @Shadow private boolean twinkle;

        public void radiance$submitParameters(VertexConsumer out, Camera camera, float partial) {
            if (!twinkle || age < lifetime / 3 || (age + lifetime) / 3 % 2 == 0)
                ((ParticleQuadTakeover.Source) this)
                        .radiance$billboardParameters(out, camera, partial);
        }
    }
}
