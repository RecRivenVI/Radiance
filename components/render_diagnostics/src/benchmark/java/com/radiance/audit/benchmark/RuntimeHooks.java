package com.radiance.audit.benchmark;

import java.lang.management.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Loader-neutral common metrics. No fork JNI, GL context, focus fabrication or GPU waits. */
public final class RuntimeHooks {
    private static final int LIMIT = 200_000;
    private static final boolean correctnessOnly =
            Boolean.getBoolean("radiance.audit.correctnessOnly");
    private static final long[][] samples = new long[LIMIT][];
    private static final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private static volatile int count;
    private static long started, worldStart, frameStart, cpuStart, lastStart;
    private static long warmup, sampleDuration, startupTimeout;
    private static Field level, focused;
    private static Method stop;
    private static Method windowAttribute;
    private static Method framebufferSize;
    private static final int[] width = new int[1], height = new int[1];
    private static long window;
    private static int windowsCreated;
    private static boolean bound,
            stopRequested,
            inFrame,
            flushed,
            sampleBoundaryWritten,
            skipBoundaryFrame;
    private static String error = "", adapter = "unbound", activationGuard = "not-requested";
    private static Path output;
    private static final boolean startupObservation =
            "startup".equals(System.getProperty("radiance.audit.worldMaterials134"));
    private static long startupWindow, startupPrevious, startupMaximum, startupFirst, startupFrames;
    private static long preparationStart,
            preparationEnd,
            preparationFrames,
            preparationPrevious,
            preparationGap;
    private static final List<String> transformed = Collections.synchronizedList(new ArrayList<>());

    private RuntimeHooks() {}

    public static void initialize() throws Exception {
        String version = System.getProperty("radiance.audit.benchmark.version", "");
        if (!Set.of("1.21.1-neoforge", "1.21.4-fabric").contains(version))
            throw new IllegalArgumentException(
                    "Specify radiance.audit.benchmark.version=1.21.1-neoforge or 1.21.4-fabric");
        warmup = seconds("radiance.audit.benchmark.warmupSeconds", 30);
        sampleDuration = seconds("radiance.audit.benchmark.sampleSeconds", 30);
        startupTimeout = seconds("radiance.audit.benchmark.startupSeconds", 180);
        if (!correctnessOnly
                && threads.isThreadCpuTimeSupported()
                && !threads.isThreadCpuTimeEnabled()) threads.setThreadCpuTimeEnabled(true);
        output = Path.of("radiance-audit/benchmark/" + System.currentTimeMillis());
        Files.createDirectories(output);
        Files.writeString(
                output.resolve("CONFIG.txt"),
                "version="
                        + version
                        + "\nunattended="
                        + Boolean.getBoolean("radiance.audit.unattended")
                        + "\ncorrectnessOnly="
                        + correctnessOnly
                        + "\nwarmupNs="
                        + warmup
                        + "\nsampleNs="
                        + sampleDuration
                        + "\nnativeGpuTimings=unavailable\ngeneratedFrames=unavailable\n");
        started = System.nanoTime();
        Runtime.getRuntime()
                .addShutdownHook(new Thread(RuntimeHooks::flush, "Radiance-Audit-benchmark-save"));
    }

    private static long seconds(String key, int fallback) {
        int value = Integer.getInteger(key, fallback);
        if (value < 1 || value > 3600) throw new IllegalArgumentException(key + " must be 1..3600");
        return value * 1_000_000_000L;
    }

    public static void transformed(String name) {
        transformed.add(name);
    }

    public static void windowCreated(long handle, Class<?> glfw) {
        try {
            window = handle;
            if (startupObservation && startupWindow == 0)
                startupWindow = startupPrevious = System.nanoTime();
            windowsCreated++;
            windowAttribute = glfw.getMethod("glfwGetWindowAttrib", long.class, int.class);
            framebufferSize =
                    glfw.getMethod("glfwGetFramebufferSize", long.class, int[].class, int[].class);
            if (Boolean.getBoolean("radiance.audit.unattended"))
                activationGuard = preventActivation(handle, glfw);
        } catch (ReflectiveOperationException | RuntimeException e) {
            activationGuard = "failed";
            fail(e);
        }
    }

