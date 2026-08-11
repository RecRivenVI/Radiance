package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Particle.class)
public abstract class ParticleMixins implements IParticleExt {

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "getLightColor")
    private int radiance$captureAmbientLight(float tickDelta,
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Integer> original) {
        return com.radiance.client.render.ParticleEmissionCapture.ambient(this, original.call(tickDelta));
    }

    private String radiance$contentName = null;

    @Override
    public String radiance$getContentName() {
        return radiance$contentName;
    }

    @Override
    public void radiance$setContentName(String contentName) {
        radiance$contentName = contentName;
    }

    @Override
    @Invoker("getLightColor")
    public abstract int radiance$getLightColor(float tickDelta);
}
