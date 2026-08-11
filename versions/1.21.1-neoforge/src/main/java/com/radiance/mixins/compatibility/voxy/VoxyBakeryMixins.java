package com.radiance.mixins.compatibility.voxy;

import com.radiance.compatibility.voxy.VoxyAtlasSnapshot;
import me.cortex.voxy.client.core.model.bakery.SoftwareModelTextureBakery;
import me.cortex.voxy.client.core.model.bakery.SoftwareRasterizer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SoftwareModelTextureBakery.class, remap = false)
public abstract class VoxyBakeryMixins {
    @Shadow @Final private SoftwareRasterizer rasterizer;

    @Inject(method = "setupTexture", at = @At("HEAD"), cancellable = true)
    private void radiance$cpuAtlas(CallbackInfo ci) {
        var snapshot = VoxyAtlasSnapshot.snapshot();
        rasterizer.setSamplerTexture(snapshot.pixels(), snapshot.width(), snapshot.height());
        ci.cancel();
    }
}