    // FOCUSED/FOCUS_ON_SHOW only stop GLFW's own focus calls. Windows can still activate the
    // window after a user click or when the foreground window disappears (observed 2026-09-29
    // after an eight-second empty foreground). WS_EX_NOACTIVATE excludes both; WS_EX_APPWINDOW
    // keeps the ordinary taskbar button. Z-order, size, presentation and input filtering are
    // unchanged.
    private static String preventActivation(long handle, Class<?> glfw)
            throws ReflectiveOperationException {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return "unsupported-platform";
        ClassLoader loader =
                glfw.getClassLoader() != null
                        ? glfw.getClassLoader()
                        : ClassLoader.getSystemClassLoader();
        return preventActivation(
                handle,
                Class.forName("org.lwjgl.glfw.GLFWNativeWin32", true, loader),
                Class.forName("org.lwjgl.system.windows.User32", true, loader));
    }

    static String preventActivation(long handle, Class<?> nativeWin32, Class<?> user32)
            throws ReflectiveOperationException {
        long hwnd =
                (long) nativeWin32.getMethod("glfwGetWin32Window", long.class).invoke(null, handle);
        if (hwnd == 0) throw new IllegalStateException("Benchmark window has no Win32 handle");
        int index = user32.getField("GWL_EXSTYLE").getInt(null);
        long required =
                user32.getField("WS_EX_NOACTIVATE").getInt(null)
                        | user32.getField("WS_EX_APPWINDOW").getInt(null);
        Method get = user32.getMethod("GetWindowLongPtr", long.class, int.class);
        Method set = user32.getMethod("SetWindowLongPtr", long.class, int.class, long.class);
        set.invoke(null, hwnd, index, (long) get.invoke(null, hwnd, index) | required);
        if (((long) get.invoke(null, hwnd, index) & required) != required)
            throw new IllegalStateException("Benchmark window activation guard was not applied");
        return "applied";
    }

    public static int cursorMode(int mode, int value) {
        return mode == 0x00033001 ? 0x00034001 : value;
    }

    public static void neutralCursor(Object x, Object y) {
        for (Object target : new Object[] {x, y}) {
            if (target instanceof java.nio.DoubleBuffer b) b.put(b.position(), 0d);
            else if (target instanceof double[] a) a[0] = 0d;
        }
    }

