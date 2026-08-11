package com.radiance.audit;

import com.radiance.client.pipeline.Pipeline;
import com.radiance.compatibility.voxy.VoxyNative;
import com.radiance.compatibility.voxy.VoxyPtBridge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.sounds.SoundSource;

/** Bounded functional source-to-GPU exercise. Never samples timings or mutates another instance. */
public final class VoxyWorldProbe {
    private static final boolean ENABLED =
            Boolean.getBoolean("radiance.audit.voxy") && ExperimentAccess.permitted();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final List<Map<String, Object>> stages = new ArrayList<>();
    private static long started, since, oldEpoch;
    private static int step;
    private static boolean advancedFrameObserved;
    private static CompletableFuture<?> reload;

    private VoxyWorldProbe() {}

    public static void poll(Minecraft mc) {
        if (!ENABLED || step == 6 || step == 9) return;
        if (Boolean.getBoolean("radiance.audit.voxy.pipelineOnly")) {
            if (mc.screen instanceof TitleScreen) {
                var advanced =
                        Pipeline.getAvailableShaderPacks().stream()
                                .filter(
                                        choice ->
                                                choice.relativePath()
                                                        .replace('\\', '/')
                                                        .endsWith("/advanced.zip"))
                                .findFirst()
                                .orElseThrow();
                if (!Pipeline.isShaderPackActive(advanced))
                    throw new IllegalStateException(
                            "Pipeline-only check did not actually select Advanced");
                LOG.info(
                        "VOXY_PIPELINE_ONLY Advanced title reached; no world was entered; stopping normally");
                step = 9;
                mc.stop();
            }
            return;
        }
        long now = System.nanoTime();
        if (started != 0 && now - started > 600_000_000_000L)
            throw new IllegalStateException("Voxy functional validation exceeded bounded duration");
        if (step == 7 && now - since > 3_000_000_000L) {
            step = 8;
            mc.createWorldOpenFlows()
                    .openWorld("Audit Benchmark", () -> mc.setScreen(new TitleScreen()));
            LOG.info("VOXY_WORLD reentry requested");
            return;
        }
        if (mc.level == null || mc.player == null) return;
        try {
            if (started == 0) {
                if (!Files.isRegularFile(
                        mc.gameDirectory.toPath().resolve(".radiance-voxy-validation")))
                    throw new IllegalStateException("Owned Voxy functional fixture required");
                if (mc.getWindow().getWidth() != 1920
                        || mc.getWindow().getHeight() != 1080
                        || mc.options.getSoundSourceVolume(SoundSource.MASTER) != 0)
                    throw new IllegalStateException("Voxy validation must be silent at 1920x1080");
                started = since = now;
                mc.options.pauseOnLostFocus = false;
                LOG.info(
                        "VOXY_WORLD ready; functional-only; source Voxy storage/ingestion; no GL renderer");
            }
            long[] counters = VoxyPtBridge.counters();
            boolean ready =
                    counters.length >= 5 && counters[1] > 0 && counters[2] >= 7 && counters[4] > 0;
            // poll runs at runTick RETURN. Start observation after the rebuilt pipeline
            // has returned a frame, rather than counting synchronous compilation as viewing.
            if (step == 4 && !advancedFrameObserved) {
                advancedFrameObserved = true;
                since = now;
                return;
            }
            long seconds = (now - since) / 1_000_000_000L;
            if (step == 0 && seconds >= 18 && ready) {
                record("vanilla-initial", counters);
                teleport(mc, 320);
                step = 1;
                since = now;
            } else if (step == 1 && seconds >= 22 && mc.player.getX() > 300 && ready) {
                record("far-source-ingested", counters);
                teleport(mc, .5);
                step = 2;
                since = now;
            } else if (step == 2 && seconds >= 22 && mc.player.getX() < 10 && ready) {
                record("near-far-return", counters);
                Screenshot.grab(
                        mc.gameDirectory,
                        "voxy-vanilla.png",
                        mc.getMainRenderTarget(),
                        result -> LOG.info("VOXY_WORLD vanilla capture {}", result.getString()));
                oldEpoch = VoxyPtBridge.epoch();
                reload = mc.reloadResourcePacks();
                step = 3;
                since = now;
            } else if (step == 3
                    && reload != null
                    && reload.isDone()
                    && ready
                    && VoxyPtBridge.epoch() > oldEpoch) {
                reload.join();
                if (VoxyNative.section(oldEpoch, 0, 1, 0, 0))
                    throw new IllegalStateException("Retired Voxy epoch accepted a section");
                record("reload-old-epoch-rejected", counters);
                var advanced =
                        Pipeline.getAvailableShaderPacks().stream()
                                .filter(
                                        choice ->
                                                choice.relativePath()
                                                        .replace('\\', '/')
                                                        .endsWith("/advanced.zip"))
                                .findFirst()
                                .orElseThrow(
                                        () -> new IllegalStateException("Advanced pack missing"));
                if (!Pipeline.setShaderPack(advanced) && !Pipeline.isShaderPackActive(advanced))
                    throw new IllegalStateException("Advanced pack was not selected");
                step = 4;
                since = now;
            } else if (step == 4 && seconds >= 12 && ready) {
                record("advanced-scene", counters);
                Screenshot.grab(
                        mc.gameDirectory,
                        "voxy-advanced.png",
                        mc.getMainRenderTarget(),
                        result -> LOG.info("VOXY_WORLD advanced capture {}", result.getString()));
                oldEpoch = VoxyPtBridge.epoch();
                step = 5;
                since = now;
            } else if (step == 5 && seconds >= 3) {
                disconnectOnce(
                        () -> {
                            mc.level.disconnect();
                            mc.disconnect(
                                    new net.minecraft.client.gui.screens.GenericMessageScreen(
                                            net.minecraft.network.chat.Component.translatable(
                                                    "menu.savingLevel")));
                            mc.setScreen(new TitleScreen());
                            if (VoxyPtBridge.active())
                                throw new IllegalStateException("Voxy session survived world exit");
                        });
                step = 7;
                since = now;
                LOG.info("VOXY_WORLD world exited; source lease and PT session released");
            } else if (step == 8 && ready && VoxyPtBridge.epoch() > oldEpoch) {
                record("world-reentered", counters);
                Path output = Path.of(System.getProperty("validation.result"));
                Files.createDirectories(output);
                Files.writeString(
                        output.resolve("voxy-functional.json"),
                        new com.google.gson.GsonBuilder()
                                        .setPrettyPrinting()
                                        .create()
                                        .toJson(
                                                Map.of(
                                                        "stages",
                                                        stages,
                                                        "runtimeStagesPassed",
                                                        true,
                                                        "visualAcceptance",
                                                        "pending-user",
                                                        "performanceMeasured",
                                                        false,
                                                        "shutdown",
                                                        "normal-stop-requested"))
                                + "\n");
                step = 9;
                LOG.info(
                        "VOXY_WORLD functional stages passed; normal-stop requested; visual pending");
                mc.stop();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Voxy functional evidence", e);
        }
    }

    static boolean disconnectOnce(Runnable disconnect) {
        if (step != 5) return false;
        // Minecraft.disconnect renders a progress screen via runTick, reentering this probe.
        step = 6;
        disconnect.run();
        return true;
    }

    private static void record(String stage, long[] counters) {
        stages.add(Map.of("stage", stage, "epoch", VoxyPtBridge.epoch(), "counters", counters));
        LOG.info(
                "VOXY_WORLD stage={} epoch={} resident={} selected={} models={} bytes={} recorded-builds={}",
                stage,
                VoxyPtBridge.epoch(),
                counters[0],
                counters[1],
                counters[2],
                counters[3],
                counters[4]);
    }

    private static void teleport(Minecraft mc, double x) {
        var server = mc.getSingleplayerServer();
        if (server == null)
            throw new IllegalStateException("Voxy fixture requires its integrated server");
        var uuid = mc.player.getUUID();
        server.execute(
                () -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    if (player == null) throw new IllegalStateException("Fixture player missing");
                    if (x > 300) {
                        var source = server.createCommandSourceStack();
                        server.getCommands().performPrefixedCommand(source, "function audit:build");
                        if (!player.serverLevel()
                                .getBlockState(new net.minecraft.core.BlockPos(312, 68, 0))
                                .is(net.minecraft.world.level.block.Blocks.GLOWSTONE))
                            throw new IllegalStateException(
                                    "Voxy remote fixture was not actually built");
                    }
                    player.connection.teleport(x, 75, .5, -90, 5);
                });
    }
}
