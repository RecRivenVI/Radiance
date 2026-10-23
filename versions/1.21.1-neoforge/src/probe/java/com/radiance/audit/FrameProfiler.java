package com.radiance.audit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Observer only: never changes graphics, focus, frame limit, worlds, or server state. */
public final class FrameProfiler {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final java.lang.management.ThreadMXBean CPU =
            ManagementFactory.getThreadMXBean();
    private static volatile int pendingSeconds;
    private static volatile boolean stop;
    private static volatile boolean active;
    private static boolean nativeReady, autoStarted;
    private static long worldSince, deadline, frame, previousStart, captureFrames;
    private static long nextControlPoll;
    private static String previousControlId = "", pendingLabel = "client-command";
    private static boolean controlFailed;
    private static Thread owner;
    private static FrameTimings current;
    private static volatile ProfileReport report;
    private static ProfileMode mode = ProfileMode.TIMED, pendingMode = ProfileMode.TIMED;
    private static jdk.jfr.Recording recording;
    private static Path recordingOutput;
    private static long beginCost;

    private FrameProfiler() {}

    static boolean requested() {
        return pendingSeconds > 0 || active;
    }

    static long frameToken() {
        return frame;
    }

    static boolean collecting() {
        return active && current != null && Thread.currentThread() == owner;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FrameProfiler::commands);
    }

    private static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher()
                .register(
                        Commands.literal("radianceaudit")
                                .then(
                                        Commands.literal("profile")
                                                .then(
                                                        Commands.literal("start")
                                                                .executes(c -> request(30))
                                                                .then(
                                                                        Commands.argument(
                                                                                        "seconds",
                                                                                        IntegerArgumentType
                                                                                                .integer(
                                                                                                        1,
                                                                                                        120))
                                                                                .executes(
                                                                                        c ->
                                                                                                request(
                                                                                                        IntegerArgumentType
                                                                                                                .getInteger(
                                                                                                                        c,
                                                                                                                        "seconds")))))
                                                .then(
                                                        Commands.literal("stop")
                                                                .executes(
                                                                        c -> {
                                                                            stop = true;
                                                                            return 1;
                                                                        }))));
    }

    private static int request(int seconds) {
        return request(seconds, "client-command");
    }

    static int request(int seconds, String label) {
        return request(seconds, label, ProfileMode.TIMED);
    }

    static int request(int seconds, String label, ProfileMode selected) {
        if (active || pendingSeconds != 0) return 0;
        pendingLabel = label;
        pendingMode = selected;
        pendingSeconds = seconds;
        Minecraft.getInstance()
                .gui
                .getChat()
                .addMessage(
                        Component.literal(
                                "Radiance Audit: profiling " + seconds + " s; scene=" + label));
        return 1;
    }

    public static boolean beginFrame() {
        long observerStart = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        pollControl(mc);
        int auto = Integer.getInteger("radiance.audit.profileSeconds", 0);
        if (auto > 0 && !autoStarted && mc.level != null) {
            if (worldSince == 0) worldSince = System.nanoTime();
            if (System.nanoTime() - worldSince
                    > Long.getLong("radiance.audit.profileDelaySeconds", 10L) * 1_000_000_000L) {
                autoStarted = true;
                pendingSeconds = Math.clamp(auto, 1, 120);
            }
        }
        if (!active && pendingSeconds > 0) {
            mode = pendingMode;
            nativeReady = NativeDiagnostics.attachProfiler();
            if (!nativeReady && mode.nativeFlags != 0)
                throw new IllegalStateException("Matching native profile ABI 2 is required");
            if (nativeReady)
                NativeDiagnostics
                        .drainProfile(); // Previous capture tail cannot contaminate this one.
            Path output =
                    profileDirectory(mc)
                            .resolve(
                                    Instant.now().toString().replace(':', '-')
                                            + "-pid"
                                            + ProcessHandle.current().pid());
            report =
                    new ProfileReport(
                            output,
                            "time="
                                    + Instant.now()
                                    + "\npid="
                                    + ProcessHandle.current().pid()
                                    + "\nnative-profile="
                                    + nativeReady
                                    + "\njava="
                                    + System.getProperty("java.version")
                                    + "\nscene="
                                    + pendingLabel
                                    + "\nprofile-mode="
                                    + mode
                                    + "\nnative-profile-abi=2\nnative-profile-flags="
                                    + mode.nativeFlags
                                    + "\nresource-census="
                                    + (mode == ProfileMode.CONTENT_AUDIT)
                                    + "\ncontent-audit="
                                    + (mode == ProfileMode.CONTENT_AUDIT)
                                    + "\nproducer-census="
                                    + (mode.producers
                                            || Boolean.getBoolean("radiance.audit.producerCensus"))
                                    + "\nallocation-tag-audit="
                                    + Boolean.getBoolean("radiance.audit.nativeAllocations")
                                    + "\nexplicit-native-census-env="
                                    + System.getenv("RADIANCE_CHUNK_CENSUS")
                                    + "\nthread-cpu-clock=ThreadMXBean; nanosecond units do not imply nanosecond resolution. Inspect observed positive quantum in STATUS.txt; never infer per-frame CPU waits from quantized individual samples.\n"
                                    + "FRAME_ONLY retains the same probe, basic telemetry, fault capture and frame writer, but disables nested profile timers, native profile samples and additional profile GPU ranges. Product/base queries remain unchanged. It is an overhead control, not a probe-free product baseline.\n"
                                    + "\nframe-scene-columns="
                                    + SceneProfileState.HEADER
                                    + "\nscene-at-request="
                                    + sceneState(mc).csv()
                                    + "\nScene fields observe Java camera/world state; not GPU uniform readback.\n"
                                    + "\nCPU=self time partitions runTick wall time, including waits; not CPU utilization.\n"
                                    + "Native domain 0=render-owner, 1=worker/no-owner; nested self durations. GPU 2=command buffers, 3=world modules, 4=whole frame; these overlap, NEVER add them to CPU or each other.\n"
                                    + "GPU timestamps are completion intervals on the main queue, exclude separate chunk queues and SDK internal FG work. Command-buffer, module and pt-pass ranges overlap; never sum them. No new idle/wait. Missing/unavailable/partial-readback GPU frames are omitted, not zero.\n"
                                    + "Java per-frame distributions include zeros. Native/GPU quantiles are per invocation, capped at first 20000 samples; raw rows retained.\n"
                                    + "Frame token correlates Java/native submission; clocks are not subtracted across JNI. SDK rates are not display measurements.\n"
                                    + "No per-vertex probes. Entity capture includes model dispatch, CPU vertex generation and material work; marshal is separately nested. Background work overlaps rendering.\n",
                            frame + 1);
            ProducerCensus.start(output, mode.producers);
            InstanceInputCensus.start(output);
            if (mode == ProfileMode.CPU_SAMPLES || mode == ProfileMode.JFR_ONLY) {
                try {
                    recording =
                            new jdk.jfr.Recording(
                                    jdk.jfr.Configuration.getConfiguration("profile"));
                    recording.setName("Radiance CPU attribution");
                    recording.setMaxSize(32L * 1024 * 1024);
                    recording
                            .enable("jdk.ExecutionSample")
                            .withPeriod(java.time.Duration.ofMillis(10));
                    recording
                            .enable("jdk.NativeMethodSample")
                            .withPeriod(java.time.Duration.ofMillis(10));
                    recording
                            .enable("jdk.ThreadCPULoad")
                            .withPeriod(java.time.Duration.ofSeconds(1));
                    recordingOutput = output.resolve("cpu-samples.jfr");
                    recording.start();
                } catch (java.io.IOException | java.text.ParseException error) {
                    throw new IllegalStateException(
                            "Cannot start explicitly requested CPU samples", error);
                }
            }
            Thread writer = new Thread(report, "Radiance Audit profile writer");
            writer.setDaemon(true);
            writer.start();
            deadline = System.nanoTime() + pendingSeconds * 1_000_000_000L;
            pendingSeconds = 0;
            stop = false;
            captureFrames = 0;
            previousStart = 0;
            active = true;
            owner = Thread.currentThread();
            LOG.info("[Radiance Audit/profile] Started: native={} output={}", nativeReady, output);
        }
        if (!active) return false;
        if (stop || System.nanoTime() >= deadline || captureFrames >= 20000) {
            finish();
            return false;
        }
        ++frame;
        ++captureFrames;
        ProducerCensus.frame();
        if (nativeReady) NativeDiagnostics.profileFrame(frame, mode.nativeFlags);
        current = new FrameTimings(System::nanoTime);
        beginCost = System.nanoTime() - observerStart;
        return true;
    }

    private static Path profileDirectory(Minecraft mc) {
        String result = System.getProperty("validation.result", "");
        if (Boolean.getBoolean("radiance.audit.profileControl") && !result.isBlank())
            return Path.of(result).toAbsolutePath().resolve("profiles");
        return mc.gameDirectory.toPath().toAbsolutePath().resolve("radiance-audit/profiles");
    }

    private static SceneProfileState sceneState(Minecraft mc) {
        if (mc.level == null) return SceneProfileState.absent();
        var camera = mc.gameRenderer.getMainCamera();
        var position = camera.getPosition();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        return new SceneProfileState(
                mc.level.dimension().location().toString(),
                position.x,
                position.y,
                position.z,
                camera.getYRot(),
                camera.getXRot(),
                mc.player == null ? Double.NaN : mc.player.getEyePosition(partial).y,
                mc.level.getLevelData().getHorizonHeight(mc.level),
                mc.level.getMinBuildHeight(),
                mc.level.getMaxBuildHeight(),
                mc.level.getGameTime(),
                mc.level.getDayTime(),
                mc.level.getRainLevel(partial),
                mc.level.getThunderLevel(partial));
    }

    private static void pollControl(Minecraft mc) {
        if (!Boolean.getBoolean("radiance.audit.profileControl")
                || active
                || pendingSeconds != 0
                || mc.level == null) return;
        String result = System.getProperty("validation.result", "");
        if (result.isBlank()) return;
        long now = System.nanoTime();
        if (now < nextControlPoll) return;
        nextControlPoll = now + 1_000_000_000L;
        try {
            var capture =
                    ProfileCaptureControl.read(
                            Path.of(result).resolve("profile-request.properties"),
                            previousControlId,
                            ManagementFactory.getRuntimeMXBean().getStartTime());
            if (capture != null) {
                previousControlId = capture.id();
                request(capture.seconds(), capture.label());
                LOG.info("[Radiance Audit/profile] Local request {}", capture);
            }
            controlFailed = false;
        } catch (java.io.IOException invalid) {
            if (!controlFailed)
                LOG.warn("Invalid local profile request; rendering unchanged", invalid);
            controlFailed = true;
        }
    }

    public static FrameTimings.Span span(FrameTimings.Stage stage) {
        return active && mode.captures(stage) && Thread.currentThread() == owner && current != null
                ? current.enter(stage)
                : null;
    }

    public static void generatedPbr(com.mojang.blaze3d.vertex.MeshData mesh) {
        if (!active
                || mode != ProfileMode.TIMED
                || current == null
                || Thread.currentThread() != owner
                || mesh == null) return;
        var draw = mesh.drawState();
        current.generatedPbrVertices += draw.vertexCount();
        current.generatedPbrBytes += (long) draw.vertexCount() * draw.format().getVertexSize();
    }

    public static WorkerSample workerStart() {
        return active ? new WorkerSample(report, System.nanoTime()) : null;
    }

    public static final class WorkerSample implements AutoCloseable {
        private final ProfileReport sink;
        private final long start;

        private WorkerSample(ProfileReport sink, long start) {
            this.sink = sink;
            this.start = start;
        }

        @Override
        public void close() {
            if (active && sink != null && sink == report) {
                long elapsed = System.nanoTime() - start;
                sink.offer("", "0,java-worker,CHUNK_COMPILE," + elapsed + "," + elapsed + "\n");
            }
        }
    }

    public static long cpuTime() {
        return CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled()
                ? CPU.getCurrentThreadCpuTime()
                : -1;
    }

    public static void endFrame(long start, long cpuStart, boolean failed) {
        long end = System.nanoTime(), cpuEnd = cpuTime();
        var timings = current;
        current = null;
        if (timings == null || report == null) return;
        if (nativeReady) NativeDiagnostics.profileFrame(0, mode.nativeFlags);
        StringBuilder rows = new StringBuilder();
        for (var stage : FrameTimings.Stage.values()) {
            if (!mode.captures(stage)) continue;
            rows.append(frame)
                    .append(",java,")
                    .append(stage)
                    .append(',')
                    .append(timings.inclusive[stage.ordinal()])
                    .append(',')
                    .append(timings.self[stage.ordinal()])
                    .append('\n');
        }
        if (nativeReady) {
            String values = NativeDiagnostics.drainProfile();
            if (values != null) rows.append(values);
        }
        if (mode == ProfileMode.TIMED)
            rows.append(frame)
                    .append(",5,java.pbr.generated,")
                    .append(timings.generatedPbrVertices)
                    .append(',')
                    .append(timings.generatedPbrBytes)
                    .append('\n');
        var mc = Minecraft.getInstance();
        long rates = failed ? 0 : RendererProxy.presentationRates();
        String frameRow =
                String.format(
                        Locale.ROOT,
                        "%d,%d,%d,%d,%s,%s,%s,%d,%d,%d,%d,%d,%s,%s%n",
                        frame,
                        end - start,
                        cpuStart < 0 || cpuEnd < 0 ? -1 : cpuEnd - cpuStart,
                        previousStart == 0 ? -1 : start - previousStart,
                        mc.level != null,
                        mc.isWindowActive(),
                        mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
                        mc.getWindow().getWidth(),
                        mc.getWindow().getHeight(),
                        mc.options.renderDistance().get(),
                        rates >>> 32,
                        rates & 0xffffffffL,
                        failed,
                        sceneState(mc).csv());
        previousStart = start;
        rows.append(frame)
                .append(",observer,begin-frame,")
                .append(beginCost)
                .append(',')
                .append(beginCost)
                .append('\n');
        long endCost = System.nanoTime() - end;
        rows.append(frame)
                .append(",observer,end-frame-before-offer,")
                .append(endCost)
                .append(',')
                .append(endCost)
                .append('\n');
        report.offer(frameRow, rows.toString());
        if (failed) finish();
    }

    public static void finish() {
        InstanceInputCensus.finish();
        if (!active) return;
        active = false;
        current = null;
        ProducerCensus.finish();
        if (nativeReady) {
            NativeDiagnostics.profileFrame(0, 0);
            String tail = NativeDiagnostics.drainProfile();
            if (tail != null) report.offer("", tail);
        }
        report.finish(captureFrames);
        report = null;
        if (recording != null) {
            try (var completed = recording) {
                completed.stop();
                java.nio.file.Files.createDirectories(recordingOutput.getParent());
                completed.dump(recordingOutput);
            } catch (java.io.IOException failure) {
                LOG.error("CPU sample recording failed; not a valid JFR capture", failure);
            } finally {
                recording = null;
            }
        }
        LOG.info(
                "[Radiance Audit/profile] Capture stopped after {} frames; in-flight GPU tail may be absent",
                captureFrames);
    }
}
