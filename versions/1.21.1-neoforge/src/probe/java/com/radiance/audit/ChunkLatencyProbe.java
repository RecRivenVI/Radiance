package com.radiance.audit;

import com.radiance.client.proxy.world.ChunkProxy;
import java.io.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Fixed isolated world, normal interaction packets, bounded run. Never judges pixels. */
public final class ChunkLatencyProbe {
    private static final boolean ENABLED =
            "1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_CHUNK_BENCH"));
    private static final boolean CITY = Boolean.getBoolean("radiance.audit.loadingInteractions");
    private static boolean cityActive;
    private static PrintWriter operations;
    private static final int RELOAD_DELAY_MS =
            ENABLED
                    ? Integer.parseInt(
                            System.getProperty("radiance.audit.chunkBench.reloadDelayMs", "2000"))
                    : 2000;
    private static final int RELOAD_INTERVAL_MS =
            ENABLED
                    ? Integer.parseInt(
                            System.getProperty(
                                    "radiance.audit.chunkBench.reloadIntervalMs", "1500"))
                    : 1500;
    private static long start, last, operationAt;
    private static volatile int action;
    private static int observed;
    private static volatile int serverObserved;
    private static boolean reload, stopped;
    private static BlockPos support;
    private static volatile BlockPos target;
    private static volatile long actionTime;
    private static final java.util.concurrent.atomic.AtomicLong
            diskLoads = new java.util.concurrent.atomic.AtomicLong(),
            newChunks = new java.util.concurrent.atomic.AtomicLong();
    private static PrintWriter samples, trace;
    private static java.lang.reflect.Method traceMethod;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static void register() {
        if (!ENABLED && !CITY) return;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
                    int request = action;
                    var pos = target;
                    if (request == 0 || request == serverObserved || pos == null) return;
                    var level = event.getServer().overworld();
                    if (level.hasChunkAt(pos)
                            && level.getBlockState(pos).isAir() == (request % 2 == 0)) {
                        serverObserved = request;
                        LOG.info(
                                "CHUNK_BENCH server-state operation={} java_ns={} latency_ns={}",
                                request,
                                System.nanoTime(),
                                System.nanoTime() - actionTime);
                    }
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkDataEvent.Load event) ->
                        diskLoads.incrementAndGet());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
                    if (event.isNewChunk()) newChunks.incrementAndGet();
                });
    }

    public static void poll(Minecraft mc) {
        if (CITY) {
            try {
                if (cityActive) pollCity(mc);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot observe the real chunk pipeline", failure);
            }
            return;
        }
        if (!ENABLED || stopped || mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        try {
            if (start == 0) {
                if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-acceptance")))
                    throw new IllegalStateException("Isolated marker required");
                if ("1".equals(com.radiance.audit.ExperimentAccess.getenv("RADIANCE_FACE_PROBE"))) {
                    try {
                        Class.forName("com.radiance.client.render.MaterialFaces");
                        FaceCaptureProbe.verify();
                    } catch (ClassNotFoundException baseline) {
                        LOG.info("FACE_CAPTURE_PROBE absent in baseline");
                    }
                }
                start = last = now;
                samples =
                        new PrintWriter(
                                Files.newBufferedWriter(
                                        mc.gameDirectory.toPath().resolve("chunk-benchmark.csv")));
                trace =
                        new PrintWriter(
                                Files.newBufferedWriter(
                                        mc.gameDirectory
                                                .toPath()
                                                .resolve("chunk-update-trace.txt")));
                try {
                    traceMethod = ChunkProxy.class.getMethod("drainUpdateTraceNative");
                } catch (NoSuchMethodException missing) {
                    trace.println(
                            "This artifact has no native revision/frame tracing API; do not infer update completion from ready count.");
                }
                samples.println(
                        "elapsed_ns,real_frame_interval_ns,process_cpu_ns,ready_sections,disk_deserializations,new_server_chunks,focused,phase");
                mc.options.pauseOnLostFocus = false;
                mc.setScreen(null);
                support = mc.player.blockPosition().offset(2, -1, 0);
                for (int y = 0; y < 5 && mc.level.getBlockState(support).isAir(); y++)
                    support = support.below();
                target = support.above();
                if (mc.level.getBlockState(support).isAir()
                        || !mc.level.getBlockState(target).isAir())
                    throw new IllegalStateException(
                            "Benchmark target must have solid support and empty placement cell");
                mc.getSingleplayerServer()
                        .execute(
                                () -> {
                                    var sp =
                                            mc.getSingleplayerServer()
                                                    .getPlayerList()
                                                    .getPlayer(mc.player.getUUID());
                                    if (sp != null) sp.setGameMode(GameType.CREATIVE);
                                });
                LOG.info(
                        "CHUNK_BENCH begin target={} player={} view={} sim={} dimensions={}x{}",
                        target,
                        mc.player.position(),
                        mc.options.renderDistance().get(),
                        mc.options.simulationDistance().get(),
                        mc.getWindow().getWidth(),
                        mc.getWindow().getHeight());
            }
            long elapsed = now - start;
            if (traceMethod != null) {
                long before = System.nanoTime();
                String events = (String) traceMethod.invoke(null);
                long after = System.nanoTime();
                trace.println("JAVA_BRACKET " + before + " " + after);
                trace.print(events);
                trace.flush();
            }
            long cpu =
                    ((com.sun.management.OperatingSystemMXBean)
                                    java.lang.management.ManagementFactory
                                            .getOperatingSystemMXBean())
                            .getProcessCpuTime();
            samples.println(
                    elapsed
                            + ","
                            + (now - last)
                            + ","
                            + cpu
                            + ","
                            + ChunkProxy.countReadyChunksNative()
                            + ","
                            + diskLoads.get()
                            + ","
                            + newChunks.get()
                            + ","
                            + mc.isWindowActive()
                            + ","
                            + (reload ? "reload" : "idle"));
            last = now;
            samples.flush();
            if (elapsed >= 40_000_000_000L && !reload) {
                reload = true;
                mc.levelRenderer.allChanged();
                LOG.info("CHUNK_BENCH rebuild-all elapsed_ns={}", elapsed);
            }
            if (action < 24
                    && action == observed
                    && action == serverObserved
                    && elapsed >= operationDueNs(action, RELOAD_DELAY_MS, RELOAD_INTERVAL_MS)) {
                mc.player.getInventory().selected = 0;
                mc.player.getInventory().setItem(0, new ItemStack(Items.STONE));
                mc.gameMode.handleCreativeModeItemAdd(mc.player.getMainHandItem(), 36);
                boolean place = action % 2 == 0;
                if (mc.level.getBlockState(target).isAir() != place)
                    throw new IllegalStateException(
                            "Benchmark precondition changed before operation " + action);
                operationAt = actionTime = System.nanoTime();
                action++;
                if (Boolean.getBoolean("radiance.audit.chunkBench.stateAtOperation")) {
                    java.util.Properties state = new java.util.Properties();
                    BenchmarkChunkState.capture(state);
                    LOG.info(
                            "CHUNK_BENCH pending-state operation={} java_ns={} state={}",
                            action,
                            System.nanoTime(),
                            state);
                }
                if (place)
                    mc.gameMode.useItemOn(
                            mc.player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                    Vec3.atCenterOf(support).add(0, 0.5, 0),
                                    Direction.UP,
                                    support,
                                    false));
                else mc.gameMode.startDestroyBlock(target, Direction.UP);
                LOG.info(
                        "CHUNK_BENCH operation={} kind={} java_ns={} pos={}",
                        action,
                        place ? "place" : "dig",
                        operationAt,
                        target);
            }
            if (action != observed && mc.level.getBlockState(target).isAir() == (action % 2 == 0)) {
                observed = action;
                long readAt = System.nanoTime();
                LOG.info(
                        "CHUNK_BENCH client-state operation={} java_ns={} latency_ns={}",
                        action,
                        readAt,
                        readAt - operationAt);
            }
            if (elapsed >= 75_000_000_000L) {
                stopped = true;
                samples.close();
                trace.close();
                LOG.info(
                        "CHUNK_BENCH end actions={} observed={} server_observed={} normal-stop",
                        action,
                        observed,
                        serverObserved);
                mc.stop();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static long operationDueNs(int index, int reloadDelayMs, int reloadIntervalMs) {
        if (index < 0
                || index >= 24
                || reloadDelayMs < 0
                || reloadDelayMs > 10000
                || reloadIntervalMs < 50
                || reloadIntervalMs > 2000)
            throw new IllegalArgumentException("Invalid isolated interaction schedule");
        return index < 12
                ? 12_000_000_000L + index * 1_500_000_000L
                : 40_000_000_000L
                        + reloadDelayMs * 1_000_000L
                        + (index - 12) * reloadIntervalMs * 1_000_000L;
    }

    static boolean cityActive() {
        return cityActive;
    }

    static void beginCity(Minecraft mc, BlockPos base, String scenario) throws IOException {
        if (!CITY || cityActive || !ExperimentAccess.permitted())
            throw new IllegalStateException("Invalid city interaction window");
        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-takeover-stages")))
            throw new IllegalStateException("Owned city required");
        Path result = Path.of(System.getProperty("validation.result"), "interaction-" + scenario);
        Files.createDirectory(result);
        trace = new PrintWriter(Files.newBufferedWriter(result.resolve("trace.txt")));
        operations = new PrintWriter(Files.newBufferedWriter(result.resolve("actions.jsonl")));
        // Earlier loading is outside this bounded correlation window. Preserve its
        // cumulative drop count, then require it not to increase during interactions.
        trace.println("TRACE_BASELINE");
        trace.print(ChunkProxy.drainUpdateTraceNative());
        trace.println("TRACE_WINDOW");
        trace.flush();
        support = base;
        target = base.above();
        action = observed = serverObserved = 0;
        start = System.nanoTime();
        cityActive = true;
        AuditHooks.chunkWindow(true);
        LOG.info(
                "CHUNK_BENCH city-begin scenario={} target={} java_ns={}", scenario, target, start);
    }

    private static void pollCity(Minecraft mc) throws ReflectiveOperationException {
        long now = System.nanoTime();
        long before = System.nanoTime();
        String events = ChunkProxy.drainUpdateTraceNative();
        long after = System.nanoTime();
        trace.println("JAVA_BRACKET " + before + " " + after);
        trace.print(events);
        trace.flush();
        if (action < 12
                && action == observed
                && action == serverObserved
                && now - start >= 500_000_000L + action * 1_000_000_000L) {
            var state = new java.util.Properties();
            BenchmarkChunkState.capture(state);
            mc.player.getInventory().selected = 0;
            var item =
                    switch (action / 2 % 4) {
                        case 1 -> Items.GLASS;
                        case 2 -> Items.SEA_LANTERN;
                        case 3 -> Items.OAK_LEAVES;
                        default -> Items.STONE;
                    };
            mc.player.getInventory().setItem(0, new ItemStack(item));
            mc.gameMode.handleCreativeModeItemAdd(mc.player.getMainHandItem(), 36);
            boolean place = action % 2 == 0;
            if (mc.level.getBlockState(target).isAir() != place)
                throw new IllegalStateException(
                        "Interaction target changed before action " + action);
            operationAt = actionTime = System.nanoTime();
            action++;
            var row = new java.util.LinkedHashMap<String, Object>();
            row.put("operation", action);
            row.put("java_ns", operationAt);
            row.put("kind", place ? "place" : "dig");
            row.put("item", item.toString());
            row.put("state", state);
            operations.println(new com.google.gson.Gson().toJson(row));
            operations.flush();
            if (place)
                mc.gameMode.useItemOn(
                        mc.player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3.atCenterOf(support).add(0, 0.5, 0),
                                Direction.UP,
                                support,
                                false));
            else mc.gameMode.startDestroyBlock(target, Direction.UP);
            LOG.info(
                    "CHUNK_BENCH operation={} kind={} java_ns={} pos={}",
                    action,
                    place ? "place" : "dig",
                    operationAt,
                    target);
        }
        if (action != observed && mc.level.getBlockState(target).isAir() == (action % 2 == 0)) {
            observed = action;
            long at = System.nanoTime();
            LOG.info(
                    "CHUNK_BENCH client-state operation={} java_ns={} latency_ns={}",
                    action,
                    at,
                    at - operationAt);
        }
        if (action == 12 && now - operationAt >= 3_000_000_000L) {
            if (observed != 12 || serverObserved != 12)
                throw new IllegalStateException("Interaction packets were not acknowledged");
            operations.close();
            trace.close();
            AuditHooks.chunkWindow(false);
            cityActive = false;
            LOG.info(
                    "CHUNK_BENCH city-end actions={} observed={} server_observed={}",
                    action,
                    observed,
                    serverObserved);
        } else if (now - start >= 40_000_000_000L) {
            throw new IllegalStateException("Interaction window exceeded its bound");
        }
    }
}
