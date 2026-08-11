package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.GlStateManager;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.proxy.vulkan.RendererDiagnostics;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserves Minecraft's observable GL state cache while replacing the final LWJGL calls with
 * their Vulkan equivalents. NeoForge's backupGlState/restoreGlState contract reads the cache
 * maintained by the original methods, so cancelling those methods would restore stale defaults.
 */
@Mixin(GlStateManager.class)
public class GlStateManagerMixins {

    @Inject(method = "_activeTexture(I)V", at = @At("HEAD"))
    private static void mirrorActiveTexture(int texture, CallbackInfo ci) {
        TextureProxy.activeTexture(texture);
    }

    @Inject(method = "_bindTexture(I)V", at = @At("HEAD"))
    private static void mirrorBoundTexture(int texture, CallbackInfo ci) {
        TextureProxy.bindTexture(texture);
    }

    @Inject(method = "_scissorBox(IIII)V", at = @At("HEAD"))
    private static void mirrorScissorBox(int x, int y, int width, int height, CallbackInfo ci) {
        PipelineStateProxy.ViewportState.setScissor(x, y, width, height);
    }

    @Inject(method = "_viewport(IIII)V", at = @At("HEAD"))
    private static void mirrorViewport(int x, int y, int width, int height, CallbackInfo ci) {
        PipelineStateProxy.ViewportState.setViewport(x, y, width, height);
    }

    @Inject(method = "_blendFunc(II)V", at = @At("HEAD"))
    private static void mirrorBlendFunc(int srcFactor, int dstFactor, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetBlendFuncCombined(srcFactor, dstFactor);
    }

    @Inject(method = "_blendFuncSeparate(IIII)V", at = @At("HEAD"))
    private static void mirrorBlendFuncSeparate(int srcFactorRGB, int dstFactorRGB,
        int srcFactorAlpha, int dstFactorAlpha, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetBlendFuncSeparate(srcFactorRGB, srcFactorAlpha,
            dstFactorRGB, dstFactorAlpha);
    }

    @Inject(method = "_blendEquation(I)V", at = @At("HEAD"))
    private static void mirrorBlendEquation(int mode, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetBlendOpCombined(mode);
    }

    @Inject(method = "_colorMask(ZZZZ)V", at = @At("HEAD"))
    private static void mirrorColorMask(boolean red, boolean green, boolean blue,
        boolean alpha, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetColorWriteMask(red, green, blue, alpha);
    }

    @Inject(method = "_logicOp(I)V", at = @At("HEAD"))
    private static void mirrorLogicOp(int op, CallbackInfo ci) {
        PipelineStateProxy.ColorBlendState.glSetColorLogicOp(op);
    }

    @Inject(method = "_depthFunc(I)V", at = @At("HEAD"))
    private static void mirrorDepthFunc(int func, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.glSetDepthCompareOp(func);
    }

    @Inject(method = "_depthMask(Z)V", at = @At("HEAD"))
    private static void mirrorDepthMask(boolean mask, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.setDepthWriteEnable(mask);
    }

    @Inject(method = "_stencilFunc(III)V", at = @At("HEAD"))
    private static void mirrorStencilFunc(int func, int ref, int mask, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.glSetStencilFunc(func, ref, mask);
    }

    @Inject(method = "_stencilMask(I)V", at = @At("HEAD"))
    private static void mirrorStencilMask(int mask, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.vkSetStencilWriteMask(mask);
    }

    @Inject(method = "_stencilOp(III)V", at = @At("HEAD"))
    private static void mirrorStencilOp(int sfail, int dpfail, int dppass, CallbackInfo ci) {
        PipelineStateProxy.DepthStencilState.glSetStencilOp(sfail, dpfail, dppass);
    }

    @Inject(method = "_polygonMode(II)V", at = @At("HEAD"))
    private static void mirrorPolygonMode(int face, int mode, CallbackInfo ci) {
        PipelineStateProxy.RasterizationState.glSetPolygonMode(mode);
    }

    @Inject(method = "_polygonOffset(FF)V", at = @At("HEAD"))
    private static void mirrorPolygonOffset(float factor, float units, CallbackInfo ci) {
        PipelineStateProxy.RasterizationState.glSetPolygonOffset(factor, units);
    }

    @Inject(method = "_clearColor(FFFF)V", at = @At("HEAD"))
    private static void mirrorClearColor(float red, float green, float blue, float alpha,
        CallbackInfo ci) {
        PipelineStateProxy.ClearState.setClearColor(red, green, blue, alpha);
    }

    @Inject(method = "_clearDepth(D)V", at = @At("HEAD"))
    private static void mirrorClearDepth(double depth, CallbackInfo ci) {
        PipelineStateProxy.ClearState.setClearDepth(depth);
    }

    @Inject(method = "_clearStencil(I)V", at = @At("HEAD"))
    private static void mirrorClearStencil(int stencil, CallbackInfo ci) {
        PipelineStateProxy.ClearState.setClearStencil(stencil);
    }

