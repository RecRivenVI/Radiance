package com.radiance.audit.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class CatnipDepthAuditMixin {
    @Unique private static final boolean radianceaudit$enabled = "1".equals(System.getenv("RADIANCE_CATNIP_SMOKE"));
    @Unique private static boolean radianceaudit$depthMask = true;
    @Unique private static long radianceaudit$last;
    @Inject(method="_depthMask(Z)V", at=@At("HEAD"))
    private static void radianceaudit$mask(boolean enabled, CallbackInfo ci) {
        if (radianceaudit$enabled) radianceaudit$depthMask = enabled;
    }
    @Inject(method="_clear(IZ)V", at=@At("HEAD"))
    private static void radianceaudit$clear(int mask, boolean error, CallbackInfo ci) {
        if (!radianceaudit$enabled || (mask & 256) == 0) return;
        long now = System.nanoTime();
        var screen = Minecraft.getInstance().screen;
        if (screen != null && now - radianceaudit$last > 2_000_000_000L) {
            radianceaudit$last = now;
            com.mojang.logging.LogUtils.getLogger().info("CATNIP_DEPTH mask={} screen={}",
                radianceaudit$depthMask, screen.getClass().getName());
        }
    }
}
