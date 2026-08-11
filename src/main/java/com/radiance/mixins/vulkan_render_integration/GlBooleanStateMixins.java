package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors every requested boolean state to Vulkan while retaining Mojang's cached value. */
@Mixin(targets = "com.mojang.blaze3d.platform.GlStateManager$BooleanState")
public class GlBooleanStateMixins {

    @Shadow
    private int state;

    @Inject(method = "setEnabled(Z)V", at = @At("HEAD"))
    private void mirrorRequestedState(boolean enabled, CallbackInfo ci) {
        // A Vulkan render pass can establish its own dynamic state while Mojang's process-wide
        // cache remains unchanged. Mirror every caller request, even when BooleanState decides
        // that no OpenGL call would have been necessary.
        apply(enabled);
    }

    @Redirect(method = "setEnabled(Z)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false),
        remap = false)
    private void redirectEnable(int ignored) {
    }

    @Redirect(method = "setEnabled(Z)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false),
        remap = false)
    private void redirectDisable(int ignored) {
    }

    private void apply(boolean enabled) {
        switch (this.state) {
            case GL11.GL_SCISSOR_TEST ->
                PipelineStateProxy.ViewportState.setScissorEnabled(enabled);
            case GL11.GL_DEPTH_TEST ->
                PipelineStateProxy.DepthStencilState.setDepthTestEnable(enabled);
            case GL11.GL_BLEND -> PipelineStateProxy.ColorBlendState.setBlendEnable(enabled);
            case GL11.GL_CULL_FACE -> com.radiance.client.render.MaterialFaces.enabled(enabled);
            case GL11.GL_POLYGON_OFFSET_FILL ->
                PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL,
                    enabled);
            case GL11.GL_POLYGON_OFFSET_LINE ->
                PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_LINE,
                    enabled);
            case GL11.GL_COLOR_LOGIC_OP ->
                PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(enabled);
            default -> throw new IllegalStateException(
                "Unsupported vanilla GL boolean state: " + this.state);
        }
    }
}
