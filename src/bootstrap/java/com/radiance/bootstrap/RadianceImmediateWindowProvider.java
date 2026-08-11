package com.radiance.bootstrap;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_DONT_CARE;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_SCALE_TO_MONITOR;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.GLFW_X11_CLASS_NAME;
import static org.lwjgl.glfw.GLFW.GLFW_X11_INSTANCE_NAME;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetError;
import static org.lwjgl.glfw.GLFW.glfwGetMonitorPos;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMaximizeWindow;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSizeLimits;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwSetWindowIcon;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowHintString;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;

import com.radiance.bootstrap.ui.ColourScheme;
import com.radiance.bootstrap.ui.LoadingScene;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowPosCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vulkan early-window provider. The GLFW_NO_API window and native renderer survive handoff.
 */
public final class RadianceImmediateWindowProvider implements ImmediateWindowProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadianceBootstrap");
    private static final String[] GLFW_CANDIDATES = {
            System.mapLibraryName("glfw"), "libglfw.so.3", "libglfw.3.dylib", "glfw3.dll"
    };
    private static final long FRAME_PERIOD_MILLIS = 50L;

    private final ReentrantLock renderLock = new ReentrantLock();
    private final AtomicBoolean handedOff = new AtomicBoolean();
    private final AtomicBoolean loadingReleased = new AtomicBoolean();
    private final AtomicReference<Throwable> renderFailure = new AtomicReference<>();

    private volatile long window;
    private volatile int framebufferWidth;
    private volatile int framebufferHeight;
    private volatile int windowWidth;
    private volatile int windowHeight;
    private volatile int windowX;
    private volatile int windowY;
    private volatile boolean nativeInitialized;
    private LoadingScene scene;
    private ScheduledExecutorService renderExecutor;
    private ScheduledFuture<?> renderFuture;
    private GLFWFramebufferSizeCallback framebufferSizeCallback;
    private GLFWWindowSizeCallback windowSizeCallback;
    private GLFWWindowPosCallback windowPosCallback;
    private Method overlayFactory;
    private final AtomicBoolean firstGameFrame = new AtomicBoolean();
    private Thread handoffThread;
    private long nextTransitionFrame;

    @Override
    public String name() {
        return "radiance";
    }

    @Override
    public Runnable initialize(String[] arguments) {
        if (!RadianceGraphicsBootstrapper.isClientLaunchTarget(option(arguments, "--launchTarget", null))) {
            LOGGER.debug("Skipping the Radiance window provider for a non-client launch target");
            return () -> { };
        }
        BootstrapState.register(this);
        int requestedWidth = intOption(arguments, "--width",
                FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        int requestedHeight = intOption(arguments, "--height",
                FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        int framebufferScale = Math.max(1,
                FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_FBSCALE));
        boolean maximized = contains(arguments, "--earlywindow.maximized")
                || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);
        String minecraftVersion = option(arguments, "--fml.mcVersion", "1.21.1");
        String neoForgeVersion = option(arguments, "--fml.neoForgeVersion", "unknown");
        Path gameDirectory = Path.of(option(arguments, "--gameDir", "."))
                .toAbsolutePath().normalize();
        Path runtimeDirectory = BootstrapResources.prepareAndLoad(gameDirectory);

        try {
            createWindow(requestedWidth, requestedHeight, maximized, minecraftVersion);
            NativeRuntime.initialize(runtimeDirectory.toString(), GLFW_CANDIDATES.clone(), window);
            nativeInitialized = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!handedOff.get()) {
                    try {
                        stopEarlyRenderer();
                        NativeRuntime.closeBeforeGame();
                    } catch (Throwable failure) {
                        LOGGER.error("Unable to close the early renderer during JVM shutdown", failure);
                    }
                }
            }, "Radiance early renderer shutdown"));
            scene = new LoadingScene(framebufferScale, colourScheme(gameDirectory), minecraftVersion,
                    neoForgeVersion,
                    FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_SQUIR),
                    NativeRuntime::uploadTexture);
            validateNativeWindow();
            glfwShowWindow(window);
            glfwPollEvents();

            renderExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Radiance early loading renderer");
                thread.setDaemon(true);
                return thread;
            });
            renderFuture = renderExecutor.scheduleAtFixedRate(this::renderEarlyFrame,
                    0L, FRAME_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
            return this::periodicTick;
        } catch (Throwable failure) {
            closeFailedInitialization();
            throw failure;
        }
    }

    private void createWindow(int width, int height, boolean maximized, String minecraftVersion) {
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        // Treat the requested resolution as framebuffer pixels on Windows, without DPI enlargement.
        glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_FALSE);
        if (minecraftVersion != null) {
            String vanillaWindowTitle = "Minecraft* " + minecraftVersion;
            glfwWindowHintString(GLFW_X11_CLASS_NAME, vanillaWindowTitle);
            glfwWindowHintString(GLFW_X11_INSTANCE_NAME, vanillaWindowTitle);
        }
        window = glfwCreateWindow(width, height, "Minecraft: NeoForge Loading...", 0L, 0L);
        if (window == 0L) {
            throw new IllegalStateException("Unable to create the Radiance GLFW_NO_API window");
        }
        glfwSetWindowSizeLimits(window, 854, 480, GLFW_DONT_CARE, GLFW_DONT_CARE);

        long monitor = glfwGetPrimaryMonitor();
        if (monitor != 0L) {
            // Match NeoForge's early display (DisplayWindow): center the client area inside the
            // full video mode at the monitor origin. Centering inside glfwGetMonitorWorkarea sits
            // half a taskbar height above the non-Radiance startup window because the work area
            // excludes the taskbar.
            GLFWVidMode videoMode = glfwGetVideoMode(monitor);
            if (videoMode != null) {
                int[] monitorX = new int[1];
                int[] monitorY = new int[1];
                glfwGetMonitorPos(monitor, monitorX, monitorY);
                int[] actualWidth = new int[1];
                int[] actualHeight = new int[1];
                glfwGetWindowSize(window, actualWidth, actualHeight);
                glfwSetWindowPos(window,
                        monitorX[0] + Math.max(0, (videoMode.width() - actualWidth[0]) / 2),
                        monitorY[0] + Math.max(0, (videoMode.height() - actualHeight[0]) / 2));
            }
        }
        if (maximized) glfwMaximizeWindow(window);
        setNeoForgedIcon();

        framebufferSizeCallback = GLFWFramebufferSizeCallback.create((handle, w, h) -> {
            if (handle == window) {
                framebufferWidth = w;
                framebufferHeight = h;
            }
        });
        windowSizeCallback = GLFWWindowSizeCallback.create((handle, w, h) -> {
            if (handle == window && w > 0 && h > 0) {
                windowWidth = w;
                windowHeight = h;
            }
        });
        windowPosCallback = GLFWWindowPosCallback.create((handle, x, y) -> {
            if (handle == window) {
                windowX = x;
                windowY = y;
            }
        });
        glfwSetFramebufferSizeCallback(window, framebufferSizeCallback);
        glfwSetWindowSizeCallback(window, windowSizeCallback);
        glfwSetWindowPosCallback(window, windowPosCallback);
        refreshWindowMetrics();
    }

    private void setNeoForgedIcon() {
        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];
        try (GLFWImage.Buffer icons = GLFWImage.malloc(1)) {
            ByteBuffer pixels = STBImage.stbi_load_from_memory(
                    readClasspathResource("neoforged_icon.png", 20000),
                    width, height, channels, 4);
            if (pixels == null) {
                throw new NullPointerException("Unable to decode NeoForged icon: "
                        + STBImage.stbi_failure_reason());
            }
            try {
                icons.position(0).width(width[0]).height(height[0]).pixels(pixels);
                glfwSetWindowIcon(window, icons);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } catch (NullPointerException e) {
            System.err.println("Failed to load NeoForged icon");
        }
        handleLastGlfwError((error, description) -> LOGGER.debug(String.format(
                "Suppressing GLFW icon error: [0x%X]%s", error, description)));
    }

    private static ByteBuffer readClasspathResource(String name, int initialCapacity) {
        try (var channel = Channels.newChannel(Objects.requireNonNull(
                RadianceImmediateWindowProvider.class.getClassLoader().getResourceAsStream(name),
                "The resource " + name + " cannot be found"))) {
            ByteBuffer buffer = BufferUtils.createByteBuffer(initialCapacity);
            while (true) {
                int read = channel.read(buffer);
                if (read == -1) break;
                if (!buffer.hasRemaining()) {
                    ByteBuffer expanded = BufferUtils.createByteBuffer(buffer.capacity() * 3 / 2);
                    buffer.flip();
                    expanded.put(buffer);
                    buffer = expanded;
                }
            }
            buffer.flip();
            return MemoryUtil.memSlice(buffer);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void handleLastGlfwError(
            java.util.function.BiConsumer<Integer, String> handler) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer descriptionPointer = stack.mallocPointer(1);
            int error = glfwGetError(descriptionPointer);
            if (error != org.lwjgl.glfw.GLFW.GLFW_NO_ERROR) {
                long address = descriptionPointer.get();
                String description = address == 0L ? "" : MemoryUtil.memUTF8(address);
                handler.accept(error, description);
            }
        }
    }

    private void refreshWindowMetrics() {
        int[] first = new int[1];
        int[] second = new int[1];
        glfwGetWindowSize(window, first, second);
        windowWidth = first[0];
        windowHeight = second[0];
        glfwGetWindowPos(window, first, second);
        windowX = first[0];
        windowY = second[0];
        glfwGetFramebufferSize(window, first, second);
        framebufferWidth = first[0];
        framebufferHeight = second[0];
    }

    private void renderEarlyFrame() {
        if (handedOff.get() || loadingReleased.get() || renderFailure.get() != null) return;
        renderLock.lock();
        try {
            if (handedOff.get() || renderFailure.get() != null
                    || framebufferWidth <= 0 || framebufferHeight <= 0) return;
            LoadingScene.Frame frame = scene.render(255);
            render(frame, framebufferWidth, framebufferHeight, 1.0F, false, true);
        } catch (Throwable failure) {
            if (renderFailure.compareAndSet(null, failure)) {
                LOGGER.error("Radiance early loading frame failed; the main loading thread will abort",
                        failure);
            }
        } finally {
            renderLock.unlock();
        }
    }

    @Override
    public void periodicTick() {
        if (window != 0L) {
            glfwPollEvents();
            if (glfwWindowShouldClose(window)) {
                renderFailure.compareAndSet(null,
                        new IllegalStateException("The Radiance loading window was closed"));
            }
        }
        throwRenderFailure();
        if (handedOff.get() && !firstGameFrame.get() && !loadingReleased.get()
                && Thread.currentThread() == handoffThread) {
            long now = System.nanoTime();
            if (now >= nextTransitionFrame) {
                renderLock.lock();
                try {
                    refreshWindowMetrics();
                    if (framebufferWidth > 0 && framebufferHeight > 0) {
                        render(scene.render(255), framebufferWidth, framebufferHeight, 1.0F, false, true);
                    }
                    nextTransitionFrame = now + TimeUnit.MILLISECONDS.toNanos(FRAME_PERIOD_MILLIS);
                } finally {
                    renderLock.unlock();
                }
            }
        }
    }

    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title,
            LongSupplier monitor) {
        stopEarlyRenderer();
        throwRenderFailure();
        renderLock.lock();
        try {
            if (!handedOff.compareAndSet(false, true)) {
                return window;
            }
            glfwSetWindowTitle(window, title.get());
            detachCallbacks();
            NativeRuntime.handoff(window);
            handoffThread = Thread.currentThread();
            refreshWindowMetrics();
            validateNativeWindow();
            return window;
        } finally {
            renderLock.unlock();
        }
    }

    private void stopEarlyRenderer() {
        ScheduledFuture<?> future = renderFuture;
        if (future != null) future.cancel(false);
        ScheduledExecutorService executor = renderExecutor;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out stopping the early loading renderer");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping the early loading renderer", e);
            }
        }
    }

    @Override
    public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
        width.accept(framebufferWidth);
        height.accept(framebufferHeight);
    }

    @Override
    public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter,
            IntConsumer heightSetter, IntConsumer xSetter, IntConsumer ySetter) {
        refreshWindowMetrics();
        widthSetter.accept(windowWidth);
        heightSetter.accept(windowHeight);
        xSetter.accept(windowX);
        ySetter.accept(windowY);
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Supplier<T> loadingOverlay(Supplier<?> minecraft, Supplier<?> reload,
            Consumer<Optional<Throwable>> errorConsumer, boolean fade) {
        Method factory = overlayFactory;
        if (factory == null) {
            throw new IllegalStateException("Radiance GAME loading-overlay factory was not resolved");
        }
        try {
            return (Supplier<T>) factory.invoke(null, minecraft, reload, errorConsumer, this, fade);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create the Radiance GAME loading overlay", e);
        }
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
        for (Module module : layer.modules()) {
            Class<?> type = Class.forName(module,
                    "com.radiance.client.loading.RadianceLoadingOverlay");
            if (type == null) continue;
            getClass().getModule().addReads(module);
            overlayFactory = java.util.Arrays.stream(type.getMethods())
                    .filter(method -> method.getName().equals("newInstance"))
                    .filter(method -> Modifier.isStatic(method.getModifiers()))
                    .filter(method -> method.getParameterCount() == 5)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "RadianceLoadingOverlay.newInstance must be a public static five-argument factory"));
            return;
        }
        throw new IllegalStateException("GAME layer does not contain RadianceLoadingOverlay");
    }

    /**
     * Records a loading frame into the game renderer's current UI target.
     */
    public void onGameFrame(float opacity, int framebufferWidth, int framebufferHeight,
            boolean paintBackground) {
        if (!Float.isFinite(opacity) || opacity < 0.0F || opacity > 1.0F) {
            throw new IllegalArgumentException("opacity must be finite and within 0..1");
        }
        if (loadingReleased.get() || framebufferWidth <= 0 || framebufferHeight <= 0) return;
        renderLock.lock();
        try {
            // NeoForge renders the scene opaque and applies its fade while compositing.
            if (firstGameFrame.compareAndSet(false, true)) {
                validateNativeWindow();
            }
            LoadingScene.Frame frame = scene.render(255);
            render(frame, framebufferWidth, framebufferHeight, opacity, true,
                    paintBackground);
        } finally {
            renderLock.unlock();
        }
    }

    public int backgroundAbgr() {
        return scene.backgroundAbgr();
    }

    public void addMojangTexture(int textureKey, int width, int height, ByteBuffer rgba,
            boolean linear, boolean clampToEdge) {
        renderLock.lock();
        try {
            scene.addMojangTexture(textureKey, width, height, rgba, linear, clampToEdge);
        } finally {
            renderLock.unlock();
        }
    }

    public void addMojangTexture(int textureKey) {
        renderLock.lock();
        try {
            scene.addMojangTexture(textureKey);
        } finally {
            renderLock.unlock();
        }
    }

    public void releaseLoading() {
        if (!loadingReleased.compareAndSet(false, true)) return;
        renderLock.lock();
        try {
            scene.close();
            NativeRuntime.releaseLoading();
        } finally {
            renderLock.unlock();
        }
    }

    private static void render(LoadingScene.Frame frame, int framebufferWidth,
            int framebufferHeight, float opacity, boolean gameFrame, boolean paintBackground) {
        NativeRuntime.renderFrame(frame.vertices(), frame.batches(), frame.batchCount(),
                frame.canvasWidth(), frame.canvasHeight(), frame.backgroundAbgr(),
                framebufferWidth, framebufferHeight, opacity, gameFrame, paintBackground);
    }

    @Override
    public String getGLVersion() {
        // GLFW_NO_API supplies no OpenGL capability for FML's GL_VERSION feature gate.
        return "0.0";
    }

    @Override
    public void crash(String message) {
        LOGGER.error("NeoForge startup failed while the Radiance provider was active: {}", message);
    }

    public long windowHandle() {
        return window;
    }

    public boolean isNativeInitialized() {
        return nativeInitialized;
    }

    private void closeFailedInitialization() {
        stopEarlyRendererQuietly();
        if (nativeInitialized) {
            try {
                NativeRuntime.closeBeforeGame();
            } catch (Throwable closeFailure) {
                LOGGER.error("Failed to close the partially initialized Radiance renderer", closeFailure);
            }
            nativeInitialized = false;
        }
        detachCallbacks();
        if (window != 0L) {
            glfwDestroyWindow(window);
            window = 0L;
        }
    }

    private void stopEarlyRendererQuietly() {
        ScheduledFuture<?> future = renderFuture;
        if (future != null) future.cancel(false);
        ScheduledExecutorService executor = renderExecutor;
        if (executor != null) executor.shutdownNow();
    }

    private void detachCallbacks() {
        if (window == 0L) return;
        glfwSetFramebufferSizeCallback(window, null);
        glfwSetWindowSizeCallback(window, null);
        glfwSetWindowPosCallback(window, null);
        if (framebufferSizeCallback != null) {
            framebufferSizeCallback.free();
            framebufferSizeCallback = null;
        }
        if (windowSizeCallback != null) {
            windowSizeCallback.free();
            windowSizeCallback = null;
        }
        if (windowPosCallback != null) {
            windowPosCallback.free();
            windowPosCallback = null;
        }
    }

    private void validateNativeWindow() {
        long[] identities = NativeRuntime.identities();
        if (identities == null || identities.length < 5) {
            throw new IllegalStateException("NativeRuntime.identities returned fewer than five values");
        }
        if (identities[0] != window) {
            throw new IllegalStateException("Native window identity " + identities[0]
                    + " differs from GLFW window " + window);
        }
    }

    private void throwRenderFailure() {
        Throwable failure = renderFailure.get();
        if (failure == null) return;
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException("Radiance early loading renderer failed", failure);
    }

    private static ColourScheme colourScheme(Path gameDirectory) {
        Path options = gameDirectory.resolve("options.txt");
        if (Files.isRegularFile(options)) {
            try {
                for (String line : Files.readAllLines(options)) {
                    if (line.equalsIgnoreCase("darkMojangStudiosBackground:true")) {
                        return ColourScheme.BLACK;
                    }
                }
            } catch (IOException e) {
                LOGGER.debug("Unable to read {}", options, e);
            }
        }
        return ColourScheme.RED;
    }

    private static boolean contains(String[] arguments, String key) {
        for (String argument : arguments) {
            if (key.equals(argument)) return true;
        }
        return false;
    }

    private static int intOption(String[] arguments, String key, int fallback) {
        String value = option(arguments, key, Integer.toString(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + key + ": " + value, e);
        }
    }

    private static String option(String[] arguments, String key, String fallback) {
        for (int i = 0; i + 1 < arguments.length; i++) {
            if (key.equals(arguments[i])) return arguments[i + 1];
        }
        return fallback;
    }
}
