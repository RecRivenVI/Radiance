package com.radiance.audit;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/** All disk IO and aggregation live on an optional daemon. Backpressure drops reports, never frames. */
final class ProfileReport implements Runnable {
    record Row(String frames, String timings) {}
    final Path directory;
    private final ArrayBlockingQueue<Row> queue = new ArrayBlockingQueue<>(256);
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean finished;
    private volatile boolean failed;
    private final String metadata;
    private final long firstFrame;
    ProfileReport(Path directory, String metadata) { this(directory, metadata, 0); }
    ProfileReport(Path directory, String metadata, long firstFrame) {
        this.directory = directory; this.metadata = metadata; this.firstFrame = firstFrame;
    }
    void offer(String frame, String timings) {
        if (!failed && !queue.offer(new Row(frame, timings))) dropped.incrementAndGet();
    }
    void finish() { finished = true; }
    private static final class Distribution {
        long sum; long count; final ArrayList<Long> samples = new ArrayList<>();
        void add(long value) { sum += value; count++; if (samples.size() < 20000) samples.add(value); }
        long percentile(double p) {
            if (samples.isEmpty()) return 0;
            samples.sort(Long::compare);
            return samples.get((int)Math.ceil(p * samples.size()) - 1);
        }
    }
    @Override public void run() {
        var totals = new TreeMap<String, Distribution>();
        long rootNs = 0, nativeDropped = 0, writtenChars = 0, outputCapDrops = 0;
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("METADATA.txt"), metadata);
            try (var frames = Files.newBufferedWriter(directory.resolve("frames.csv"));
                 var timings = Files.newBufferedWriter(directory.resolve("timings.csv"));
                 var counters = Files.newBufferedWriter(directory.resolve("counters.csv"))) {
                frames.write("frame,wall_ns,render_thread_cpu_ns,start_interval_ns,world,focused,screen,width,height,render_distance,sdk_rendered_fps,sdk_generated_fps,failed\n");
                timings.write("frame,domain,stage,inclusive_ns,self_ns\n");
                counters.write("frame,stage,count,bytes\n");
                while (!finished || !queue.isEmpty()) {
                    Row row = queue.poll(250, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (row == null) continue;
                    // All generated CSV fields are ASCII. Keep raw evidence bounded independently of FPS.
                    long rowChars=(long)row.frames().length()+row.timings().length();
                    if(writtenChars+rowChars > 128L*1024*1024) { outputCapDrops++;continue; }
                    writtenChars+=rowChars;
                    frames.write(row.frames());
                    if (!row.frames().isBlank()) {
                        var frameFields = row.frames().strip().split(",");
                        for (int index=1;index<=3;index++) {
                            long value=Long.parseLong(frameFields[index]);
                            if (value>=0) totals.computeIfAbsent("frame,"+new String[]{"","wall","thread-cpu","start-interval"}[index]+",inclusive", k->new Distribution()).add(value);
                        }
                    }
                    for (String line : row.timings().split("\n")) {
                        if (line.isBlank()) continue;
                        String[] v = line.split(",", 5);
                        if (v.length != 5) continue;
                        long owner = Long.parseLong(v[0]);
                        if (owner != 0 && owner < firstFrame) continue; // Late GPU completion from an older capture.
                        if (v[1].equals("5")) {
                            counters.write(v[0]+","+v[2]+","+v[3]+","+v[4]); counters.newLine();
                            continue; // Work units are never converted to milliseconds or frame-time percentages.
                        }
                        timings.write(line); timings.newLine();
                        long inclusive = Long.parseLong(v[3]), self = Long.parseLong(v[4]);
                        if (v[1].equals("9")) { nativeDropped += inclusive; continue; }
                        if (v[1].equals("java") && v[2].equals("FRAME_OTHER")) rootNs += inclusive;
                        // Native and GPU distributions are per invocation; Java rows are per frame (including zeros).
                        totals.computeIfAbsent(v[1]+","+v[2]+",inclusive", k -> new Distribution()).add(inclusive);
                        totals.computeIfAbsent(v[1]+","+v[2]+",self", k -> new Distribution()).add(self);
                    }
                }
            }
            try (var out = Files.newBufferedWriter(directory.resolve("summary.csv"))) {
                out.write("domain,stage,measure,samples,total_ms,mean_ms,p50_ms,p95_ms,p99_ms,percent_render_thread_wall\n");
                for (var entry : totals.entrySet()) {
                    var d = entry.getValue();
                    String share = entry.getKey().startsWith("java,") && entry.getKey().endsWith(",self") && rootNs != 0
                        ? String.format(Locale.ROOT, "%.3f", d.sum * 100.0 / rootNs) : "";
                    out.write(String.format(Locale.ROOT,"%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%s%n", entry.getKey(), d.count,
                        d.sum/1e6, d.sum/1e6/Math.max(1,d.count), d.percentile(.50)/1e6,
                        d.percentile(.95)/1e6, d.percentile(.99)/1e6, share));
                }
            }
            Files.writeString(directory.resolve("STATUS.txt"), "finished=true\ndropped-writer-batches="+dropped.get()
                +"\ndropped-native-events="+nativeDropped+"\noutput-cap-dropped-batches="+outputCapDrops+"\nraw-output-cap=128MiB\nquantile-sample-cap-per-metric=20000\n");
            writeReadable(totals, rootNs, nativeDropped);
            com.mojang.logging.LogUtils.getLogger().info("[Radiance Audit/profile] Saved {} (writerDrops={}, nativeDrops={})", directory, dropped, nativeDropped);
        } catch (Exception failure) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().error("[Radiance Audit/profile] Report writer failed: {}", directory, failure);
        }
    }
    private void writeReadable(Map<String,Distribution> totals,long wall,long nativeDrops) throws IOException {
        long frames=totals.getOrDefault("java,FRAME_OTHER,inclusive",new Distribution()).count;
        StringBuilder out=new StringBuilder("# Frame profile\n\n");
        out.append("Recorded frames: ").append(frames).append(". Writer drops: ").append(dropped.get())
            .append(". Native event drops: ").append(nativeDrops).append(".\n\n");
        out.append("CPU tables measure wall duration, including waits. GPU overlaps CPU. Native rows drill into the Java rows; never add these three tables together. Capture start/stop and optional timestamps add overhead.\n\n");
        out.append("| Frame metric | Mean ms | p50 ms | p95 ms | p99 ms |\n|---|---:|---:|---:|---:|\n");
        for(String name:List.of("wall","thread-cpu","start-interval")) {
            var d=totals.get("frame,"+name+",inclusive");if(d==null)continue;
            out.append(String.format(Locale.ROOT,"| %s | %.3f | %.3f | %.3f | %.3f |%n",name,d.sum/1e6/d.count,d.percentile(.5)/1e6,d.percentile(.95)/1e6,d.percentile(.99)/1e6));
        }
        for(String domain:List.of("java","0","1","java-worker","2","3","4")) {
            String title=switch(domain) {
                case "java" -> "Render-thread exclusive partition (sum = 100%)";
                case "0" -> "Native CPU exclusive drill-down (already inside Java times)";
                case "1", "java-worker" -> "Background / no frame owner: "+domain+" (overlaps frames)";
                case "2" -> "GPU command-buffer completion intervals";
                case "3" -> "GPU world-module completion intervals (inside world-buffer)";
                default -> "GPU whole main-queue frame (not SDK-generated frames)";
            };
            out.append("\n## ").append(title).append("\n\n| Stage | Total ms | Mean ms / sample | Ms / recorded render frame | Frame-wall share |\n|---|---:|---:|---:|---:|\n");
            totals.entrySet().stream().filter(e->e.getKey().startsWith(domain+",") && e.getKey().endsWith(","+(domain.equals("4") ? "inclusive" : "self")))
                .sorted((a,b)->Long.compare(b.getValue().sum,a.getValue().sum)).forEach(e->{
                    var d=e.getValue();if(d.sum==0)return;
                    out.append(String.format(Locale.ROOT,"| %s | %.3f | %.3f | %s | %s |%n",e.getKey().split(",")[1],d.sum/1e6,d.sum/1e6/d.count,
                        (domain.equals("java") || domain.equals("0")) && frames>0 ? String.format(Locale.ROOT,"%.3f",d.sum/1e6/frames) : "n/a",
                        domain.equals("java") && wall>0 ? String.format(Locale.ROOT,"%.2f%%",d.sum*100.0/wall) : "n/a"));
                });
        }
        out.append("\nNative/GPU sample means are per invocation, not per rendered frame. The extra CPU per-frame column divides native render-owner totals by all recorded Java frames; worker and GPU rows deliberately omit it. Use frame tokens in timings.csv for per-frame joins. Missing GPU tail/unsupported timestamp/partial readback frames are absent, not zero. Domain 8 rows report omissions.\n");
        Files.writeString(directory.resolve("REPORT.md"),out.toString());
    }
}
