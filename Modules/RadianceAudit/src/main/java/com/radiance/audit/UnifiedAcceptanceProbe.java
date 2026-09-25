package com.radiance.audit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;

/** Isolated, opt-in scenario driver. It records execution, never approves appearance. */
public final class UnifiedAcceptanceProbe {
    private static final boolean ENABLED = "1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_UNIFIED_PREFLIGHT"));
    private static long entered;
    private static int step;
    private static boolean reloaded;
    private static boolean paused;
    private static boolean initialCapture;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    public static void poll(Minecraft mc) {
        if (LichenProbe.poll(mc)) return;
        if (HotspotOptimizationProbe.poll(mc)) return;
        if (FixedSceneRouteProbe.poll(mc)) return;
        if (FgBoundaryProbe.poll(mc)) return;
        if (!ENABLED || mc.level == null || mc.player == null) return;
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
            throw new IllegalStateException("Preflight requires an isolated acceptance marker");
        if (entered == 0) { entered = System.nanoTime(); LOG.info("UNIFIED_PREFLIGHT world-ready"); }
        long seconds = (System.nanoTime() - entered) / 1_000_000_000L;
        try {
            if (seconds >= 8 && !initialCapture) {
                initialCapture=true;
                Files.writeString(mc.gameDirectory.toPath().resolve("radiance-fg-capture.request"), "initial crosshair");
                LOG.info("UNIFIED_PREFLIGHT initial-crosshair");
            }
            if (step == 1 && seconds >= 25 && !paused) {
                var pause = mc.screen.getClass().getDeclaredMethod("lambda$init$9");
                pause.setAccessible(true); pause.invoke(mc.screen); paused = true;
                LOG.info("UNIFIED_PREFLIGHT ponder-identify-pause (unchanged geometry interval)");
                Files.writeString(mc.gameDirectory.toPath().resolve("radiance-fg-capture.request"), "Ponder UI PT");
            }
            if (step == 0 && seconds >= 15) {
                step++; open(mc); LOG.info("UNIFIED_PREFLIGHT ponder-open");
            } else if (step == 1 && seconds >= 35) {
                step++;
                if (paused) {
                    var pause = mc.screen.getClass().getDeclaredMethod("lambda$init$9");
                    pause.setAccessible(true); pause.invoke(mc.screen);
                }
                var method = mc.screen.getClass().getDeclaredMethod("scroll", boolean.class);
                method.setAccessible(true);
                LOG.info("UNIFIED_PREFLIGHT transition result={}", method.invoke(mc.screen, true));
            } else if (step == 2 && seconds >= 50) {
                step++; GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 3840, 2054);
                LOG.info("UNIFIED_PREFLIGHT resize=3840x2054");
            } else if (step == 3 && seconds >= 65) {
                step++; mc.setScreen(null); LOG.info("UNIFIED_PREFLIGHT ponder-close");
            } else if (step == 4 && seconds >= 72) {
                step++; open(mc); LOG.info("UNIFIED_PREFLIGHT ponder-reopen");
            } else if (step == 5 && seconds >= 85) {
                step++; mc.setScreen(null);
                mc.reloadResourcePacks().whenComplete((ignored, error) -> {
                    if (error != null) LOG.error("UNIFIED_PREFLIGHT reload-failed", error);
                    else { reloaded = true; LOG.info("UNIFIED_PREFLIGHT client-reload-complete"); }
                });
            } else if (step == 6 && reloaded && seconds >= 100) {
                step++; mc.player.connection.sendCommand("reload");
                mc.levelRenderer.allChanged(); LOG.info("UNIFIED_PREFLIGHT server-reload-and-chunks");
            } else if (step == 7 && seconds >= 110) {
                step++; mc.setScreen(new PauseScreen(true));
                Files.writeString(mc.gameDirectory.toPath().resolve("radiance-fg-capture.request"), "pause blur");
                LOG.info("UNIFIED_PREFLIGHT pause-blur");
            } else if (step == 8 && seconds >= 122) {
                step++; mc.setScreen(null);
                Files.writeString(mc.gameDirectory.toPath().resolve("radiance-fg-capture.request"), "crosshair");
                LOG.info("UNIFIED_PREFLIGHT crosshair");
            } else if (step == 9 && seconds >= 135) {
                step++; LOG.info("UNIFIED_PREFLIGHT normal-stop-requested"); mc.stop();
            }
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("Unified preflight step " + step + " failed", error);
        }
    }
    private static void open(Minecraft mc) throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
        Screen screen = (Screen) type.getMethod("of", ResourceLocation.class)
            .invoke(null, ResourceLocation.fromNamespaceAndPath("create", "mechanical_piston"));
        if (screen == null) throw new IllegalStateException("Ponder scene unavailable");
        mc.setScreen(screen);
    }
}
