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
                    "jdk.GCPhasePause",
                    "radiance.RenderFrame",
                    "jdk.ClassLoad",
                    "jdk.ClassDefine",
                    "jdk.Compilation",
                    "jdk.SafepointBegin",
                    "jdk.SafepointStateSynchronization",
                    "jdk.ExecuteVMOperation");
    private static final Set<String> GLOBAL =
            Set.of(
                    "jdk.GCPhasePause",
                    "jdk.Compilation",
                    "jdk.SafepointBegin",
                    "jdk.SafepointStateSynchronization",
                    "jdk.ExecuteVMOperation");

    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]);
        var extraThreads = java.util.regex.Pattern.compile(args.length > 1 ? args[1] : "(?!)");
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
                if (!GLOBAL.contains(name)
                        && (thread == null
                                || (renderId != thread.getJavaThreadId()
                                        && !extraThreads
                                                .matcher(String.valueOf(thread.getJavaName()))
                                                .find()))) continue;
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
                            ",\""
                                    + (event.hasField("sampledThread")
                                            ? "sampledThread"
                                            : "eventThread")
                                    + "\":{\"javaName\":"
                                    + quote(thread.getJavaName())
                                    + ",\"javaThreadId\":"
                                    + thread.getJavaThreadId()
                                    + ",\"osThreadId\":"
                                    + thread.getOSThreadId()
                                    + "}");
                if (name.equals("radiance.RenderFrame"))
                    out.print(
                            ",\"frameToken\":"
                                    + event.getLong("frameToken")
                                    + ",\"measuredWallNs\":"
                                    + event.getLong("measuredWallNs")
                                    + ",\"renderThreadCpuNs\":"
                                    + event.getLong("renderThreadCpuNs"));
                for (String field : Set.of("loadedClass", "definedClass"))
                    if (event.hasField(field))
                        out.print(
                                ",\""
                                        + field
                                        + "\":{\"name\":"
                                        + quote(event.getClass(field).getName())
                                        + "}");
                if (name.equals("jdk.Compilation")) {
                    jdk.jfr.consumer.RecordedMethod method = event.getValue("method");
                    if (method != null)
                        out.print(
                                ",\"method\":{\"name\":"
                                        + quote(method.getType().getName() + "." + method.getName())
                                        + "}");
                }
                if (event.hasField("operation"))
                    out.print(",\"operation\":" + quote(event.getString("operation")));
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
                if (event.getEventType().getName().equals("radiance.RenderFrame")
                        && event.getThread() != null) return event.getThread().getJavaThreadId();
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
