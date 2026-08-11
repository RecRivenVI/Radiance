package com.radiance.mixins.vanilla_resource_tracker;

import java.util.Map;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontManager.class)
public class FontManagerOwnershipMixins {
    @Shadow @Final private Map<ResourceLocation, FontSet> fontSets;
    @Shadow @Final private FontSet missingFontSet;
    @Shadow private volatile FontSet lastFontSetCache;

    @Inject(method = "getFontSetCached", at = @At("HEAD"))
    private void radiance$validateFontOwner(
            ResourceLocation id, CallbackInfoReturnable<FontSet> cir) {
        // The same font name can designate a different provider generation after reload.
        // A retired cache hit must not lazily bake from that provider's closed bitmap.
        var cached = lastFontSetCache;
        if (cached != null
                && id.equals(cached.name())
                && cached != fontSets.getOrDefault(id, missingFontSet)) lastFontSetCache = null;
    }
}