    @Redirect(method = "_activeTexture(I)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;glActiveTexture(I)V"))
    private static void preserveActiveTextureWithoutOpenGl(int texture) {
        // _activeTexture already updated Mojang's activeTexture cache before this terminal call.
        // Vulkan texture selection is resolved from RenderSystem's sampler slots at draw time.
    }

    @Redirect(method = "_bindTexture(I)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBindTexture(II)V", remap = false),
        remap = false)
    private static void preserveBoundTextureWithoutOpenGl(int target, int texture) {
        // _bindTexture already updated Mojang's per-unit binding cache; the mirrored binding is
        // consumed by Vulkan readback while draw-time samplers come from ShaderInstance state.
    }

    @Redirect(method = "_deleteTexture(I)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDeleteTextures(I)V", remap = false),
        remap = false)
    private static void releaseVulkanTexture(int texture) {
        TextureProxy.releaseTextureId(texture, MissingTextureAtlasSprite.getTexture().getId());
    }

    @Redirect(method = "_deleteTextures([I)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDeleteTextures([I)V", remap = false),
        remap = false)
    private static void releaseVulkanTextures(int[] textures) {
        int fallbackId = MissingTextureAtlasSprite.getTexture().getId();
        for (int texture : textures) {
            TextureProxy.releaseTextureId(texture, fallbackId);
        }
    }

    @Redirect(method = "_scissorBox(IIII)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20;glScissor(IIII)V", remap = false),
        remap = false)
    private static void redirectScissorBox(int x, int y, int width, int height) {
    }

    @Redirect(method = "_viewport(IIII)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glViewport(IIII)V", remap = false),
        remap = false)
    private static void redirectViewport(int x, int y, int width, int height) {
    }

    @Redirect(method = "_blendFunc(II)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V", remap = false),
        remap = false)
    private static void redirectBlendFunc(int srcFactor, int dstFactor) {
    }

    @Redirect(method = "_blendFuncSeparate(IIII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;glBlendFuncSeparate(IIII)V"),
        remap = false)
    private static void redirectBlendFuncSeparate(int srcFactorRGB, int dstFactorRGB,
        int srcFactorAlpha, int dstFactorAlpha) {
    }

    @Redirect(method = "_blendEquation(I)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL14;glBlendEquation(I)V", remap = false),
        remap = false)
    private static void redirectBlendEquation(int mode) {
    }

    @Redirect(method = "_colorMask(ZZZZ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColorMask(ZZZZ)V", remap = false),
        remap = false)
    private static void redirectColorMask(boolean red, boolean green, boolean blue,
        boolean alpha) {
    }

    @Redirect(method = "_logicOp(I)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLogicOp(I)V", remap = false),
        remap = false)
    private static void redirectLogicOp(int op) {
    }

    @Redirect(method = "_depthFunc(I)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", remap = false),
        remap = false)
    private static void redirectDepthFunc(int func) {
    }

    @Redirect(method = "_depthMask(Z)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V", remap = false),
        remap = false)
    private static void redirectDepthMask(boolean mask) {
    }

    @Redirect(method = "_stencilFunc(III)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glStencilFunc(III)V", remap = false),
        remap = false)
    private static void redirectStencilFunc(int func, int ref, int mask) {
    }

    @Redirect(method = "_stencilMask(I)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glStencilMask(I)V", remap = false),
        remap = false)
    private static void redirectStencilMask(int mask) {
    }

    @Redirect(method = "_stencilOp(III)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glStencilOp(III)V", remap = false),
        remap = false)
    private static void redirectStencilOp(int sfail, int dpfail, int dppass) {
    }

    @Redirect(method = "_polygonMode(II)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPolygonMode(II)V", remap = false),
        remap = false)
    private static void redirectPolygonMode(int face, int mode) {
    }

    @Redirect(method = "_polygonOffset(FF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPolygonOffset(FF)V", remap = false),
        remap = false)
    private static void redirectPolygonOffset(float factor, float units) {
    }

    @Redirect(method = "_clearColor(FFFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClearColor(FFFF)V", remap = false),
        remap = false)
    private static void redirectClearColor(float red, float green, float blue, float alpha) {
    }

    @Redirect(method = "_clearDepth(D)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClearDepth(D)V", remap = false),
        remap = false)
    private static void redirectClearDepth(double depth) {
    }

    @Redirect(method = "_clearStencil(I)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClearStencil(I)V", remap = false),
        remap = false)
    private static void redirectClearStencil(int stencil) {
    }

    @Redirect(method = "_clear(IZ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClear(I)V", remap = false),
        remap = false)
    private static void redirectClear(int mask) {
        DrawCommandProxy.Overlay.glClear(mask);
    }

    @Redirect(method = "_getString(I)Ljava/lang/String;",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetString(I)Ljava/lang/String;", remap = false),
        remap = false)
    private static String redirectGetString(int name) {
        return RendererDiagnostics.backendString(name);
    }
}
