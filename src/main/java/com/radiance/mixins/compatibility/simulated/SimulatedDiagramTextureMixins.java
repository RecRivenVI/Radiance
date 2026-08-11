package com.radiance.mixins.compatibility.simulated;

import com.radiance.compatibility.simulated.DiagramLightmaps;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicTexture.class)
public abstract class SimulatedDiagramTextureMixins {
    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void radiance$captureDiagramMap(CallbackInfo ci) {
        if (DiagramLightmaps.capture((DynamicTexture) (Object) this)) ci.cancel();
    }
}
