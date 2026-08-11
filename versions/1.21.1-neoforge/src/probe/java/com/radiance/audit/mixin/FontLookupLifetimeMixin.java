package com.radiance.audit.mixin;

import java.util.Map;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the real lookup result against the live registration; never changes that result. */
@Mixin(FontManager.class)
public abstract class FontLookupLifetimeMixin {
    @Shadow @Final private Map<ResourceLocation, FontSet> fontSets;
    @Shadow @Final private FontSet missingFontSet;
    @Unique private int radiance$reports;

    @Inject(method = "getFontSetCached", at = @At("RETURN"))
    private void radiance$lookup(ResourceLocation id, CallbackInfoReturnable<FontSet> cir) {
        if (!Boolean.getBoolean("radiance.audit.lines") || radiance$reports >= 8) return;
        var actual = cir.getReturnValue();
        var expected = fontSets.getOrDefault(id, missingFontSet);
        if (actual != expected) {
            com.radiance.audit.FontSourceProbe.staleLookup();
            radiance$reports++;
            com.mojang.logging.LogUtils.getLogger()
                    .error(
                            "FONT_LOOKUP stale id={} actual={} current={} caller={}",
                            id,
                            System.identityHashCode(actual),
                            System.identityHashCode(expected),
                            StackWalker.getInstance()
                                    .walk(s -> s.limit(16).map(Object::toString).toList()));
        }
    }
}
