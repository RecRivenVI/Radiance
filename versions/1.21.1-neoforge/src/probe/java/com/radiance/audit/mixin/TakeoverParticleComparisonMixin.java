package com.radiance.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;

/** Woven into our adapter, never the authoritative source particle class. */
@Mixin(targets = "com.radiance.client.vertex.ParticleQuadTakeover", remap = false)
public class TakeoverParticleComparisonMixin {
    @WrapMethod(method = "request")
    private static void audit$renderer(
            Particle particle,
            VertexConsumer consumer,
            net.minecraft.client.Camera camera,
            float partial,
            Operation<Void> original) {
        com.radiance.audit.ParticleTakeoverProbe.fullRender(
                camera, () -> original.call(particle, consumer, camera, partial));
    }

    @WrapMethod(method = "submit")
    private static void audit$quad(
            Particle particle,
            VertexConsumer consumer,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float tick,
            float size,
            float u0,
            float u1,
            float v0,
            float v1,
            int light,
            float r,
            float g,
            float b,
            float a,
            Operation<Void> original) {
        com.radiance.audit.ParticleTakeoverProbe.compare(
                (SingleQuadParticle) particle,
                consumer,
                rotation,
                x,
                y,
                z,
                tick,
                () ->
                        original.call(
                                particle, consumer, rotation, x, y, z, tick, size, u0, u1, v0, v1,
                                light, r, g, b, a));
    }
}