    public static void requireWindowed(long monitor) {
        if (monitor != 0)
            throw new IllegalStateException(
                    "Unattended benchmarks require a windowed configuration; fullscreen was not silently changed");
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void bind(Object client) throws ReflectiveOperationException {
        boolean intermediary = client.getClass().getName().equals("net.minecraft.class_310");
        adapter = intermediary ? "fabric-1.21.4-intermediary" : "neoforge-1.21.1-mojmap";
        level = field(client.getClass(), intermediary ? "field_1687" : "level");
        focused = field(client.getClass(), intermediary ? "field_1695" : "windowActive");
        stop = client.getClass().getDeclaredMethod(intermediary ? "method_1592" : "stop");
        stop.setAccessible(true);
        bound = true;
    }

    public static void begin(Object client, boolean render) {
        if (render && Boolean.getBoolean("radiance.audit.optimizeWorld"))
            WorldOptimization.poll(client);
        if (correctnessOnly || !render) return;
        try {
            if (!bound) bind(client);
            inFrame = true;
            frameStart = System.nanoTime();
            cpuStart = threads.isThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : -1;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
        }
    }

    /** Drives vanilla Optimize World without loading a diagnostic or product mod. */
    private static final class WorldOptimization {
        private static boolean launched, done, stopped;
        private static Object screen, upgrader, access;
        private static long since = System.nanoTime();

        private static Object invoke(Object target, String name, Object... args)
                throws ReflectiveOperationException {
            for (Method method : target.getClass().getMethods())
                if (method.getName().equals(name) && method.getParameterCount() == args.length)
                    return method.invoke(target, args);
            throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        }

        private static void poll(Object client) {
            if (stopped) return;
            try {
                if (!Files.isRegularFile(Path.of(".radiance-vanilla-upgrade")))
                    throw new IllegalStateException("Missing owned vanilla upgrade marker");
                if (System.nanoTime() - since > 900_000_000_000L)
                    throw new IllegalStateException("Vanilla Optimize World timeout");
                if (!launched) {
                    Object current = field(client.getClass(), "screen").get(client);
                    if (current == null
                            || !current.getClass()
                                    .getName()
                                    .equals("net.minecraft.client.gui.screens.TitleScreen")
                            || invoke(client, "getOverlay") != null) return;
                    ClassLoader loader = client.getClass().getClassLoader();
                    Class<?> modList = Class.forName("net.neoforged.fml.ModList", true, loader);
                    Object list = modList.getMethod("get").invoke(null);
                    Class<?> info =
                            Class.forName(
                                    "net.neoforged.neoforgespi.language.IModInfo", true, loader);
                    var ids = new TreeSet<String>();
                    for (Object item : (Iterable<?>) invoke(list, "getMods"))
                        ids.add((String) info.getMethod("getModId").invoke(item));
                    if (!ids.equals(Set.of("minecraft", "neoforge")))
                        throw new IllegalStateException(
                                "World upgrade must be pure vanilla: " + ids);
                    Object source = invoke(client, "getLevelSource");
                    access = invoke(source, "validateAndCreateAccess", "Newport City");
                    Class<?> type =
                            Class.forName(
                                    "net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen",
                                    true,
                                    loader);
                    Method create =
                            Arrays.stream(type.getMethods())
                                    .filter(
                                            m ->
                                                    m.getName().equals("create")
                                                            && m.getParameterCount() == 5)
                                    .findFirst()
                                    .orElseThrow();
                    Object callback =
                            Proxy.newProxyInstance(
                                    loader,
                                    new Class<?>[] {create.getParameterTypes()[1]},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("accept")) {
                                            if (!Boolean.TRUE.equals(args[0]))
                                                throw new IllegalStateException("Upgrade canceled");
                                            done = true;
                                            return null;
                                        }
                                        if (method.getName().equals("toString"))
                                            return "Owned vanilla optimization callback";
                                        if (method.getName().equals("hashCode"))
                                            return System.identityHashCode(proxy);
                                        if (method.getName().equals("equals"))
                                            return proxy == args[0];
                                        throw new UnsupportedOperationException(method.toString());
                                    });
                    screen =
                            create.invoke(
                                    null,
                                    client,
                                    callback,
                                    invoke(client, "getFixerUpper"),
                                    access,
                                    false);
                    if (screen == null)
                        throw new IllegalStateException("Vanilla Optimize World refused data");
                    upgrader = field(type, "upgrader").get(screen);
                    invoke(client, "setScreen", screen);
                    launched = true;
                    System.out.println("VANILLA_OPTIMIZE started eraseCache=false mods=" + ids);
                }
                if (!done) return;
                var result = new Properties();
                for (String name :
                        List.of("getTotalChunks", "getConverted", "getSkipped", "isFinished"))
                    result.setProperty(name, String.valueOf(invoke(upgrader, name)));
                Object status = invoke(upgrader, "getStatus");
                Object contents = invoke(status, "getContents");
                String key = String.valueOf(invoke(contents, "getKey"));
                result.setProperty("statusKey", key);
                int total = Integer.parseInt(result.getProperty("getTotalChunks"));
                if (!key.equals("optimizeWorld.stage.finished.chunks")
                        || total <= 0
                        || total
                                != Integer.parseInt(result.getProperty("getConverted"))
                                        + Integer.parseInt(result.getProperty("getSkipped")))
                    throw new IllegalStateException("Incomplete world optimization: " + result);
                invoke(
                        upgrader,
                        "cancel"); // Joins only the completed vanilla worker, never a GPU wait.
                invoke(access, "close");
                result.setProperty("eraseCache", "false");
                Path destination =
                        Path.of(System.getProperty("validation.result"))
                                .resolve("optimization.properties");
                try (var writer = Files.newBufferedWriter(destination)) {
                    result.store(writer, "Vanilla Optimize World");
                }
                stopped = true;
                System.out.println("VANILLA_OPTIMIZE PASS " + result);
                invoke(client, "stop");
            } catch (Throwable failure) {
                stopped = true;
                failure.printStackTrace();
                try {
                    Files.writeString(
                            Path.of(System.getProperty("validation.result"))
                                    .resolve("optimization-failure.txt"),
                            failure.toString());
                    invoke(client, "stop");
                } catch (Exception secondary) {
                    failure.addSuppressed(secondary);
                }
            }
        }
    }

    public static void end(Object client, boolean render) {
        if (render) startupFrame();
        if (correctnessOnly || !render || !bound) return;
        try {
            long now = System.nanoTime();
            boolean world = level.get(client) != null;
            if (world && worldStart == 0) {
                worldStart = frameStart;
                if (output != null) captureSettings(client, "world-start");
            }
            long elapsed = worldStart == 0 ? 0 : frameStart - worldStart;
            if (world && elapsed >= warmup && !sampleBoundaryWritten) {
                sampleBoundaryWritten = true;
                if (output != null) captureSettings(client, "sample-start");
                // Keep boundary I/O and its following start interval out of the sample.
                skipBoundaryFrame = true;
                lastStart = frameStart;
                inFrame = false;
                return;
            }
            if (skipBoundaryFrame) {
                skipBoundaryFrame = false;
                lastStart = frameStart;
                inFrame = false;
                return;
            }
            if (world && elapsed >= warmup && elapsed < warmup + sampleDuration && count < LIMIT) {
                long cpu = cpuStart < 0 ? -1 : threads.getCurrentThreadCpuTime() - cpuStart;
                int actualFocus =
                        windowAttribute == null
                                ? -1
                                : (int) windowAttribute.invoke(null, window, 0x00020001);
                int minimized =
                        windowAttribute == null
                                ? -1
                                : (int) windowAttribute.invoke(null, window, 0x00020002);
                if (framebufferSize != null) framebufferSize.invoke(null, window, width, height);
                samples[count] =
                        new long[] {
                            frameStart - started,
                            now - frameStart,
                            lastStart == 0 ? 0 : frameStart - lastStart,
                            cpu,
                            focused.getBoolean(client) ? 1 : 0,
                            actualFocus,
                            width[0],
                            height[0],
                            minimized
                        };
                count++;
            }
            lastStart = frameStart;
            inFrame = false;
            if (!stopRequested
                    && (count >= LIMIT
                            || elapsed >= warmup + sampleDuration
                            || (worldStart == 0 && now - started > startupTimeout))) {
                if (worldStart == 0) error = "WORLD_NOT_ENTERED";
                if (world && output != null) captureSettings(client, "sample-end");
                stopRequested = true;
                stop.invoke(client); // Original client shutdown saves integrated server.
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
        }
    }

    private static void fail(Exception e) {
        if (error.isEmpty()) {
            error = e.toString();
            System.err.println("[Radiance Audit/benchmark] observer invalid: " + error);
        }
    }

    /** Java-selected active modules/attributes, not a claim of SDK evaluate success. */
    private static void captureSettings(Object client, String phase) {
        try {
            ClassLoader loader = client.getClass().getClassLoader();
            Properties values =
                    readSettings(
                            Class.forName("com.radiance.client.pipeline.Pipeline", false, loader),
                            Class.forName("com.radiance.client.option.Options", false, loader));
            if (Boolean.getBoolean("radiance.audit.benchmark.chunkBoundaries"))
                captureChunkBoundary(loader, values);
            try (var w = Files.newBufferedWriter(output.resolve(phase + "-settings.properties"))) {
                values.store(w, "Observed live Java pipeline and options; no setters invoked");
            }
        } catch (Exception e) {
            fail(e);
        }
    }

    static void captureChunkBoundary(ClassLoader loader, Properties values)
            throws ReflectiveOperationException {
        // Optional observer owns fork-specific JNI; the startup companion stays loader-neutral.
        Class.forName("com.radiance.audit.BenchmarkChunkState", true, loader)
                .getMethod("capture", Properties.class)
                .invoke(null, values);
    }

    public static Properties readSettings(Class<?> pipeline, Class<?> options)
            throws ReflectiveOperationException {
        Properties p = new Properties();
        p.setProperty(
                "activePreset", String.valueOf(pipeline.getMethod("getActivePreset").invoke(null)));
        Object instance = pipeline.getField("INSTANCE").get(null);
        var modules = (List<?>) pipeline.getMethod("getModules").invoke(instance);
        List<String> names = new ArrayList<>();
        for (Object m : modules) {
            String name = String.valueOf(m.getClass().getField("name").get(m));
            names.add(name);
            var attributes = (List<?>) m.getClass().getField("attributeConfigs").get(m);
            if (attributes != null)
                for (Object a : attributes)
                    p.setProperty(
                            String.valueOf(a.getClass().getField("name").get(a)),
                            String.valueOf(a.getClass().getField("value").get(a)));
        }
        p.setProperty("activeModules", String.join(",", names));
        for (Field f : options.getFields())
            if (Modifier.isStatic(f.getModifiers())
                    && (f.getType().isPrimitive() || f.getType() == String.class))
                p.setProperty("option." + f.getName(), String.valueOf(f.get(null)));
        return p;
    }

    public static synchronized void flush() {
        if (flushed || output == null) return;
        flushed = true;
        try {
            if (startupObservation) {
                Properties observation = new Properties();
                observation.setProperty(
                        "windowToFirstFrameMillis",
                        Double.toString((startupFirst - startupWindow) / 1e6));
                observation.setProperty(
                        "maximumCompletedFrameGapMillis", Double.toString(startupMaximum / 1e6));
                observation.setProperty(
                        "framesBeforeInteractiveMenu", Long.toString(startupFrames));
                observation.setProperty("windowsCreated", Integer.toString(windowsCreated));
                observation.setProperty("windowHandle", Long.toString(window));
                observation.setProperty(
                        "preparationMillis",
                        Double.toString((preparationEnd - preparationStart) / 1e6));
                observation.setProperty(
                        "framesDuringPreparation", Long.toString(preparationFrames));
                observation.setProperty(
                        "maximumPreparationFrameGapMillis", Double.toString(preparationGap / 1e6));
                observation.setProperty(
                        "scope",
                        "window creation, completed early present calls, completed game frames; not scanout timing");
                try (var writer =
                        Files.newBufferedWriter(
                                Path.of(
                                        System.getProperty("validation.result"),
                                        "startup-window.properties"))) {
                    observation.store(writer, "Startup observation");
                }
            }
            if (!correctnessOnly)
                try (var w = Files.newBufferedWriter(output.resolve("frames.csv"))) {
                    w.write(
                            "start_ns,frame_wall_ns,start_interval_ns,render_thread_cpu_ns,minecraft_cached_focused,glfw_actual_focused,framebuffer_width,framebuffer_height,iconified\n");
                    int n = count;
                    for (int i = 0; i < n; i++) {
                        long[] row = samples[i];
                        for (int j = 0; j < row.length; j++) {
                            if (j > 0) w.write(",");
                            w.write(Long.toString(row[j]));
                        }
                        w.write("\n");
                    }
                }
            Files.writeString(
                    output.resolve("STATUS.txt"),
                    "adapter="
                            + adapter
                            + "\nframes="
                            + count
                            + "\nworldEntered="
                            + (worldStart != 0)
                            + "\ncorrectnessOnly="
                            + correctnessOnly
                            + "\nnormalStopRequested="
                            + stopRequested
                            + "\nincompleteFrame="
                            + inFrame
                            + "\nwindowsCreated="
                            + windowsCreated
                            + "\nactivationGuard="
                            + activationGuard
                            + "\nerror="
                            + error
                            + "\n"
                            + "This observer does not prove native close, world save, visible output, or generated-frame delivery. Inspect client logs separately.\n");
            Files.write(output.resolve("transformed.txt"), List.copyOf(transformed));
        } catch (Exception e) {
            System.err.println("[Radiance Audit/benchmark] cannot write evidence: " + e);
        }
    }

    public static void loadingFrame(boolean gameFrame) {
        if (!gameFrame) startupFrame();
    }

    public static synchronized void preparationStarted() {
        preparationStart = preparationPrevious = System.nanoTime();
    }

    public static synchronized void preparationFinished() {
        preparationEnd = System.nanoTime();
        preparationGap = Math.max(preparationGap, preparationEnd - preparationPrevious);
    }

    private static synchronized void startupFrame() {
        if (!startupObservation
                || Boolean.getBoolean("radiance.audit.menuInteractive")
                || startupWindow == 0) return;
        long now = System.nanoTime();
        if (startupFirst == 0) startupFirst = now;
        startupMaximum = Math.max(startupMaximum, now - startupPrevious);
        startupPrevious = now;
        ++startupFrames;
        if (preparationStart != 0 && preparationEnd == 0) {
            preparationGap = Math.max(preparationGap, now - preparationPrevious);
            preparationPrevious = now;
            ++preparationFrames;
        }
    }
}
