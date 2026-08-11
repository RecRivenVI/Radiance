package com.radiance.mixins.compatibility.veil;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.compatibility.veil.VeilShaderAdapter;
import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.program.ShaderUniformCache;
import foundry.veil.impl.client.render.shader.program.ShaderProgramImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = ShaderProgramImpl.CompiledProgram.class, remap = false)
public abstract class VeilCompiledProgramMixins {

    @Shadow
    public abstract int program();
    @Shadow
    @Final
    private Int2ObjectMap<CompiledShader> shaders;
    @Shadow
    @Final
    private ShaderUniformCache uniformCache;
    @Shadow
    @Final
    private Set<String> definitionDependencies;

    @Inject(method = "detectVertexFormat", at = @At("HEAD"), cancellable = true,
        remap = false)
    private void radiance$detectCapturedFormat(CallbackInfoReturnable<VertexFormat> cir) {
        cir.setReturnValue(VeilShaderAdapter.vertexFormat(this.program()));
    }

    @Inject(method = "validate", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$skipOpenGlValidation(ShaderProgram program, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "free", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$freeCapturedProgram(CallbackInfo ci) {
        VeilShaderAdapter.freeCompiledProgram(this.shaders, this.uniformCache,
            this.definitionDependencies);
        ci.cancel();
    }
}
