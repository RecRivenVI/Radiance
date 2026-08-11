package com.radiance.audit.tools;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Bounded export: filter sampledThread as well as eventThread before expanding stack metadata. */
public final class RenderJfrExport {
    private static final Set<String> EVENTS =
            Set.of(
                    "jdk.ExecutionSample",
                    "jdk.NativeMethodSample",
                    "jdk.JavaMonitorEnter",
                    "jdk.ThreadPark",
                    "jdk.GCPhasePause");

    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]);
        long renderId = renderThread(input);
        try (var recording = new RecordingFile(Path.of(args[0]));
                var out = new PrintWriter(System.out, false, StandardCharsets.UTF_8)) {
            out.print("{\"renderThreadId\":" + renderId + ",\"recording\":{\"events\":[");
            boolean first = true;
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String name = event.getEventType().getName();
                if (!EVENTS.contains(name)) continue;
                RecordedThread thread =
                        event.hasField("sampledThread")
                                ? event.getThread("sampledThread")
                                : event.getThread();
                if (!name.equals("jdk.GCPhasePause")
                        && (thread == null || renderId != thread.getJavaThreadId())) continue;
                if (!first) out.print(',');
                first = false;
                out.print(
                        "{\"type\":"
                                + quote(name)
                                + ",\"values\":{\"startTime\":"
                                + quote(event.getStartTime().toString())
                                + ",\"duration\":"
                                + event.getDuration().toNanos() / 1e9);
                if (thread != null)
                    out.print(
                            ",\"eventThread\":{\"javaName\":"
                                    + quote(thread.getJavaName())
                                    + ",\"javaThreadId\":"
                                    + thread.getJavaThreadId()
                                    + "}");
                var stack = event.getStackTrace();
                if (stack != null) {
                    out.print(
                            ",\"stackTrace\":{\"truncated\":"
                                    + stack.isTruncated()
                                    + ",\"frames\":[");
                    boolean firstFrame = true;
                    for (var frame : stack.getFrames()) {
                        if (!firstFrame) out.print(',');
                        firstFrame = false;
                        var method = frame.getMethod();
                        out.print(
                                "{\"method\":{\"name\":"
                                        + quote(method.getName())
                                        + ",\"type\":{\"name\":"
                                        + quote(method.getType().getName())
                                        + "}}}");
                    }
                    out.print("]}");
                }
                out.print("}}");
            }
            out.print("]}}");
            if (out.checkError()) throw new IllegalStateException("JFR export output failed");
        }
    }

    private static long renderThread(Path input) throws Exception {
        Map<Long, Integer> samples = new HashMap<>();
        try (var recording = new RecordingFile(input)) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                if (!event.getEventType().getName().endsWith("Sample")
                        || !event.hasField("sampledThread")
                        || event.getStackTrace() == null) continue;
                boolean frame =
                        event.getStackTrace().getFrames().stream()
                                .anyMatch(
                                        f ->
                                                f.getMethod()
                                                                .getType()
                                                                .getName()
                                                                .equals(
                                                                        "net.minecraft.client.Minecraft")
                                                        && f.getMethod()
                                                                .getName()
                                                                .equals("runTick"));
                if (frame)
                    samples.merge(
                            event.getThread("sampledThread").getJavaThreadId(), 1, Integer::sum);
            }
        }
        return samples.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Cannot identify render thread from Minecraft.runTick stacks"))
                .getKey();
    }

    private static String quote(String value) {
        if (value == null) return "null";
        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                + "\"";
    }
}
