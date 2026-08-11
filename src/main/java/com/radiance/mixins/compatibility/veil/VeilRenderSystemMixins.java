package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilRuntimeAdapter;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilShaderLimits;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "foundry.veil.api.client.render.VeilRenderSystem", remap = false)
public abstract class VeilRenderSystemMixins {
    @Shadow private static VeilRenderer renderer;
    @Shadow private static int screenQuadVao;
    @Shadow private static IntBuffer emptySamplers;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$initializeRenderer(CallbackInfo ci) {
        VeilRuntimeAdapter.Initialization initialized = VeilRuntimeAdapter.initialize(renderer);
        renderer = initialized.renderer();
        screenQuadVao = initialized.screenQuadVao();
        if (initialized.emptySamplers() != null) {
            emptySamplers = initialized.emptySamplers();
        }
        ci.cancel();
    }

    @Inject(method = "drawScreenQuad", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$drawScreenQuad(CallbackInfo ci) {
        VeilRuntimeAdapter.drawScreenQuad();
        ci.cancel();
    }

    @Redirect(method = "close", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL43C;glDeleteVertexArrays(I)V"), remap = false)
    private static void radiance$closeUnallocatedVao(int id) {
        VeilRuntimeAdapter.validateUnallocatedScreenQuad(id);
    }

    @Inject(method = "renderPost", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$deferFinalWorldPost(VeilRenderLevelStageEvent.Stage stage, CallbackInfo ci) {
        if (VeilRuntimeAdapter.deferPost(stage)) {
            ci.cancel();
        }
    }

    @Inject(method = {
        "computeSupported",
        "atomicCounterSupported",
        "transformFeedbackSupported",
        "multibindSupported",
        "sparseBuffersSupported",
        "directStateAccessSupported",
        "separateShaderObjectsSupported",
        "clearTextureSupported",
        "copyImageSupported",
        "shaderStorageBufferSupported",
        "programInterfaceQuerySupported",
        "textureAnisotropySupported",
        "textureMirrorClampToEdgeSupported",
        "textureCubeMapSeamlessSupported",
        "textureCubeMapArraySupported",
        "nvDrawTextureSupported",
        "drawIndirectSupported",
        "multiDrawIndirectSupported",
        "gpuShaderFloat64BitSupported",
        "gpuShaderInt64BitSupported",
        "vertexAttribute64BitSupported",
        "bindlessTextureSupported",
        "vertexType10F11F11FRevSupported",
        "pipelineStatisticsQuerySupported",
        "hasImGui"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$reportNoOpenGlCapabilities(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.unsupportedOpenGlCapability());
    }

    @Inject(method = "tessellationSupported", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$tessellationAvailable(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.tessellationSupported());
    }

    @Inject(method = "maxCombinedTextureUnits", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$maxCombinedTextureUnits(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxCombinedTextureUnits());
    }

    @Inject(method = "maxColorAttachments", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$maxColorAttachments(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxColorAttachments());
    }

    @Inject(method = "maxUniformBuffersBindings", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$maxUniformBuffersBindings(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxUniformBufferBindings());
    }

    @Inject(method = "maxVertexAttributes", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$maxVertexAttributes(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxVertexAttributes());
    }

    @Inject(method = "maxVertexAttributeRelativeOffset", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$maxVertexAttributeRelativeOffset(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxVertexAttributeRelativeOffset());
    }

    @Inject(method = "maxFramebufferWidth", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$maxFramebufferWidth(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxFramebufferWidth());
    }

    @Inject(method = "maxFramebufferHeight", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$maxFramebufferHeight(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxFramebufferHeight());
    }

    @Inject(method = "uniformBufferAlignment", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$uniformBufferAlignment(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.uniformBufferAlignment());
    }

    @Inject(method = {"maxSamples", "maxArrayTextureLayers"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$singleSample2DStorage(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.singleSampleOrLayer());
    }

    @Inject(method = {"maxTransformFeedbackBindings", "maxAtomicCounterBufferBindings", "maxShaderStorageBufferBindings"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$unsupportedBindingTypes(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.unsupportedBindingCount());
    }

    @Inject(method = "maxTextureAnisotropy", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$noAnisotropicSampler(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxTextureAnisotropy());
    }

    @Inject(method = "maxUniformBufferSize", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$uniformCapacity(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxUniformBufferSize());
    }

    @Inject(method = "maxShaderStorageBufferSize", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$noStorageBlocks(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.maxShaderStorageBufferSize());
    }

    @Inject(method = "shaderLimits", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$shaderLimits(int shader, CallbackInfoReturnable<VeilShaderLimits> cir) {
        cir.setReturnValue(VeilRuntimeAdapter.shaderLimits(shader));
    }

    @Redirect(method = "detectVendor", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL43C;glGetString(I)Ljava/lang/String;"), remap = false)
    private static String radiance$vendor(int name) {
        return VeilRuntimeAdapter.backendString(name);
    }
}
