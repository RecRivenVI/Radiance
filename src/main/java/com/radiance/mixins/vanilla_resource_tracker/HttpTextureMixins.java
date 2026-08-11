package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.HttpTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player skins and capes are uploaded by {@link HttpTexture}, which overrides the simple texture
 * load path and never calls {@code bind()} first. Without an explicit target the upload falls back
 * to the currently bound texture and writes skin pixels into whatever texture was bound before.
 */
@Mixin(HttpTexture.class)
public abstract class HttpTextureMixins extends AbstractTextureMixins {

    @Inject(method = "upload",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIZ)V"))
    private void radiance$setTargetIDBeforeUpload(NativeImage image, CallbackInfo ci) {
        ((INativeImageExt) (Object) image).radiance$setTargetID(getId());
    }
}
