package com.radiance.audit.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.client.texture.AuxiliaryTextures;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.audit.FrameProfiler;
import com.radiance.audit.FrameTimings.Stage;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=AuxiliaryTextures.class,remap=false)
public abstract class ProfileAuxiliaryTexturesMixin {
    @WrapMethod(method="loadAndUpload")
    private static void audit$loadAndUpload(NativeImage source, INativeImageExt sourceExt, int level, int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth, int regionHeight, boolean blur, Operation<Void> original) {
        try(var ignored=FrameProfiler.span(Stage.AUX_TEXTURE)) { original.call(source,sourceExt,level,offsetX,offsetY,unpackSkipPixels,unpackSkipRows,regionWidth,regionHeight,blur); }
    }
}
