package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilShaderAdapter;
import foundry.veil.api.client.render.shader.program.ShaderUniformCache;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.function.IntSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ShaderUniformCache.class, remap = false)
public abstract class VeilShaderUniformCacheMixins {

    @Shadow
    @Final
    private IntSupplier shader;
    @Shadow
    @Final
    private Object2ObjectMap<String, ShaderUniformCache.Uniform> samplers;
    @Shadow
    @Final
    private Object2ObjectMap<String, ShaderUniformCache.Uniform> uniforms;
    @Shadow
    @Final
    private Object2ObjectMap<String, ShaderUniformCache.UniformBlock> uniformBlocks;
    @Shadow
    @Final
    private Object2ObjectMap<String, ShaderUniformCache.StorageBlock> storageBlocks;
    @Shadow
    private boolean requested;

    @Inject(method = "updateUniforms", at = @At("HEAD"), cancellable = true,
        remap = false)
    private void radiance$loadCapturedUniformLayout(CallbackInfo ci) {
        int nativeId = this.shader.getAsInt();
        VeilShaderAdapter.populateUniformCache(nativeId, this.samplers, this.uniforms,
            this.uniformBlocks, this.storageBlocks);
        this.requested = true;
        ci.cancel();
    }
}
