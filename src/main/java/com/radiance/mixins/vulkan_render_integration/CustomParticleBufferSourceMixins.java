package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.particle.MobAppearanceParticle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({ItemPickupParticle.class, MobAppearanceParticle.class})
public class CustomParticleBufferSourceMixins {

    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"))
    private MultiBufferSource.BufferSource radiance$captureCustomParticleGeometry(
        RenderBuffers renderBuffers) {
        return EntityProxy.captureCustomParticleBufferSource(renderBuffers.bufferSource());
    }
}
