package com.radiance.audit;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Opt-in copied-world comparison; camera and graphics come from the prepared fixture. */
public final class CityFrameComparisonProbe {
    private static final boolean ENABLED = Boolean.getBoolean("radiance.audit.cityComparison");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long entered, quietSince, nextPoll, deadline;
    private static long viewportDeadline;
    private static boolean viewportConfigured;
    private static int step;
    private static CompletableFuture<Void> reload;
    private static int captureIndex;
    private static ProfileMode[] modes;
    private static long betweenUntil;
    private static long nextProgress;
    private static boolean screenshotCaptured;
    private static final java.util.List<Object> partWindows = new java.util.ArrayList<>();

    private static void startCensus(long now) {
        ProducerCensus.start(
                java.nio.file.Path.of(System.getProperty("validation.result"), "route-census"),
                true,
                true);
        betweenUntil = now + 3_000_000_000L;
        step = 6;
    }

    static ProfileMode[] captureModes(String plan) {
        return switch (plan) {
            case "functional" -> new ProfileMode[0];
            case "standard" -> new ProfileMode[] {ProfileMode.TIMED};
            case "throughput" -> new ProfileMode[] {ProfileMode.FRAME_ONLY};
            case "takeover" -> new ProfileMode[] {ProfileMode.FRAME_ONLY, ProfileMode.TIMED};
            case "vanilla" ->
                    new ProfileMode[] {
                        ProfileMode.FRAME_ONLY, ProfileMode.TIMED, ProfileMode.JFR_ONLY
                    };
            case "producers" -> new ProfileMode[] {ProfileMode.PRODUCERS};
            case "incremental" ->
                    new ProfileMode[] {
                        ProfileMode.TIMED, ProfileMode.PRODUCERS, ProfileMode.CPU_SAMPLES
                    };
            case "calibration" ->
                    new ProfileMode[] {
                        ProfileMode.FRAME_ONLY,
                        ProfileMode.TIMED,
                        ProfileMode.TIMED,
                        ProfileMode.FRAME_ONLY,
                        ProfileMode.FRAME_ONLY,
                        ProfileMode.TIMED
                    };
            case "attribution" ->
                    new ProfileMode[] {
                        ProfileMode.TIMED,
                        ProfileMode.PRODUCERS,
                        ProfileMode.CPU_SAMPLES,
                        ProfileMode.CONTENT_AUDIT,
                        ProfileMode.TIMED
                    };
            default -> throw new IllegalArgumentException("Unknown city profile plan: " + plan);
        };
    }

    private CityFrameComparisonProbe() {}

    static boolean quiet(Properties state) {
        for (String key :
                new String[] {
                    "columnQueue", "rebuildQueue", "importantBuildsInFlight", "normalBuildsInFlight"
                }) if (!"0".equals(state.getProperty("chunk.java." + key))) return false;
        if (!"false".equals(state.getProperty("chunk.java.initialSeedPending"))) return false;
        var nativeState = new Properties();
        for (String field : state.getProperty("chunk.native", "").split(";")) {
            String[] pair = field.trim().split("=", 2);
            if (pair.length == 2) nativeState.setProperty(pair[0], pair[1]);
        }
        for (String key :
                new String[] {"queued_builds", "pending_batch_builds", "active_gpu_batches"})
            if (!"0".equals(nativeState.getProperty(key))) return false;
        return true;
    }

