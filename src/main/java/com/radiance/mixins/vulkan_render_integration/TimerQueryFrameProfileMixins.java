package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.vulkan.RendererProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.mojang.blaze3d.systems.TimerQuery$FrameProfile")
public class TimerQueryFrameProfileMixins {

    @Redirect(
        method = "cancel()V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glDeleteQueries(I)V")
    )
    private void skipCancelledGlQueryDelete(int queryName) {
    }

    @Redirect(
        method = "isDone()Z",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glGetQueryObjecti(II)I")
    )
    private int checkVulkanGpuProfile(int queryName, int parameter) {
        return RendererProxy.isGpuProfileReady(queryName) ? 1 : 0;
    }

    @Redirect(
        method = "isDone()Z",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBTimerQuery;glGetQueryObjecti64(II)J")
    )
    private long readCompletedVulkanGpuProfile(int queryName, int parameter) {
        return RendererProxy.gpuProfileTimeNs(queryName);
    }

    @Redirect(
        method = "isDone()Z",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glDeleteQueries(I)V")
    )
    private void skipCompletedGlQueryDelete(int queryName) {
    }

    @Redirect(
        method = "get()J",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBTimerQuery;glGetQueryObjecti64(II)J")
    )
    private long readVulkanGpuProfile(int queryName, int parameter) {
        return RendererProxy.gpuProfileTimeNs(queryName);
    }

    @Redirect(
        method = "get()J",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glDeleteQueries(I)V")
    )
    private void skipGlQueryDelete(int queryName) {
    }
}
