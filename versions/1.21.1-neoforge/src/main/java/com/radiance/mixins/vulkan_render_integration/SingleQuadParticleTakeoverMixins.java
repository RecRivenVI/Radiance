package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.ParticleQuadTakeover;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleTakeoverMixins extends Particle
        implements ParticleQuadTakeover.Source {
    protected SingleQuadParticleTakeoverMixins(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Shadow
    public abstract float getQuadSize(float tick);

    @Shadow
    protected abstract float getU0();

    @Shadow
    protected abstract float getU1();

    @Shadow
    protected abstract float getV0();

    @Shadow
    protected abstract float getV1();

    @Shadow
    public abstract SingleQuadParticle.FacingCameraMode getFacingCameraMode();

    public void radiance$submitParameters(
            VertexConsumer consumer, net.minecraft.client.Camera camera, float partial) {
        radiance$billboardParameters(consumer, camera, partial);
    }

    public void radiance$billboardParameters(
            VertexConsumer consumer, net.minecraft.client.Camera camera, float partial) {
        var rotation = new Quaternionf();
        getFacingCameraMode().setRotation(rotation, camera, partial);
        if (roll != 0) rotation.rotateZ(net.minecraft.util.Mth.lerp(partial, oRoll, roll));
        radiance$rotatedParameters(consumer, camera, rotation, partial);
    }

    public void radiance$rotatedParameters(
            VertexConsumer consumer,
            net.minecraft.client.Camera camera,
            Quaternionf rotation,
            float partial) {
        var position = camera.getPosition();
        float px = (float) (net.minecraft.util.Mth.lerp((double) partial, xo, x) - position.x());
        float py = (float) (net.minecraft.util.Mth.lerp((double) partial, yo, y) - position.y());
        float pz = (float) (net.minecraft.util.Mth.lerp((double) partial, zo, z) - position.z());
        float size = getQuadSize(partial), u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partial);
        ParticleQuadTakeover.submit(
                this, consumer, rotation, px, py, pz, partial, size, u0, u1, v0, v1, light, rCol,
                gCol, bCol, alpha);
    }

    @Inject(
            method =
                    "renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V",
            at = @At("HEAD"),
            cancellable = true)
    private void radiance$submitLocalQuad(
            VertexConsumer out,
            Quaternionf rotation,
            float x,
            float y,
            float z,
            float partialTick,
            CallbackInfo ci) {
        if (!ParticleQuadTakeover.eligible(this, out)) return;
        float size = getQuadSize(partialTick);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTick);
        ParticleQuadTakeover.submit(
                this,
                out,
                rotation,
                x,
                y,
                z,
                partialTick,
                size,
                u0,
                u1,
                v0,
                v1,
                light,
                rCol,
                gCol,
                bCol,
                alpha);
        ci.cancel();
    }
}
