package com.radiance.audit;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** One bounded loading timeline in an owned fixture; normal commands, no renderer setters. */
public final class LoadingChainProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.loadingChain");
    private static final boolean MOVEMENT = Boolean.getBoolean("radiance.audit.loadingMovement");
    private static final boolean INTERACTIONS =
            Boolean.getBoolean("radiance.audit.loadingInteractions");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final AtomicLong DISK = new AtomicLong(),
            GENERATED = new AtomicLong(),
            RECEIVED = new AtomicLong();
    private static final Gson JSON = new Gson();
    private static BufferedWriter output;
    private static long start,
            nextSample,
            quietSince,
            stableSince,
            lastDisk,
            lastGenerated,
            lastReceived;
    private static int step;
    private static CompletableFuture<Integer> teleport;
    private static long finishRequestedAt;
    private static String stopReason;
    private static int routeStep;
    private static long nextRouteStep;

    private LoadingChainProbe() {}

    public static boolean enabled() {
        return ENABLED && ExperimentAccess.permitted();
    }

    static void register() {
        if (!enabled()) return;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkDataEvent.Load event) ->
                        DISK.incrementAndGet());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
                    if (event.isNewChunk()) GENERATED.incrementAndGet();
                });
    }

    public static void received() {
        if (ENABLED) RECEIVED.incrementAndGet();
    }

    public static void poll(Minecraft mc) {
        if (!enabled() || step == 5) return;
        long now = System.nanoTime();
        try {
            if (step == 6) {
                if (FrameProfiler.requested()) return;
                if (now - finishRequestedAt > 15_000_000_000L)
                    throw new IllegalStateException(
                            "Profile writer did not finish before shutdown");
                boolean written;
                try (var directories =
                        Files.list(Path.of(System.getProperty("validation.result"), "profiles"))) {
                    written =
                            directories
                                    .filter(Files::isDirectory)
                                    .allMatch(p -> Files.isRegularFile(p.resolve("STATUS.txt")));
                }
                if (!written) return;
                record(mc, now, "normal-stop", null);
                recordSettings("normal-stop");
                output.close();
                step = 5;
                LOG.info(
                        "LOADING_CHAIN normal-stop reason={} stable={}",
                        stopReason,
                        stableSince != 0);
                CityFrameComparisonProbe.writeSourceObservations();
                mc.stop();
                return;
            }
            if (step == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                if (!Files.isRegularFile(
                        mc.gameDirectory.toPath().resolve(".radiance-takeover-stages")))
                    throw new IllegalStateException("Owned loading fixture required");
                if (!CityFrameComparisonProbe.viewportReady(mc, now)) return;
                output =
                        Files.newBufferedWriter(
                                Path.of(
                                        System.getProperty("validation.result"),
                                        "loading-chain.jsonl"));
                boolean detail = Boolean.getBoolean("radiance.audit.loadingDetail");
                if ((detail && (MOVEMENT || INTERACTIONS)) || (MOVEMENT && INTERACTIONS))
                    throw new IllegalArgumentException(
                            "Movement uses the lightweight whole capture");
                if (FrameProfiler.request(
                                detail ? 65 : MOVEMENT || INTERACTIONS ? 365 : 245,
                                "loading-chain",
                                detail ? ProfileMode.TIMED : ProfileMode.FRAME_ONLY)
                        != 1) throw new IllegalStateException("Another capture is active");
                step = 1;
                return;
            }
            if (step == 1) {
                if (!FrameProfiler.collecting()) return;
                start = now;
                record(mc, now, "open-world", null);
                step = 2;
                mc.createWorldOpenFlows()
                        .openWorld(
                                System.getProperty("radiance.audit.cityWorld", "Newport City"),
                                () -> mc.setScreen(new TitleScreen()));
            }
            if (start == 0) return;
            if (now - start
                            >= (Boolean.getBoolean("radiance.audit.loadingDetail")
                                    ? 60_000_000_000L
                                    : MOVEMENT || INTERACTIONS
                                            ? 360_000_000_000L
                                            : 240_000_000_000L)
                    || Files.isRegularFile(
                            mc.gameDirectory.toPath().resolve("loading-chain-stop.request"))) {
                stop(mc, now, "bounded-timeout");
                return;
            }
            if (mc.level == null || mc.player == null) return;
            if (step == 2) {
                record(mc, now, "world-ready", null);
                LOG.info("LOADING_CHAIN world-ready");
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                if (server == null) throw new IllegalStateException("Integrated server required");
                teleport =
                        server.submit(
                                () -> {
                                    var player = server.getPlayerList().getPlayer(uuid);
                                    if (player == null
                                            || !player.getAbilities().mayfly
                                            || !player.getAbilities().flying)
                                        throw new IllegalStateException(
                                                "Frozen camera requires existing creative flight");
                                    server.getCommands()
                                            .performPrefixedCommand(
                                                    player.createCommandSourceStack()
                                                            .withPermission(4),
                                                    "tp @s 0 512 0 facing 0 0 0");
                                    return 1;
                                });
                mc.setScreen(new ChatScreen(""));
                step = 3;
            }
            if (step == 3 && teleport.isDone()) {
                if (teleport.join() != 1)
                    throw new IllegalStateException("Teleport command failed");
                // Vanilla Vec3 command arguments center integer X/Z on the target block.
                if (mc.player.position().distanceToSqr(0.5, 512, 0.5) > 1e-6) {
                    if (now - start > 30_000_000_000L)
                        throw new IllegalStateException(
                                "Teleport did not reach its vanilla block-center destination");
                } else {
                    record(mc, now, "camera-fixed", null);
                    recordSettings("camera-fixed");
                    step = 4;
                }
            }
            if (step == 7) advanceRoute(mc, now);
            if (step >= 9 && step <= 12) advanceInteractions(mc, now);
            if (now < nextSample) return;
            nextSample = now + 1_000_000_000L;
            var state = new Properties();
            BenchmarkChunkState.capture(state);
            long disk = DISK.get(), generated = GENERATED.get(), received = RECEIVED.get();
            boolean quiet =
                    (step == 4 || step == 8 || step == 9)
                            && CityFrameComparisonProbe.quiet(state)
                            && BenchmarkChunkState.noNativeWork(state)
                            && disk == lastDisk
                            && generated == lastGenerated
                            && received == lastReceived;
            lastDisk = disk;
            lastGenerated = generated;
            lastReceived = received;
            if (quiet) {
                if (quietSince == 0) {
                    quietSince = now;
                    record(mc, now, "quiet-candidate", state);
                }
                if (stableSince == 0 && now - quietSince >= 10_000_000_000L) {
                    stableSince = now;
                    record(mc, now, "quiet-confirmed", state);
                }
            } else {
                if (quietSince != 0) record(mc, now, "quiet-interrupted", state);
                quietSince = stableSince = 0;
            }
            record(mc, now, stableSince == 0 ? "loading" : "steady", state);
            if (step == 9 && stableSince != 0 && teleport == null) {
                ChunkLatencyProbe.beginCity(
                        mc, new net.minecraft.core.BlockPos(15, 255, 15), "idle");
                step = 10;
            } else if ((step == 4 || step == 8)
                    && stableSince != 0
                    && now - stableSince >= 20_000_000_000L) {
                if (MOVEMENT && step == 4) {
                    record(mc, now, "route-start", state);
                    step = 7;
                    quietSince = stableSince = 0;
                    teleport = null;
                    nextRouteStep = now;
                } else if (INTERACTIONS) {
                    record(mc, now, "interactions-start", state);
                    prepareInteractionPad(mc, false);
                    step = 9;
                    quietSince = stableSince = 0;
                } else stop(mc, now, "stable-window-complete");
            }
        } catch (Exception | LinkageError failure) {
            step = 5;
            LOG.error("LOADING_CHAIN FAIL; stop without retry", failure);
            FrameProfiler.finish();
            try {
                if (output != null) output.close();
            } catch (java.io.IOException close) {
                failure.addSuppressed(close);
            }
            mc.stop();
        }
    }

    static double routeX(int step) {
        if (step < 0 || step > 64) throw new IllegalArgumentException("Outside frozen route");
        return 0.5 + 8.0 * (step <= 32 ? step : 64 - step);
    }

    private static void prepareInteractionPad(Minecraft mc, boolean streaming) {
        int x = streaming ? 271 : 15;
        var uuid = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        teleport =
                server.submit(
                        () -> {
                            var player = server.getPlayerList().getPlayer(uuid);
                            if (player == null)
                                throw new IllegalStateException("Interaction player disappeared");
                            var world = player.serverLevel();
                            var support = new net.minecraft.core.BlockPos(x, 255, 15);
                            if (!world.getBlockState(support.above()).isAir())
                                throw new IllegalStateException(
                                        "Frozen city boundary target must be empty");
                            world.setBlockAndUpdate(
                                    support,
                                    net.minecraft.world.level.block.Blocks.STONE
                                            .defaultBlockState());
                            player.teleportTo(world, x - 0.5, 258, 14.5, 0, 45);
                            return 1;
                        });
    }

    private static void advanceInteractions(Minecraft mc, long now) throws Exception {
        if (step == 10) {
            if (ChunkLatencyProbe.cityActive()) return;
            record(mc, now, "interaction-idle-finished", null);
            prepareInteractionPad(mc, true);
            quietSince = stableSince = 0;
            step = 11;
        }
        if (step == 9 || step == 11) {
            if (teleport == null || !teleport.isDone()) return;
            teleport.join();
            int x = step == 9 ? 15 : 271;
            var support = new net.minecraft.core.BlockPos(x, 255, 15);
            if (mc.player.position().distanceToSqr(x - 0.5, 258, 14.5) > 1e-6
                    || mc.level.getBlockState(support).isAir()) return;
            teleport = null;
            if (step == 11) {
                ChunkLatencyProbe.beginCity(mc, support, "loading");
                record(mc, now, "interaction-loading-start", null);
                step = 12;
            }
        }
        if (step == 12 && !ChunkLatencyProbe.cityActive()) {
            record(mc, now, "interaction-loading-finished", null);
            stop(mc, now, "interaction-windows-complete");
        }
    }

    private static void advanceRoute(Minecraft mc, long now) throws Exception {
        // One server-authoritative request at a time. Await the client acknowledgement;
        // never skip waypoints or queue teleports behind a slow server/render frame.
        if (teleport != null) {
            if (!teleport.isDone()) return;
            teleport.join();
            if (mc.player.position().distanceToSqr(routeX(routeStep), 512, 0.5) > 1e-6) return;
            teleport = null;
            record(mc, now, "route-waypoint", null);
            if (routeStep == 64) {
                record(mc, now, "route-finished", null);
                step = 8;
                quietSince = stableSince = 0;
                return;
            }
        }
        if (now < nextRouteStep) return;
        nextRouteStep = now + 250_000_000L;
        double x = routeX(++routeStep);
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        teleport =
                server.submit(
                        () -> {
                            var player = server.getPlayerList().getPlayer(uuid);
                            if (player == null)
                                throw new IllegalStateException("Route player disappeared");
                            player.teleportTo(player.serverLevel(), x, 512, 0.5, -90, 90);
                            return 1;
                        });
    }

    private static void stop(Minecraft mc, long now, String reason) throws Exception {
        record(mc, now, "capture-stop-request", null);
        stopReason = reason;
        finishRequestedAt = now;
        step = 6;
        // Stop at the next frame boundary, then let the asynchronous writer flush before exit.
        FrameProfiler.requestStop();
    }

    private static void recordSettings(String phase) throws Exception {
        var hooks =
                ClassLoader.getSystemClassLoader()
                        .loadClass("com.radiance.audit.benchmark.RuntimeHooks");
        var settings =
                (Properties)
                        hooks.getMethod("readSettings", Class.class, Class.class)
                                .invoke(
                                        null,
                                        com.radiance.client.pipeline.Pipeline.class,
                                        com.radiance.client.option.Options.class);
        try (var writer =
                Files.newBufferedWriter(
                        Path.of(
                                System.getProperty("validation.result"),
                                phase + "-settings.properties"))) {
            settings.store(writer, "Observed active settings; no setters invoked");
        }
    }

    private static void record(Minecraft mc, long now, String phase, Properties state)
            throws Exception {
        var row = new LinkedHashMap<String, Object>();
        row.put("phase", phase);
        row.put("java_ns", now);
        row.put("elapsed_ns", now - start);
        row.put("utc_ms", System.currentTimeMillis());
        row.put("frame", FrameProfiler.frameToken());
        row.put("disk_loads", DISK.get());
        row.put("new_server_chunks", GENERATED.get());
        row.put("client_columns", RECEIVED.get());
        row.put("stable", stableSince != 0);
        row.put("state", state);
        if (mc.player != null) {
            row.put("x", mc.player.getX());
            row.put("y", mc.player.getY());
            row.put("z", mc.player.getZ());
            row.put("yaw", mc.player.getYRot());
            row.put("pitch", mc.player.getXRot());
            row.put("view", mc.options.renderDistance().get());
            row.put("simulation", mc.options.simulationDistance().get());
            row.put(
                    "ready_sections",
                    com.radiance.client.proxy.world.ChunkProxy.countReadyChunksNative());
        }
        output.write(JSON.toJson(row));
        output.newLine();
        output.flush();
    }
}
