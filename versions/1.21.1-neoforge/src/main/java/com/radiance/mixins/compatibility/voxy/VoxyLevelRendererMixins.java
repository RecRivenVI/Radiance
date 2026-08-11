package com.radiance.mixins.compatibility.voxy;

import com.radiance.compatibility.voxy.VoxyPtBridge;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class VoxyLevelRendererMixins implements IVoxyRenderSystemHolder {
    @Override
    public VoxyRenderSystem voxy$getRenderSystem() {
        return null;
    }

    @Override
    public void voxy$shutdownRenderer() {
        VoxyPtBridge.close();
    }

    @Override
    public void voxy$createRenderer() {
        VoxyPtBridge.frame();
    }

    @Override
    public void voxy$setWorld(Level level) {
        VoxyPtBridge.setWorld(level, this);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void radiance$level(ClientLevel level, CallbackInfo ci) {
        voxy$setWorld(level);
    }

    @Inject(method = "allChanged", at = @At("HEAD"))
    private void radiance$reload(CallbackInfo ci) {
        VoxyPtBridge.reload();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void radiance$close(CallbackInfo ci) {
        VoxyPtBridge.close();
    }
}
