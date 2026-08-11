package com.radiance.audit.mixin;

import com.radiance.audit.SpringSourceCapture;
import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import foundry.veil.api.client.render.shader.compiler.VeilShaderSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.compiler.DirectShaderCompiler", remap = false)
public abstract class SpringSourceCaptureMixin {
    @Inject(method = "compile(ILfoundry/veil/api/client/render/shader/compiler/VeilShaderSource;)"
        + "Lfoundry/veil/api/client/render/shader/compiler/CompiledShader;",
        at = @At("HEAD"), remap = false)
    private void audit$captureOriginalSpring(int type, VeilShaderSource source,
        CallbackInfoReturnable<CompiledShader> ci) {
        SpringSourceCapture.capture(type, source);
    }
}
