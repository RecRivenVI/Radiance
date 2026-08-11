package com.radiance.audit;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Fixed source-heavy scene; never executes comparison renderers inside measured windows. */
public final class PersistentSourceStressProbe {
    private static int step;
    private static long since;
    private static CompletableFuture<?> setup;

    private PersistentSourceStressProbe() {}

    public static void poll(Minecraft mc) {
        if (!Boolean.getBoolean("radiance.audit.sourceStress") || step == 5) return;
        try {
            long now = System.nanoTime();
            if (step == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                if (!CityFrameComparisonProbe.viewportReady(mc, now)) return;
                step = 1;
                mc.createWorldOpenFlows()
                        .openWorld("Audit Benchmark", () -> mc.setScreen(new TitleScreen()));
            }
            if (mc.player == null || mc.level == null) return;
            if (step == 1) {
                step = 2;
                since = now;
                mc.setScreen(new ChatScreen(""));
                com.mojang.logging.LogUtils.getLogger().info("SOURCE_STRESS world-ready");
                var server = mc.getSingleplayerServer();
                setup =
                        CompletableFuture.runAsync(
                                () -> {
                                    var source =
                                            server.createCommandSourceStack()
                                                    .withSuppressedOutput()
                                                    .withPermission(4);
                                    var commands = server.getCommands();
                                    for (var command :
                                            java.util.List.of(
                                                    "gamerule doMobSpawning false",
                                                    "gamerule doDaylightCycle false",
                                                    "time set noon",
                                                    "weather clear",
                                                    "gamemode spectator RecRivenVI",
                                                    "tp RecRivenVI 0 70 20 facing 0 66 0"))
                                        commands.performPrefixedCommand(source, command);
                                    for (int i = 0; i < 96; i++) {
                                        int x = -12 + (i % 12) * 2, z = -8 + (i / 12) * 2;
                                        String type =
                                                new String[] {
                                                            "cow",
                                                            "armor_stand",
                                                            "creeper",
                                                            "enderman"
                                                        }
                                                        [i % 4];
                                        commands.performPrefixedCommand(
                                                source,
                                                "summon minecraft:"
                                                        + type
                                                        + " "
                                                        + x
                                                        + " 65 "
                                                        + z
                                                        + " {Tags:[\"source_stress_130\"],NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Invulnerable:1b}");
                                        if (i % 2 == 0)
                                            commands.performPrefixedCommand(
                                                    source,
                                                    "setblock "
                                                            + x
                                                            + " 65 "
                                                            + (z - 1)
                                                            + " minecraft:oak_sign{front_text:{messages:['{\"text\":\"Stable source\"}','{\"text\":\"130\"}','{\"text\":\"\"}','{\"text\":\"\"}']}}");
                                    }
                                },
                                server);
            } else if (step == 2 && setup.isDone() && now - since > 45_000_000_000L) {
                setup.join();
                FrameProfiler.request(15, "source-stress-frame-only", ProfileMode.FRAME_ONLY);
                step = 3;
            } else if (step == 3 && !FrameProfiler.requested()) {
                FrameProfiler.request(15, "source-stress-timed", ProfileMode.TIMED);
                step = 4;
            } else if (step == 4 && !FrameProfiler.requested()) {
                step = 5;
                com.mojang.logging.LogUtils.getLogger()
                        .info("SOURCE_STRESS PASS normal-stop; no visual acceptance implied");
                mc.stop();
            }
        } catch (Exception | LinkageError | AssertionError failure) {
            step = 5;
            com.mojang.logging.LogUtils.getLogger()
                    .error("SOURCE_STRESS FAIL preserve, no retry", failure);
            mc.stop();
        }
    }
}
