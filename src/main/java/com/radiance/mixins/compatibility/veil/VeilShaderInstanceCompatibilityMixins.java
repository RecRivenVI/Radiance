package com.radiance.mixins.compatibility.veil;

import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.shaders.Uniform;
import com.radiance.compatibility.veil.VeilVanillaShaderMetadataAccess;
import com.radiance.compatibility.veil.VeilVanillaShaderUniformAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Applies Radiance's mixin plugin compatibility pass after Veil augments vanilla shaders.
 */
@Mixin(ShaderInstance.class)
public abstract class VeilShaderInstanceCompatibilityMixins
    implements VeilVanillaShaderMetadataAccess {

    @Shadow
    @Final
    private List<Uniform> uniforms;

    @Shadow
    @Final
    public Map<String, Uniform> uniformMap;

    @Unique
    private final Map<String, Uniform> radiance$processedUniforms = new LinkedHashMap<>();

    @Override
    public void radiance$ensureProcessedUniforms(String vertexSource, String fragmentSource) {
        VeilVanillaShaderUniformAdapter.reconcile((Shader) (Object) this, this.uniforms,
            this.uniformMap, this.radiance$processedUniforms, vertexSource, fragmentSource);
    }
}
