package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilVanillaShaderAdapter;
import foundry.veil.ext.ShaderInstanceExtension;
import foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager;
import foundry.veil.impl.client.render.dynamicbuffer.VanillaShaderCompiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses Veil's CPU preprocessor while handing processed vanilla stages to Vulkan. */
@Pseudo
@Mixin(value = VanillaShaderCompiler.class, remap = false)
public abstract class VeilVanillaShaderCompilerMixins {

    @Redirect(method = "reload(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL;getCapabilities()Lorg/lwjgl/opengl/GLCapabilities;"),
        remap = false)
    private GLCapabilities radiance$useVulkanCapabilities() {
        // ShaderVersionProcessor is separately bridged to select GLSL 460 for this null marker.
        return null;
    }

    @Inject(method = "getActiveDynamicBuffers", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void radiance$getVulkanActiveBuffers(ShaderInstance shader,
        CallbackInfoReturnable<Integer> cir) {
        if (!VeilVanillaShaderAdapter.isVeilWrapper(shader)) {
            cir.setReturnValue(VeilVanillaShaderAdapter.activeBuffers(shader));
        }
    }

    @Inject(method = "compileShader", at = @At("HEAD"), remap = false)
    private void radiance$beginVulkanReload(ShaderInstance shader, int activeBuffers,
        GLCapabilities capabilities, CallbackInfo ci) {
        VeilVanillaShaderAdapter.begin(shader, activeBuffers);
    }

    @Redirect(method = "compileShader",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"))
    private void radiance$bindCompileTicket(Minecraft minecraft, Runnable callback) {
        VeilVanillaShaderAdapter.executeStage(minecraft, callback);
    }

    @Redirect(method = "lambda$compileShader$0",
        at = @At(value = "INVOKE",
            target = "Lfoundry/veil/ext/ShaderInstanceExtension;veil$recompile(ZLjava/lang/String;I)V"),
        remap = false)
    private static void radiance$captureProcessedStage(ShaderInstanceExtension extension,
        boolean vertex, String source, int activeBuffers) {
        VeilVanillaShaderAdapter.capture((ShaderInstance) extension, vertex, source,
            activeBuffers);
    }

    @Redirect(method = "lambda$compileShader$0",
        at = @At(value = "INVOKE",
            target = "Lfoundry/veil/impl/client/render/dynamicbuffer/DynamicBufferManager;markRecompiled(Lnet/minecraft/client/renderer/ShaderInstance;)V"),
        remap = false)
    private static void radiance$finishProcessedStage(DynamicBufferManager manager,
        ShaderInstance shader) {
        VeilVanillaShaderAdapter.finishStage(manager, shader);
    }

    @Inject(method = "compileShader", at = @At("RETURN"), remap = false)
    private void radiance$finishVulkanReload(ShaderInstance shader, int activeBuffers,
        GLCapabilities capabilities, CallbackInfo ci) {
        VeilVanillaShaderAdapter.finish(shader);
    }
}
