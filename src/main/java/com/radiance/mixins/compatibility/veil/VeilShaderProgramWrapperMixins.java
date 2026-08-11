package com.radiance.mixins.compatibility.veil;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.shader.ExternalShaderMetadata;
import com.radiance.client.shader.ShaderDefinition;
import com.radiance.compatibility.veil.VeilShaderAdapter;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IExternalShaderProgram;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.impl.client.render.shader.program.ShaderProgramImpl;
import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ShaderProgramImpl.Wrapper.class, priority = 2000, remap = false)
public abstract class VeilShaderProgramWrapperMixins implements IExternalShaderProgram {

    @Shadow
    @Final
    private ShaderProgram program;

    @Inject(method = "apply", at = @At("HEAD"), remap = false)
    private void radiance$applyVulkanShader(CallbackInfo ci) {
        VeilShaderAdapter.applyWrapper((ShaderProgramImpl.Wrapper) (Object) this);
    }

    @Inject(method = "clear", at = @At("HEAD"), remap = false)
    private void radiance$clearVulkanShader(CallbackInfo ci) {
        VeilShaderAdapter.clearWrapper();
    }

    public void setDefaultUniforms(VertexFormat.Mode mode, Matrix4f modelView,
        Matrix4f projection, Window window) {
        VeilShaderAdapter.setDefaultUniforms(this.program, mode, modelView, projection);
    }

    @Override
    public ExternalShaderMetadata radiance$getExternalShader(VertexFormat.Mode drawMode) {
        return VeilShaderAdapter.metadata(this.program, drawMode);
    }

    @Override
    public void radiance$writeExternalUniforms(ShaderDefinition definition,
        ByteBuffer destination) {
        VeilShaderAdapter.writeUniforms(this.program, definition, destination);
    }
}
