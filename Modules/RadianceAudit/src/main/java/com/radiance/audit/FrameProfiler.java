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
    private static final java.lang.management.ThreadMXBean CPU = ManagementFactory.getThreadMXBean();
    private static volatile int pendingSeconds;
    private static volatile boolean stop;
    private static volatile boolean active;
    private static boolean nativeReady, autoStarted;
    private static long worldSince, deadline, frame, previousStart, captureFrames;
    private static Thread owner;
    private static FrameTimings current;
    private static volatile ProfileReport report;
    private FrameProfiler() {}
    static boolean requested() { return pendingSeconds > 0 || active; }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(FrameProfiler::commands);
    }
    private static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("radianceaudit").then(Commands.literal("profile")
            .then(Commands.literal("start").executes(c -> request(30))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1,120))
                    .executes(c -> request(IntegerArgumentType.getInteger(c,"seconds")))))
            .then(Commands.literal("stop").executes(c -> { stop = true; return 1; }))));
    }
    private static int request(int seconds) {
        if (active || pendingSeconds != 0) return 0;
        pendingSeconds = seconds;
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Radiance Audit: profiling " + seconds + " s; results: radiance-audit/profiles"));
        return 1;
    }
    public static boolean beginFrame() {
        Minecraft mc = Minecraft.getInstance();
        int auto = Integer.getInteger("radiance.audit.profileSeconds",0);
        if (auto > 0 && !autoStarted && mc.level != null) {
            if (worldSince == 0) worldSince = System.nanoTime();
            if (System.nanoTime()-worldSince > Long.getLong("radiance.audit.profileDelaySeconds",10L)*1_000_000_000L) {
                autoStarted = true; pendingSeconds = Math.clamp(auto,1,120);
            }
        }
        if (!active && pendingSeconds > 0) {
            nativeReady = NativeDiagnostics.attachProfiler();
            if (nativeReady) NativeDiagnostics.drainProfile(); // Previous capture tail cannot contaminate this one.
            Path output = mc.gameDirectory.toPath().toAbsolutePath().resolve("radiance-audit/profiles")
                .resolve(Instant.now().toString().replace(':','-')+"-pid"+ProcessHandle.current().pid());
            report = new ProfileReport(output, "time="+Instant.now()+"\npid="+ProcessHandle.current().pid()
                +"\nnative-profile="+nativeReady+"\njava="+System.getProperty("java.version")
                +"\nCPU=self time partitions runTick wall time, including waits; not CPU utilization.\n"
                +"Native domain 0=render-owner, 1=worker/no-owner; nested self durations. GPU 2=command buffers, 3=world modules, 4=whole frame; these overlap, NEVER add them to CPU or each other.\n"
                +"GPU timestamps are completion intervals on the main queue, exclude separate chunk queues and SDK internal FG work. No new idle/wait. Missing/unavailable/partial-readback GPU frames are omitted, not zero.\n"
                +"Java per-frame distributions include zeros. Native/GPU quantiles are per invocation, capped at first 20000 samples; raw rows retained.\n"
                +"Frame token correlates Java/native submission; clocks are not subtracted across JNI. SDK rates are not display measurements.\n"
                +"No per-vertex probes. Entity capture includes model dispatch, CPU vertex generation and material work; marshal is separately nested. Background work overlaps rendering.\n", frame+1);
            ProducerCensus.start(output);
            Thread writer = new Thread(report,"Radiance Audit profile writer"); writer.setDaemon(true); writer.start();
            deadline = System.nanoTime()+pendingSeconds*1_000_000_000L;
            pendingSeconds = 0; stop = false; captureFrames = 0; previousStart = 0;
            active = true; owner = Thread.currentThread();
            LOG.info("[Radiance Audit/profile] Started: native={} output={}",nativeReady,output);
        }
        if (!active) return false;
        if (stop || System.nanoTime() >= deadline || captureFrames >= 20000) { finish(); return false; }
        ++frame; ++captureFrames;
        ProducerCensus.frame();
        if (nativeReady) NativeDiagnostics.profileFrame(frame,true);
        current = new FrameTimings(System::nanoTime);
        return true;
    }
    public static FrameTimings.Span span(FrameTimings.Stage stage) {
        return active && Thread.currentThread() == owner && current != null ? current.enter(stage) : null;
    }
    public static WorkerSample workerStart() { return active ? new WorkerSample(report,System.nanoTime()) : null; }
    public static final class WorkerSample implements AutoCloseable {
        private final ProfileReport sink;
        private final long start;
        private WorkerSample(ProfileReport sink,long start) { this.sink=sink;this.start=start; }
        @Override public void close() {
            if (active && sink != null && sink == report) {
                long elapsed=System.nanoTime()-start;
                sink.offer("", "0,java-worker,CHUNK_COMPILE,"+elapsed+","+elapsed+"\n");
            }
        }
    }
    public static long cpuTime() { return CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled() ? CPU.getCurrentThreadCpuTime() : -1; }
    public static void endFrame(long start, long cpuStart, boolean failed) {
        long end = System.nanoTime(), cpuEnd = cpuTime();
        var timings = current; current = null;
        if (timings == null || report == null) return;
        if (nativeReady) NativeDiagnostics.profileFrame(0,true);
        StringBuilder rows = new StringBuilder();
        for (var stage : FrameTimings.Stage.values()) rows.append(frame).append(",java,").append(stage)
            .append(',').append(timings.inclusive[stage.ordinal()]).append(',').append(timings.self[stage.ordinal()]).append('\n');
        if (nativeReady) { String values=NativeDiagnostics.drainProfile(); if(values!=null) rows.append(values); }
        var mc=Minecraft.getInstance(); long rates=failed ? 0 : RendererProxy.presentationRates();
        String frameRow=String.format(Locale.ROOT,"%d,%d,%d,%d,%s,%s,%s,%d,%d,%d,%d,%d,%s%n",frame,end-start,
            cpuStart < 0 || cpuEnd < 0 ? -1 : cpuEnd-cpuStart,previousStart==0 ? -1 : start-previousStart,
            mc.level!=null,mc.isWindowActive(),mc.screen==null ? "none" : mc.screen.getClass().getSimpleName(),
            mc.getWindow().getWidth(),mc.getWindow().getHeight(),mc.options.renderDistance().get(),rates>>>32,rates&0xffffffffL,failed);
        previousStart=start; report.offer(frameRow,rows.toString());
        if(failed) finish();
    }
    public static void finish() {
        if (!active) return;
        active=false; current=null;
        ProducerCensus.finish();
        if(nativeReady) { NativeDiagnostics.profileFrame(0,false); String tail=NativeDiagnostics.drainProfile(); if(tail!=null)report.offer("",tail); }
        report.finish(); report=null;
        LOG.info("[Radiance Audit/profile] Capture stopped after {} frames; in-flight GPU tail may be absent",captureFrames);
    }
}
