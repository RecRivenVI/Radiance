package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilDynamicBufferAdapter;
import com.radiance.compatibility.veil.VeilDynamicBufferReloadAccess;
import foundry.veil.ext.ShaderInstanceExtension;
import java.nio.IntBuffer;
import java.util.Set;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager", remap = false)
public abstract class VeilDynamicBufferManagerMixins implements VeilDynamicBufferReloadAccess {

    @Shadow
    @Final
    private Set<ShaderInstance> swapShaders;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL12C;glGenTextures(Ljava/nio/IntBuffer;)V"), remap = false)
    private void radiance$allocateTextures(IntBuffer textures) {
        VeilDynamicBufferAdapter.allocateTextureIds(textures);
    }

    @Redirect(method = "free", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL12C;glDeleteTextures(Ljava/nio/IntBuffer;)V"), remap = false)
    private void radiance$releaseTextures(IntBuffer textures) {
        VeilDynamicBufferAdapter.releaseTextureIds(textures);
    }

    @Redirect(method = "setActiveBuffers", at = @At(value = "INVOKE",
        target = "Lfoundry/veil/ext/ShaderInstanceExtension;veil$swapBuffers(I)Z"),
        remap = false)
    private boolean radiance$reloadVulkanVariant(ShaderInstanceExtension shader,
        int activeBuffers) {
        return true;
    }

    @Redirect(method = "endFrame", at = @At(value = "INVOKE",
        target = "Lfoundry/veil/ext/ShaderInstanceExtension;veil$applyCompile()Z"),
        remap = false)
    private boolean radiance$skipOpenGlShaderUpload(ShaderInstanceExtension shader) {
        return false;
    }

    @Override
    public void radiance$finishVanillaShaderReload(ShaderInstance shader) {
        this.swapShaders.remove(shader);
    }
}