    public static void poll(Minecraft mc) {
        if (LoadingChainProbe.enabled()) {
            LoadingChainProbe.poll(mc);
            return;
        }
        if (CrouchRegressionProbe.enabled()) {
            CrouchRegressionProbe.poll(mc);
            return;
        }
        if (!ENABLED || !ExperimentAccess.permitted() || step == 5) return;
        long now = System.nanoTime();
        try {
            if (deadline == 0) {
                modes =
                        captureModes(
                                System.getProperty("radiance.audit.cityProfilePlan", "standard"));
                deadline = now + 600_000_000_000L;
            }
            if (now > deadline)
                throw new IllegalStateException("City comparison exceeded 600 seconds");
            if (step == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                if (!viewportReady(mc, now)) return;
                step = 1;
                mc.createWorldOpenFlows()
                        .openWorld(
                                System.getProperty("radiance.audit.cityWorld", "Newport City"),
                                () -> mc.setScreen(new TitleScreen()));
                LOG.info("CITY_COMPARISON open-world");
            }
            if (mc.level == null || mc.player == null) return;
            if (step == 1) {
                MaterialStateBatchProbe.verify("world-ready");
                step = 2;
                entered = now;
                mc.setScreen(new ChatScreen(""));
                LOG.info(
                        "CITY_COMPARISON world-ready x={} y={} z={} yaw={} pitch={}",
                        mc.player.getX(),
                        mc.player.getY(),
                        mc.player.getZ(),
                        mc.player.getYRot(),
                        mc.player.getXRot());
                LOG.info(
                        "CITY_COMPARISON source-contract baked={} painting={} parts={}",
                        System.getProperty("radiance.internal.bakedSource", "absent"),
                        System.getProperty("radiance.internal.paintingSource", "absent"),
                        System.getProperty("radiance.internal.partSource", "absent"));
            }
            if (step == 2 && now >= nextPoll) {
                nextPoll = now + 1_000_000_000L;
                var state = new Properties();
                BenchmarkChunkState.capture(state);
                if (Boolean.getBoolean("radiance.audit.vanillaPerformance")
                        && now >= nextProgress) {
                    LOG.info("CITY_COMPARISON waiting-for-quiet state={}", state);
                    nextProgress = now + 15_000_000_000L;
                }
                if (!quiet(state)) quietSince = 0;
                else if (quietSince == 0) quietSince = now;
                if (now - entered >= 60_000_000_000L
                        && quietSince != 0
                        && now - quietSince >= 10_000_000_000L) {
                    if (modes.length == 0) {
                        PaintingModelProbe.checkpoint("before-reload");
                        step = 4;
                        entered = now;
                        LOG.info(
                                "CITY_COMPARISON functional-only; no profiling; requesting resource reload");
                        reload = mc.reloadResourcePacks();
                        return;
                    }
                    String label =
                            System.getProperty("radiance.audit.cityLabel", "city-comparison");
                    if (now < betweenUntil) return;
                    if (Boolean.getBoolean("radiance.audit.cityScreenshot")
                            && !screenshotCaptured) {
                        screenshotCaptured = true;
                        net.minecraft.client.Screenshot.grab(
                                mc.gameDirectory,
                                "city-diagnostic.png",
                                mc.getMainRenderTarget(),
                                message -> {});
                        // Keep the readback and its short settling interval outside timing.
                        betweenUntil = now + 3_000_000_000L;
                        return;
                    }
                    int seconds = Integer.getInteger("radiance.audit.cityProfileSeconds", 60);
                    if (modes[captureIndex] == ProfileMode.TIMED
                            && Boolean.getBoolean("radiance.audit.vanillaPerformance"))
                        seconds = Math.min(seconds, 10);
                    if (seconds < 1 || seconds > 120)
                        throw new IllegalArgumentException("City profile seconds must be 1..120");
                    partWindows.add(partCounters());
                    if (FrameProfiler.request(
                                    seconds,
                                    label + "-" + captureIndex + "-" + modes[captureIndex],
                                    modes[captureIndex])
                            != 1)
                        throw new IllegalStateException("Another profile is already active");
                    step = 3;
                    LOG.info("CITY_COMPARISON capture-request label={} state={}", label, state);
                }
            } else if (step == 3 && !FrameProfiler.requested()) {
                partWindows.add(partCounters());
                if (++captureIndex < modes.length) {
                    step = 2;
                    betweenUntil = now + 5_000_000_000L;
                    quietSince = 0;
                    return;
                }
                if (Boolean.getBoolean("radiance.audit.vanillaPerformance")) {
                    step = 4;
                    entered = now;
                    reload = mc.reloadResourcePacks();
                } else startCensus(now); // Counts-only observation, outside timed windows.
            } else if (step == 6) {
                if (now < betweenUntil) {
                    ProducerCensus.frame();
                    return;
                }
                ProducerCensus
                        .frame(); // poll runs at runTick RETURN: include the final completed frame.
                ProducerCensus.finish();
                step = 4;
                PaintingModelProbe.checkpoint("before-reload");
                LOG.info("CITY_COMPARISON capture-finished; requesting resource reload");
                reload = mc.reloadResourcePacks();
                entered = now;
            } else if (step == 4 && reload.isDone()) {
                reload.join();
                if (now - entered >= 15_000_000_000L) {
                    MaterialStateBatchProbe.verify("reload-complete");
                    PaintingModelProbe.checkpoint("reload-complete");
                    step = 5;
                    LOG.info(
                            "CITY_COMPARISON reload-complete normal-stop font-violations={}",
                            FontSourceProbe.violations());
                    if (FontSourceProbe.violations() != 0)
                        throw new IllegalStateException("Font lifecycle regression");
                    writeSourceObservations();
                    mc.stop();
                }
            }
        } catch (Exception | LinkageError failure) {
            step = 5;
            LOG.error("CITY_COMPARISON FAIL; stop without retry", failure);
            mc.stop();
        }
    }

