package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleManagerMixins implements IParticleManagerExt {

    @Final
    @Shadow
    private static List<ParticleRenderType> RENDER_ORDER;
    @Final
    @Shadow
    private Map<ParticleRenderType, Queue<Particle>> particles;

    @Override
    public List<ParticleRenderType> radiance$getTextureSheets() {
        return RENDER_ORDER;
    }

    @Override
    public Map<ParticleRenderType, Queue<Particle>> radiance$getParticles() {
        return particles;
    }

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;add(Lnet/minecraft/client/particle/Particle;)V",
            shift = At.Shift.BEFORE))
    public void radiance$recordParticleContent(ParticleOptions parameters,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        CallbackInfoReturnable<Particle> cir,
        @Local Particle particle) {
        ResourceLocation particleId = BuiltInRegistries.PARTICLE_TYPE.getKey(parameters.getType());
        if (particleId != null) {
            ((IParticleExt) particle).radiance$setContentName(
                EntityContentNames.toParticleContentName(particleId));
        }
    }

    private static final class EntityContentNames {

        private static String toParticleContentName(ResourceLocation particleId) {
            if ("minecraft".equals(particleId.getNamespace())) {
                return "/particle/" + particleId.getPath();
            }
            return "/particle/" + particleId.getNamespace() + "/" + particleId.getPath();
        }
    }
}
