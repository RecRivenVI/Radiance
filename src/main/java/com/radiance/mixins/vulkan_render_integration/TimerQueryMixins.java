package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.TimerQuery;
import com.radiance.client.proxy.vulkan.RendererProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TimerQuery.class)
public class TimerQueryMixins {

    @Redirect(
        method = "beginProfile()V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glGenQueries()I")
    )
    private int beginVulkanGpuProfile() {
        return RendererProxy.beginGpuProfile();
    }

    @Redirect(
        method = "beginProfile()V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glBeginQuery(II)V")
    )
    private void skipGlBeginQuery(int target, int queryName) {
    }

    @Redirect(
        method = "endProfile()Lcom/mojang/blaze3d/systems/TimerQuery$FrameProfile;",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glEndQuery(I)V")
    )
    private void skipGlEndQuery(int target) {
    }
}
