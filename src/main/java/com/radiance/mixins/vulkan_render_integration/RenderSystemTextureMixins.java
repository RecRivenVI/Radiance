package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors writes to Minecraft's shader texture slots for last-writer sampler resolution. */
@Mixin(RenderSystem.class)
public abstract class RenderSystemTextureMixins {

    @Inject(method = "_setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
        at = @At("RETURN"))
    private static void radiance$recordShaderTexture(int slot, ResourceLocation location,
        CallbackInfo ci) {
        TextureProxy.shaderTexture(slot);
    }

    @Inject(method = "_setShaderTexture(II)V", at = @At("RETURN"))
    private static void radiance$recordShaderTexture(int slot, int textureId, CallbackInfo ci) {
        TextureProxy.shaderTexture(slot);
    }
}
