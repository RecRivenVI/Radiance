package com.radiance.audit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Explicit isolated GPU-memory lifecycle probe: after the world has been entered for a while it lowers
 * the render distance, later leaves to the title screen the way the pause menu does, waits there and
 * stops the client. MEMORY_PROBE log markers let native memory reports and process GPU samples be
 * aligned with each phase. It changes only this isolated client's options and never judges pixels.
 */
public final class MemoryLifecycleProbe {
    private static final boolean ENABLED = "1".equals(ExperimentAccess.getenv("RADIANCE_MEMORY_PROBE"));
    private static final long WORLD_NS = seconds("worldSeconds", 150);
    private static final int REDUCED_DISTANCE = Integer.getInteger("radiance.audit.memoryProbe.reducedDistance", 16);
    private static final long REDUCED_NS = seconds("reducedSeconds", 90);
    private static final long TITLE_NS = seconds("titleSeconds", 60);
    private static final String REENTER_WORLD = System.getProperty("radiance.audit.memoryProbe.reenterWorld", "");
    private static final long REENTER_NS = seconds("reenterSeconds", 60);
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private enum Phase { WAIT_WORLD, WORLD, REDUCED, LEAVING, TITLE, WAIT_REENTER, WORLD_AGAIN, DONE }
    private static Phase phase = Phase.WAIT_WORLD;
    private static long since;
    private static boolean reentered;

    private MemoryLifecycleProbe() {}

    private static long seconds(String name, long fallback) {
        return Math.clamp(Long.getLong("radiance.audit.memoryProbe." + name, fallback), 5, 3600) * 1_000_000_000L;
    }

    public static void poll(Minecraft minecraft) {
        if (!ENABLED || phase == Phase.DONE) return;
        long now = System.nanoTime();
        switch (phase) {
            case WAIT_WORLD -> {
                if (minecraft.level != null && minecraft.player != null) {
                    phase = Phase.WORLD;
                    since = now;
                    LOG.info("MEMORY_PROBE world-entered renderDistance={}", minecraft.options.renderDistance().get());
                }
            }
            case WORLD -> {
                if (now - since >= WORLD_NS && minecraft.level != null) {
                    int previous = minecraft.options.renderDistance().get();
                    minecraft.options.renderDistance().set(REDUCED_DISTANCE);
                    phase = Phase.REDUCED;
                    since = now;
                    LOG.info("MEMORY_PROBE render-distance {} -> {}", previous, minecraft.options.renderDistance().get());
                }
            }
            case REDUCED -> {
                if (now - since >= REDUCED_NS && minecraft.level != null) {
                    // Leave the phase first: disconnect() runs nested client ticks while the integrated
                    // server stops, and those ticks poll this probe again.
                    phase = Phase.LEAVING;
                    since = now;
                    LOG.info("MEMORY_PROBE leave-world");
                    // Same sequence as the pause menu's disconnect for a singleplayer world.
                    minecraft.level.disconnect();
                    minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
                    minecraft.setScreen(new TitleScreen());
                    phase = Phase.TITLE;
                    since = System.nanoTime();
                    LOG.info("MEMORY_PROBE title-screen");
                }
            }
            case TITLE -> {
                if (now - since >= TITLE_NS) {
                    if (!reentered && !REENTER_WORLD.isBlank()) {
                        reentered = true;
                        phase = Phase.WAIT_REENTER;
                        LOG.info("MEMORY_PROBE reenter-request world={}", REENTER_WORLD);
                        minecraft.createWorldOpenFlows().openWorld(REENTER_WORLD,
                            () -> minecraft.setScreen(new TitleScreen()));
                        return;
                    }
                    phase = Phase.DONE;
                    LOG.info("MEMORY_PROBE normal-stop");
                    minecraft.stop();
                }
            }
            case WAIT_REENTER -> {
                if (minecraft.level != null && minecraft.player != null) {
                    phase = Phase.WORLD_AGAIN;
                    since = now;
                    LOG.info("MEMORY_PROBE world-reentered renderDistance={}", minecraft.options.renderDistance().get());
                }
            }
            case WORLD_AGAIN -> {
                if (now - since >= REENTER_NS && minecraft.level != null) {
                    phase = Phase.LEAVING;
                    LOG.info("MEMORY_PROBE leave-world-again");
                    minecraft.level.disconnect();
                    minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
                    minecraft.setScreen(new TitleScreen());
                    phase = Phase.TITLE;
                    since = System.nanoTime();
                    LOG.info("MEMORY_PROBE title-screen-again");
                }
            }
            default -> { }
        }
    }
}