    private static Object partCounters() {
        try {
            return Class.forName("com.radiance.client.vertex.PartModelCapture")
                    .getMethod("counters")
                    .invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException absentInFrozenBaseline) {
            return "unavailable-in-frozen-product";
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static void writeSourceObservations() throws java.io.IOException {
        Object coverage;
        try {
            coverage =
                    Class.forName("com.radiance.mixin_related.SourceCompatibility")
                            .getMethod("coverage")
                            .invoke(null);
        } catch (ClassNotFoundException absentInFrozenBaseline) {
            coverage = "unavailable-in-frozen-product";
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        var path =
                java.nio.file.Path.of(
                        System.getProperty("validation.result"), "source-observations.json");
        java.nio.file.Files.writeString(
                path,
                new com.google.gson.Gson()
                                .toJson(
                                        java.util.Map.of(
                                                "partWindowEndpoints", partWindows,
                                                "partCounterLayout",
                                                        "registrations,builds,draws,sources,versions,sourceBytes,meshBytes",
                                                "coverage", coverage))
                        + "\n");
    }

    static boolean viewportReady(Minecraft mc, long now) {
        int width = Integer.getInteger("radiance.audit.cityWidth", 0);
        int height = Integer.getInteger("radiance.audit.cityHeight", 0);
        if (width == 0 && height == 0) return true;
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("Both city viewport dimensions must be positive");
        var window = mc.getWindow();
        if (!viewportConfigured) {
            viewportConfigured = true;
            if (Boolean.getBoolean("radiance.audit.cityBorderless"))
                org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(
                        window.getWindow(),
                        org.lwjgl.glfw.GLFW.GLFW_DECORATED,
                        org.lwjgl.glfw.GLFW.GLFW_FALSE);
        }
        if (window.getWidth() == width && window.getHeight() == height) return true;
        if (viewportDeadline == 0) {
            // Initial decorated Windows placement may reduce the requested client height.
            // Resize before opening the world, then verify actual framebuffer pixels.
            viewportDeadline = now + 5_000_000_000L;
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.getWindow(), width, height);
            int[] actualWidth = new int[1], actualHeight = new int[1];
            org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(
                    window.getWindow(), actualWidth, actualHeight);
            LOG.info(
                    "CITY_COMPARISON viewport-request {}x{} GLFW={}x{} borderless={}",
                    width,
                    height,
                    actualWidth[0],
                    actualHeight[0],
                    Boolean.getBoolean("radiance.audit.cityBorderless"));
        } else if (now > viewportDeadline) {
            throw new IllegalStateException(
                    "City framebuffer mismatch: " + window.getWidth() + "x" + window.getHeight());
        }
        return false;
    }
}
