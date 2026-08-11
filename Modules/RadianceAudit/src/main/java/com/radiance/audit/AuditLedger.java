package com.radiance.audit;

import com.mojang.logging.LogUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

final class AuditLedger {
    static final AuditLedger INSTANCE = new AuditLedger();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLUSH_INTERVAL = 64;
    private static final long FRAME_SAMPLE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final StackWalker WALKER = StackWalker.getInstance(
        StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final DateTimeFormatter SESSION_TIME =
        DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, Intent> open = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<Long>> observed = ThreadLocal.withInitial(ArrayDeque::new);
    private final Object outputLock = new Object();
    private volatile boolean radianceAttached;
    private BufferedWriter output;
    private Path outputPath;
    private int eventsSinceFlush;
    private boolean outputFailed;
    private volatile boolean closed;
    private final AuditBudget budget;
    private final Path directoryOverride;
    private long nextFrameSampleNanos;

    private AuditLedger() { this(null, new AuditBudget(8192, 64L * 1024 * 1024)); }
    AuditLedger(Path directory, AuditBudget budget) {
        this.directoryOverride = directory;
        this.budget = budget;
    }
    int openCount() { return open.size(); }

    void initialize(boolean hasRadiance) {
        radianceAttached = hasRadiance;
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "Radiance audit shutdown"));
        write("session", 0, 0, "START", "", "radiance=" + hasRadiance, false, producer());
        LOGGER.info("Radiance audit ledger started (Radiance attached: {})", hasRadiance);
    }

    long begin(String category, String source, String detail, boolean bindToThread) {
        if (closed || !budget.acquireIntent()) return 0L;
        long id = ids.incrementAndGet();
        String producer = producer();
        Long parent = observed.get().peek();
        long parentId = parent == null ? 0L : parent;
        Intent intent = new Intent(id, parentId, category, source, producer, System.nanoTime());
        open.put(id, intent);
        if (bindToThread) observed.get().push(id);
        write(category, id, parentId, "ISSUED", source, detail, false, producer);
        return id;
    }

    long currentIntentId() {
        Long id = observed.get().peek();
        return id == null ? 0L : id;
    }

    void transition(long id, String state, String destination, String detail, boolean terminal) {
        if (id == 0L) {
            write("orphan", 0, 0, state, destination, detail, terminal, producer());
            return;
        }
        Intent intent = open.get(id);
        String category = intent == null ? "unknown" : intent.category();
        String source = intent == null ? "unknown" : intent.source();
        String origin = intent == null ? producer() : intent.producer();
        long parentId = intent == null ? 0L : intent.parentId();
        write(category, id, parentId, state, destination, source + ": " + detail, terminal,
            origin);
        if (terminal) {
            if (open.remove(id) != null) budget.releaseIntent();
            observed.get().removeFirstOccurrence(id);
            Intent parent = open.get(parentId);
            if (parent != null) parent.recordChildTerminal(state, destination);
        } else if (intent != null && isRouteState(state, destination)) {
            intent.recordDirectRoute(destination);
        }
    }

    private static boolean isRouteState(String state, String destination) {
        if (destination == null || destination.isBlank()) return false;
        return "TRANSLATED".equals(state) || "REPLACED".equals(state)
            || "VANILLA_RETAINED".equals(state);
    }

    void exitObserved(String source) {
        Deque<Long> stack = observed.get();
        Long id = stack.peek();
        if (id == null) return;
        Intent intent = open.get(id);
        if (intent == null || !intent.source().equals(source)) return;
        stack.pop();
        if (!radianceAttached) {
            transition(id, "VANILLA_RETAINED", "VANILLA", "call returned normally", true);
        } else if (intent.routedCount() > 0) {
            transition(id, "ROUTE_OBSERVED", intent.destinations(),
                "call returned; directRoutes=" + intent.directRouteCount()
                    + "; routedChildren=" + intent.routedChildCount()
                    + "; terminalChildren=" + intent.terminalChildCount(), true);
        } else {
            transition(id, "RETURNED_NO_ROUTE", "",
                "call returned; no route observed (not proof of a dropped draw); terminalChildren=" + intent.terminalChildCount(), true);
        }
    }

    void frameBoundary(long frameToken, String state, String detail) {
        long now = System.nanoTime();
        if (now < nextFrameSampleNanos) return;
        nextFrameSampleNanos = now + FRAME_SAMPLE_INTERVAL_NANOS;
        long unknown = open.size();
        write("frame", frameToken, 0, state, "", detail + "; open=" + unknown,
            false, producer());
    }

    void counterSnapshot(long generation, String detail) {
        write("counter", generation, 0, "SNAPSHOT", "CHUNK_BUILD", detail,
            false, "RadianceAudit");
    }

    private String producer() {
        return WALKER.walk(frames -> frames
            .filter(frame -> !frame.getClassName().startsWith("com.radiance.audit"))
            .filter(frame -> !frame.getClassName().startsWith("com.radiance.api.audit"))
            .findFirst()
            .map(frame -> frame.getClassName() + '#' + frame.getMethodName() + ':'
                + frame.getLineNumber())
            .orElse("unknown"));
    }

    private void write(String category, long id, long parentId, String state, String destination,
        String detail, boolean terminal, String producer) {
        synchronized (outputLock) {
            if (outputFailed || closed) return;
            try {
                if (output == null) {
                    Path directory = directoryOverride != null ? directoryOverride : Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("radiance-audit")
                        .resolve(SESSION_TIME.format(Instant.now()) + "-" + ProcessHandle.current().pid());
                    Files.createDirectories(directory);
                    outputPath = directory.resolve("ledger.jsonl");
                    output = Files.newBufferedWriter(outputPath,
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                }
                writeBounded("{\"timeNs\":" + System.nanoTime()
                    + ",\"thread\":\"" + escape(Thread.currentThread().getName())
                    + "\",\"intentId\":" + id
                    + ",\"parentIntentId\":" + parentId
                    + ",\"category\":\"" + escape(category)
                    + "\",\"state\":\"" + escape(state)
                    + "\",\"destination\":\"" + escape(destination)
                    + "\",\"terminal\":" + terminal
                    + ",\"producer\":\"" + escape(producer)
                    + "\",\"detail\":\"" + escape(detail) + "\"}\n");
                eventsSinceFlush++;
                if (eventsSinceFlush >= FLUSH_INTERVAL || "frame".equals(category)
                    || "session".equals(category)) {
                    output.flush();
                    eventsSinceFlush = 0;
                }
            } catch (IOException exception) {
                outputFailed = true;
                LOGGER.error("Radiance audit ledger disabled after an I/O failure", exception);
            }
        }
    }

    private void writeBounded(String line) throws IOException {
        if (!budget.reserveBytes(line.getBytes(StandardCharsets.UTF_8).length)) {
            if (!outputFailed) LOGGER.warn("Audit output limit reached; evidence is incomplete (dropped intents: {})", budget.droppedIntents());
            outputFailed = true;
            return;
        }
        output.write(line);
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.length() > 4096) value = value.substring(0, 4096) + "[truncated]";
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 32) escaped.append(String.format("\\u%04x", (int)c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    void close() {
        synchronized (outputLock) {
            if (closed) return;
            closed = true;
            if (output == null) return;
            int unknownCount = open.size();
            try {
                for (Intent intent : open.values()) {
                    writeBounded("{\"intentId\":" + intent.id()
                        + ",\"category\":\"" + escape(intent.category())
                        + "\",\"state\":\"UNKNOWN\",\"terminal\":true"
                        + ",\"producer\":\"" + escape(intent.producer())
                        + "\",\"detail\":\"process ended without a terminal disposition\"}\n");
                }
                output.flush();
                output.close();
                LOGGER.info("Radiance audit ledger closed: {} unknown intent(s), dropped={}, outputIncomplete={}, file={}",
                    unknownCount, budget.droppedIntents(), outputFailed, outputPath);
            } catch (IOException ignored) {
                LOGGER.warn("Radiance audit ledger could not be closed cleanly: {}", outputPath);
            } finally {
                output = null;
                open.clear();
            }
        }
    }

    private static final class Intent {
        private final long id;
        private final long parentId;
        private final String category;
        private final String source;
        private final String producer;
        private final long issuedAtNs;
        private final Set<String> destinations = new LinkedHashSet<>();
        private int terminalChildren;
        private int routedChildren;
        private int directRoutes;

        private Intent(long id, long parentId, String category, String source, String producer,
            long issuedAtNs) {
            this.id = id;
            this.parentId = parentId;
            this.category = category;
            this.source = source;
            this.producer = producer;
            this.issuedAtNs = issuedAtNs;
        }

        long id() { return id; }
        long parentId() { return parentId; }
        String category() { return category; }
        String source() { return source; }
        String producer() { return producer; }

        synchronized void recordChildTerminal(String state, String destination) {
            terminalChildren++;
            if (destination != null && !destination.isBlank()
                && !"VANILLA".equals(destination)) {
                routedChildren++;
                destinations.add(destination);
            }
        }

        synchronized void recordDirectRoute(String destination) {
            directRoutes++;
            destinations.add(destination);
        }

        synchronized int terminalChildCount() { return terminalChildren; }
        synchronized int routedChildCount() { return routedChildren; }
        synchronized int directRouteCount() { return directRoutes; }
        synchronized int routedCount() { return directRoutes + routedChildren; }
        synchronized String destinations() { return String.join(",", destinations); }
    }
}
