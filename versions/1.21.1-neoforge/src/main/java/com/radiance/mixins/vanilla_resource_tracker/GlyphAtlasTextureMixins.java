package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.texture.FontAtlasLifecycle;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FontTexture.class)
public abstract class GlyphAtlasTextureMixins extends AbstractTextureMixins
        implements IGlyphAtlasTextureExt {

    @Final @Shadow private GlyphRenderTypes renderTypes;
    @Final @Shadow private boolean colored;
    @Final @Shadow private FontTexture.Node root;

    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true)
    private void radiance$retirePendingGlyphs(CallbackInfo ci) {
        FontTexture page = (FontTexture) (Object) this;
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            // Delay the whole close, not just native release: the old page stays valid
            // until its CPU batches can be retired on their owning render thread.
            RenderSystem.recordRenderCall(page::close);
            ci.cancel();
            return;
        }
        FontAtlasLifecycle.beforeClose(page);
    }

    @Override
    public BakedGlyph radiance$bake(IRenderableGlyphExt glyph) {
        if (glyph.isColored() != this.colored) {
            return null;
        }
        FontTexture.Node slot = this.root.insert(glyph);
        if (slot != null) {
            this.bind();
            glyph.upload(this.getId(), slot.x, slot.y);
            float f = 256.0f;
            float g = 256.0f;
            float h = 0.01f;
            return new BakedGlyph(
                    this.renderTypes,
                    ((float) slot.x + 0.01f) / 256.0f,
                    ((float) slot.x - 0.01f + (float) glyph.getPixelWidth()) / 256.0f,
                    ((float) slot.y + 0.01f) / 256.0f,
                    ((float) slot.y - 0.01f + (float) glyph.getPixelHeight()) / 256.0f,
                    glyph.getLeft(),
                    glyph.getRight(),
                    glyph.getTop(),
                    glyph.getBottom());
        }
        return null;
    }
}
