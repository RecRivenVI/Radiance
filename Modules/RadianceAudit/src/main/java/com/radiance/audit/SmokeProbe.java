package com.radiance.audit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Explicit isolated launch/close check. Never substitutes for world or visual acceptance. */
public final class SmokeProbe {
    private static final boolean ENABLED = "1".equals(ExperimentAccess.getenv("RADIANCE_AUDIT_SMOKE"));
    private static long ready;
    private static boolean stopped;
    private SmokeProbe() {}
    public static void poll(Minecraft minecraft) {
        if (!ENABLED || stopped || !(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (ready == 0) {
            ready = System.nanoTime();
            com.mojang.logging.LogUtils.getLogger().info("AUDIT_SMOKE title-ready; diagnostic API version=1");
        }
        if (System.nanoTime() - ready >= 8_000_000_000L) {
            stopped = true;
            com.mojang.logging.LogUtils.getLogger().info("AUDIT_SMOKE normal-stop requested");
            minecraft.stop();
        }
    }
}
