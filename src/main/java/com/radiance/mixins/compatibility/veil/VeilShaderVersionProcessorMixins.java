package com.radiance.mixins.compatibility.veil;

import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import foundry.veil.api.client.render.shader.processor.ShaderVersionProcessor;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Selects the Vulkan GLSL target when ShaderManager deliberately supplies no GL capabilities. */
@Pseudo
@Mixin(value = ShaderVersionProcessor.class, remap = false)
public abstract class VeilShaderVersionProcessorMixins {

    @Inject(method = "modify", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$selectVulkanVersion(ShaderPreProcessor.Context context, GlslTree tree,
        CallbackInfo ci) {
        if (context.glCapabilities() == null) {
            if (tree.getVersionStatement().getVersion() == 110
                && tree.getVersionStatement().isCore()) {
                tree.getVersionStatement().setVersion(460);
            }
            ci.cancel();
        }
    }
}
