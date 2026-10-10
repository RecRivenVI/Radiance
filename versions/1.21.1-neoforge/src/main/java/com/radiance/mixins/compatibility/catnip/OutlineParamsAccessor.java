package com.radiance.mixins.compatibility.catnip;

import net.createmod.catnip.render.BindableTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.createmod.catnip.outliner.Outline$OutlineParams", remap = false)
public interface OutlineParamsAccessor {

    @Accessor("alpha")
    void radiance$setAlpha(float alpha);

    @Accessor("faceTexture")
    BindableTexture radiance$getFaceTexture();

    @Accessor("highlightedFaceTexture")
    BindableTexture radiance$getHighlightedFaceTexture();

    @Accessor("disableCull")
    boolean radiance$getDisableCull();
}
