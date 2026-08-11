package com.radiance.audit;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Minimal control/counter for measuring the common observer/SDK cost, not stage attribution. */
public final class MinimalCityBenchmark {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final List<long[]> frames = new ArrayList<>();
    private static int step;
    private static long started, entered, nextPoll, quietSince, captureStart, previousStart;

    private MinimalCityBenchmark() {}

    public static void frame(Minecraft mc, long start) {
        long end = System.nanoTime();
        if (step == 4) return;
        try {
            if (!ExperimentAccess.permitted())
                throw new IllegalStateException("Owned minimal benchmark required");
            if (started == 0) started = end;
            if (end - started > 600_000_000_000L)
                throw new IllegalStateException("Minimal city timeout");
            if (step == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                if (!CityFrameComparisonProbe.viewportReady(mc, end)) return;
                step = 1;
                mc.createWorldOpenFlows()
                        .openWorld("Newport City", () -> mc.setScreen(new TitleScreen()));
            }
            if (mc.level == null || mc.player == null) return;
            if (step == 1) {
                step = 2;
                entered = end;
                mc.setScreen(new ChatScreen(""));
                LOG.info("CITY_COMPARISON world-ready controller-only=true");
            }
            if (step == 2 && end >= nextPoll) {
                nextPoll = end + 1_000_000_000L;
                var state = new Properties();
                BenchmarkChunkState.capture(state);
                if (CityFrameComparisonProbe.quiet(state)) {
                    if (quietSince == 0) quietSince = end;
                } else quietSince = 0;
                if (end - entered >= 60_000_000_000L
                        && quietSince != 0
                        && end - quietSince >= 10_000_000_000L) {
                    step = 3;
                    captureStart = end;
                    EffectiveRenderSettings.checkpoint(mc, "minimal");
                    LOG.info("MINIMAL_CITY capture-start state={}", state);
                    return;
                }
            } else if (step == 3) {
                frames.add(
                        new long[] {
                            start, end - start, previousStart == 0 ? -1 : start - previousStart
                        });
                previousStart = start;
                if (end - captureStart
                        >= Integer.getInteger("radiance.audit.cityProfileSeconds", 30)
                                * 1_000_000_000L) {
                    var dir = Path.of(System.getProperty("validation.result"));
                    var text = new StringBuilder("start_ns,wall_ns,start_interval_ns\n");
                    for (var row : frames)
                        text.append(row[0])
                                .append(',')
                                .append(row[1])
                                .append(',')
                                .append(row[2])
                                .append('\n');
                    Files.writeString(dir.resolve("minimal-frames.csv"), text);
                    var camera = mc.gameRenderer.getMainCamera();
                    Files.writeString(
                            dir.resolve("minimal-settings.json"),
                            new Gson()
                                    .toJson(
                                            java.util.Map.of(
                                                    "width",
                                                    mc.getWindow().getWidth(),
                                                    "height",
                                                    mc.getWindow().getHeight(),
                                                    "distance",
                                                    mc.options.renderDistance().get(),
                                                    "position",
                                                    List.of(
                                                            camera.getPosition().x,
                                                            camera.getPosition().y,
                                                            camera.getPosition().z),
                                                    "yaw",
                                                    camera.getYRot(),
                                                    "pitch",
                                                    camera.getXRot(),
                                                    "frames",
                                                    frames.size(),
                                                    "nativeCollector",
                                                    false)));
                    CityFrameComparisonProbe.writeSourceObservations();
                    step = 4;
                    LOG.info("MINIMAL_CITY normal-stop frames={}", frames.size());
                    mc.stop();
                }
            }
        } catch (Exception | LinkageError failure) {
            step = 4;
            LOG.error("MINIMAL_CITY FAIL; preserve evidence, no retry", failure);
            mc.stop();
        }
    }
}
