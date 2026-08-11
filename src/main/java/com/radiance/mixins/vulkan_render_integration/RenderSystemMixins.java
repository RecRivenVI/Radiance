package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.RendererProxy;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixins {

    @Shadow(remap = false)
    private static Matrix4f projectionMatrix;

    @Shadow(remap = false)
    private static Matrix4f savedProjectionMatrix;

    @Final
    @Shadow(remap = false)
    private static Matrix4fStack modelViewStack;

    @Shadow(remap = false)
    private static Matrix4f textureMatrix;

    @Inject(method = "maxSupportedTextureSize()I", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private static void redirectMaxSupportedTextureSize(CallbackInfoReturnable<Integer> cir) {
        int maxImageSize = RendererProxy.maxSupportedTextureSize();
        cir.setReturnValue(maxImageSize);
    }

    @Inject(method = "getCapsString()Ljava/lang/String;", at = @At("HEAD"),
        cancellable = true, remap = false)
    private static void describeVulkanCapabilities(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Using Vulkan ray tracing with a GLFW_NO_API window");
    }

    @Inject(method = "flipFrame(J)V", at = @At("HEAD"))
    private static void prepareNextInputFrame(long window, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.radiance.client.proxy.vulkan.RendererProxy.streamlineFrameEvent(0);
    }
    @Redirect(method = "flipFrame(J)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private static void cancelSwapBuffers(long window) {

    }

}
