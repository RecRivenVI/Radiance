package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.shader.ShaderRegistry;
import com.radiance.client.render.AppliedShaderState;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICompiledShaderExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderInstance.class)
public abstract class ShaderProgramMixins implements IShaderProgramExt {

    @Shadow
    @Final
    private Map<String, Object> samplerMap;
    @Shadow
    @Final
    private List<String> samplerNames;
    @Shadow
    @Final
    private List<Uniform> uniforms;
    @Shadow
    @Final
    private Program vertexProgram;
    @Shadow
    @Final
    private Program fragmentProgram;
    @Shadow
    @Final
    private VertexFormat vertexFormat;
    @Shadow
    @Final
    private String name;

    @Unique
    private String radiance$vertexSource;
    @Unique
    private String radiance$fragmentSource;

    @Inject(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
        at = @At("RETURN"))
    private void captureMetadata(ResourceProvider provider, ResourceLocation shaderLocation,
        VertexFormat vertexFormat, CallbackInfo ci) {
        this.radiance$vertexSource = ((ICompiledShaderExt) (Object) this.vertexProgram)
            .radiance$getResolvedSource();
        this.radiance$fragmentSource = ((ICompiledShaderExt) (Object) this.fragmentProgram)
            .radiance$getResolvedSource();
        ShaderRegistry.registerLiveShader((ShaderInstance) (Object) this);
    }

    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void applyWithoutOpenGL(CallbackInfo ci) {
        AppliedShaderState.apply((ShaderInstance) (Object) this);
        ci.cancel();
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void clearWithoutOpenGL(CallbackInfo ci) {
        AppliedShaderState.clear();
        ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void unregisterLiveShader(CallbackInfo ci) {
        AppliedShaderState.clearIfCurrent((ShaderInstance) (Object) this);
        ShaderRegistry.unregisterLiveShader((ShaderInstance) (Object) this);
    }

    @Override
    public String radiance$getShaderName() {
        return this.name;
    }

    @Override
    public void radiance$setShaderName(String shaderName) {
    }

    @Override
    public VertexFormat radiance$getVertexFormat() {
        return this.vertexFormat;
    }

    @Override
    public void radiance$setVertexFormat(VertexFormat vertexFormat) {
    }

    @Override
    public String radiance$getVertexSource() {
        return this.radiance$vertexSource;
    }

    @Override
    public void radiance$setVertexSource(String vertexSource) {
        this.radiance$vertexSource = vertexSource;
    }

    @Override
    public String radiance$getFragmentSource() {
        return this.radiance$fragmentSource;
    }

    @Override
    public void radiance$setFragmentSource(String fragmentSource) {
        this.radiance$fragmentSource = fragmentSource;
    }

    @Override
    public List<String> radiance$getSamplerNamesValue() {
        return this.samplerNames;
    }

    @Override
    public List<Uniform> radiance$getUniformsValue() {
        return this.uniforms;
    }

    @Override
    public Map<String, Object> radiance$getSamplerTexturesValue() {
        return this.samplerMap;
    }
}
