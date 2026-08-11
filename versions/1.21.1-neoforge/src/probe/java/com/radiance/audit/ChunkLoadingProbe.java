package com.radiance.audit;

import com.google.gson.Gson;
import com.radiance.client.proxy.world.ChunkProxy;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;

/** Bounded copied-world loading diagnosis; no world edits or repeated failure reproduction. */
public final class ChunkLoadingProbe {
    private static final boolean ENABLED =
            Boolean.getBoolean("radiance.audit.chunkLoading") && ExperimentAccess.permitted();
    private static final boolean REBUILD =
            Boolean.getBoolean("radiance.audit.chunkLoading.rebuild");
    private static final int STOP_SECONDS =
            Integer.getInteger("radiance.audit.chunkLoading.stopSeconds", 60);
    private static final AtomicLong diskLoads = new AtomicLong(), generated = new AtomicLong();
    private static final Gson JSON = new Gson();
    private static long start, sampled;
    private static boolean rebuilt, stopped;
    private static BufferedWriter output;

    private ChunkLoadingProbe() {}

    public static void register() {
        if (!ENABLED) return;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkDataEvent.Load event) ->
                        diskLoads.incrementAndGet());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
                    if (event.isNewChunk()) generated.incrementAndGet();
                });
    }

    public static void poll(Minecraft mc) {
        if (!ENABLED || stopped || mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        try {
            if (start == 0) {
                if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve(".radiance-loading")))
                    throw new IllegalStateException("Copied loading fixture marker required");
                if (STOP_SECONDS < 30 || STOP_SECONDS > 115)
                    throw new IllegalArgumentException(
                            "Loading diagnosis duration outside [30,115]");
                start = now;
                Path result = Path.of(System.getProperty("validation.result"));
                Files.createDirectories(result);
                output = Files.newBufferedWriter(result.resolve("chunk-loading.jsonl"));
                record(mc, now, "world-ready");
            }
            if (now - sampled >= 1_000_000_000L) {
                sampled = now;
                record(mc, now, rebuilt ? "rebuild" : "initial");
            }
            if (REBUILD && !rebuilt && now - start >= 60_000_000_000L) {
                rebuilt = true;
                record(mc, now, "rebuild-request");
                mc.levelRenderer.allChanged();
                record(mc, System.nanoTime(), "rebuild-return");
            }
            if (now - start >= STOP_SECONDS * 1_000_000_000L) {
                stopped = true;
                FrameProfiler.finish();
                record(mc, now, "normal-stop");
                output.close();
                mc.stop();
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Loading counters unavailable", error);
        }
    }

    private static void record(Minecraft mc, long now, String phase)
            throws IOException, ReflectiveOperationException {
        long began = System.nanoTime();
        var row = new LinkedHashMap<String, Object>();
        row.put("elapsed_ns", now - start);
        row.put("java_ns", now);
        row.put("utc_ms", System.currentTimeMillis());
        row.put("frame", FrameProfiler.frameToken());
        row.put("phase", phase);
        row.put("rebuild_enabled", REBUILD);
        row.put("stop_seconds", STOP_SECONDS);
        row.put("ready_sections", ChunkProxy.countReadyChunksNative());
        row.put("disk_loads", diskLoads.get());
        row.put("new_server_chunks", generated.get());
        var state = new Properties();
        BenchmarkChunkState.capture(state);
        row.put("state", state);
        row.put("focused", mc.isWindowActive());
        row.put("screen", mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        row.put("player", mc.player.position().toString());
        row.put("yaw", mc.player.getYRot());
        row.put("pitch", mc.player.getXRot());
        row.put("view", mc.options.renderDistance().get());
        row.put("simulation", mc.options.simulationDistance().get());
        row.put("sample_ns", System.nanoTime() - began);
        output.write(JSON.toJson(row));
        output.newLine();
        output.flush();
        com.mojang.logging.LogUtils.getLogger()
                .info(
                        "CHUNK_LOADING phase={} frame={} elapsed_ns={}",
                        phase,
                        FrameProfiler.frameToken(),
                        now - start);
    }
}
