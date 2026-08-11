package com.radiance.mixins.compatibility.veil;

import com.radiance.compatibility.veil.VeilDynamicBufferAdapter;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager$DynamicBuffer", remap = false)
public abstract class VeilDynamicBufferStorageMixins {
    @Shadow @Final private DynamicBufferType type;
    @Shadow @Final private int textureId;

    @Inject(method = {"init", "resize"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$allocateAttachment(int width, int height, CallbackInfo ci) {
        VeilDynamicBufferAdapter.allocate(type, textureId, width, height);
        ci.cancel();
    }
}
