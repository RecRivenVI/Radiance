package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixins {

    @Final
    @Shadow
    private DynamicTexture texture;

    @Inject(
        method = "<init>()V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIIIIIZZZZ)V"
        )
    )
    public void setImageTargetIDBeforeUpload(CallbackInfo ci, @Local NativeImage nativeImage) {
        int id = texture.getId();
        ((INativeImageExt) (Object) nativeImage).radiance$setTargetID(id);
    }
}
