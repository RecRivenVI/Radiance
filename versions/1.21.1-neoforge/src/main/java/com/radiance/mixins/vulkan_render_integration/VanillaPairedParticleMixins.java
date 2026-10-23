package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.ParticleQuadTakeover;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.*;

/** The two original billboard poses are parameters of the same shared-quad executor. */
public final class VanillaPairedParticleMixins {
    private VanillaPairedParticleMixins() {}

    @Mixin(VibrationSignalParticle.class)
    public abstract static class Vibration extends Particle implements ParticleQuadTakeover.Source {
        protected Vibration(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        @Shadow private float rot;
        @Shadow private float rotO;
        @Shadow private float pitch;
        @Shadow private float pitchO;

        public void radiance$submitParameters(VertexConsumer out, Camera camera, float partial) {
            float wave = Mth.sin((age + partial - (float) (Math.PI * 2)) * .05F) * 2;
            float yaw = Mth.lerp(partial, rotO, rot),
                    angle = Mth.lerp(partial, pitchO, pitch) + (float) (Math.PI / 2);
            var rotation = new Quaternionf().rotationY(yaw).rotateX(-angle).rotateY(wave);
            ((ParticleQuadTakeover.Source) this)
                    .radiance$rotatedParameters(out, camera, rotation, partial);
            rotation.rotationY((float) -Math.PI + yaw).rotateX(angle).rotateY(wave);
            ((ParticleQuadTakeover.Source) this)
                    .radiance$rotatedParameters(out, camera, rotation, partial);
        }
    }

    @Mixin(ShriekParticle.class)
    public abstract static class Shriek extends Particle implements ParticleQuadTakeover.Source {
        protected Shriek(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        @Shadow private int delay;

        public void radiance$submitParameters(VertexConsumer out, Camera camera, float partial) {
            if (delay > 0) return;
            alpha = 1 - Mth.clamp((age + partial) / (float) lifetime, 0, 1);
            var rotation = new Quaternionf().rotationX(-1.0472F);
            ((ParticleQuadTakeover.Source) this)
                    .radiance$rotatedParameters(out, camera, rotation, partial);
            rotation.rotationYXZ((float) -Math.PI, 1.0472F, 0);
            ((ParticleQuadTakeover.Source) this)
                    .radiance$rotatedParameters(out, camera, rotation, partial);
        }
    }
}
