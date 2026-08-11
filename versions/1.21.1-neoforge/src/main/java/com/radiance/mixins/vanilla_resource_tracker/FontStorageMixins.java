package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.radiance.client.texture.FontAtlasLifecycle;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.util.List;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontSet.class)
public class FontStorageMixins {

    @Shadow @Final private TextureManager textureManager;
    @Shadow @Final private ResourceLocation name;
    @Shadow @Final private List<FontTexture> textures;

    @Inject(method = "closeTextures()V", at = @At("HEAD"), cancellable = true)
    private void radiance$retireFontPages(CallbackInfo ci) {
        FontAtlasLifecycle.retire(textureManager, name, textures);
        textures.clear();
        ci.cancel();
    }

    @Inject(
            method =
                    "stitch(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
            at = @At("HEAD"))
    public void ensureIRenderableGlyph(SheetGlyphInfo c, CallbackInfoReturnable<BakedGlyph> cir) {
        if (!(c instanceof IRenderableGlyphExt)) {
            throw new RuntimeException(
                    "RenderableGlyph expected to be instance of IRenderableGlyphExt");
        }
    }

    @Redirect(
            method =
                    "stitch(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/font/FontTexture;add(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)"
                                            + "Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"))
    public BakedGlyph redirectBakeToOneWithID(FontTexture instance, SheetGlyphInfo glyph) {
        IRenderableGlyphExt renderableGlyphExt = (IRenderableGlyphExt) glyph;
        return ((IGlyphAtlasTextureExt) instance).radiance$bake(renderableGlyphExt);
    }
}
