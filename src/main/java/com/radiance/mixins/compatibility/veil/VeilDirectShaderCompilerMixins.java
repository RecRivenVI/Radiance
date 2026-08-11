package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilShaderAdapter;
import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import foundry.veil.api.client.render.shader.compiler.ShaderException;
import foundry.veil.api.client.render.shader.compiler.VeilShaderSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.compiler.DirectShaderCompiler",
    remap = false)
public abstract class VeilDirectShaderCompilerMixins {

    @Inject(method = "compile(ILfoundry/veil/api/client/render/shader/compiler/VeilShaderSource;)Lfoundry/veil/api/client/render/shader/compiler/CompiledShader;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$compileProcessedSource(int type, VeilShaderSource source,
        CallbackInfoReturnable<CompiledShader> cir) throws ShaderException {
        cir.setReturnValue(VeilShaderAdapter.compileStage(type, source));
    }
}
