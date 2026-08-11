package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilShaderAdapter;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ShaderProgram.class, remap = false)
public interface VeilShaderProgramInterfaceMixins {

    @Inject(method = "bind", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$bindVulkanProgram(CallbackInfo ci) {
        VeilShaderAdapter.bindProgram((ShaderProgram) this);
        ci.cancel();
    }

    @Inject(method = "unbind", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$unbindVulkanProgram(CallbackInfo ci) {
        VeilShaderAdapter.unbindProgram();
        ci.cancel();
    }

    @Inject(method = "setUniformBlock", at = @At("HEAD"), cancellable = true,
        remap = false)
    private void radiance$keepUniformBlockBinding(CharSequence name, int binding,
        CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "setStorageBlock", at = @At("HEAD"), cancellable = true,
        remap = false)
    private void radiance$rejectStorageBlockBinding(CharSequence name, int binding,
        CallbackInfo ci) {
        throw VeilShaderAdapter.unsupportedStorageBlock(name);
    }
}
