package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import net.minecraft.client.Minecraft;

public class RendererProxy {
    public static native void streamlineFrameEvent(int event);


    private static final int VK_SUCCESS = FrameLifecycle.VK_SUCCESS;
    private static final int VK_NOT_READY = FrameLifecycle.VK_NOT_READY;
    private static final int VK_ERROR_DEVICE_LOST = -4;
    private static volatile boolean initialized;
    private static final FrameLifecycle FRAME = new FrameLifecycle();

    public static native void initFolderPath(String folderPath);

    private static native int initRendererNative(String[] glfwLibCandidates, long windowHandle);

    public static void initRenderer(Window window) {
        String mapped = System.mapLibraryName("glfw");
        String[] candidates = {mapped, "libglfw.so.3", "libglfw.3.dylib", "glfw3.dll"};
        checkVkResult("初始化 Vulkan 渲染器",
            RendererProxy.initRendererNative(candidates, window.getWindow()));
        FRAME.markAcquired();
        initialized = true;
        RenderSystem.apiDescription = backendString(7938);
    }

    public static native int maxSupportedTextureSize();
    public static native int capabilityLimit(int name);
    public static native boolean tessellationSupported();

    /**
     * Supplies the three strings queried by Blaze3D's GlUtil without requiring an OpenGL
     * context: GL_VENDOR, GL_RENDERER, and GL_VERSION respectively.
     */
    public static native String backendString(int name);

    private static native int acquireContextNative();

    private static native int submitCommandNative();

    private static native int presentNative();
    private static native int warmupCurrentPipelineNative();

    public static void warmupCurrentPipeline() {
        RenderSystem.assertOnRenderThreadOrInit();
        checkVkResult("预热当前渲染配置", warmupCurrentPipelineNative());
    }

    public static boolean acquireContext() {
        int result = acquireContextNative();
        FrameLifecycle.AcquireOutcome outcome = FRAME.acceptAcquireResult(result);
        if (outcome == FrameLifecycle.AcquireOutcome.FAILED) {
            checkVkResult("获取交换链图像", result);
        }
        return outcome == FrameLifecycle.AcquireOutcome.ACQUIRED;
    }

    public static boolean hasAcquiredFrame() {
        return FRAME.acquired();
    }

    public static void submitCommand() {
        if (!FRAME.acquired()) {
            return;
        }
        int result;
        // TextureProxy's upload calls use this monitor. Keep it only while native code
        // consumes the upload queue; a fence wait must never retain it.
        synchronized (TextureProxy.class) {
            result = submitCommandNative();
        }
        checkVkResult("提交 Vulkan 帧", result);
    }

    public static void present() {
        if (!FRAME.acquired()) {
            return;
        }
        try {
            checkVkResult("呈现 Vulkan 帧", presentNative());
        } finally {
            FRAME.markPresentedOrClosed();
        }
    }

    public static void submitCommandAndPresent() {
        if (!FRAME.acquired()) {
            return;
        }
        try {
            int result;
            synchronized (TextureProxy.class) {
                result = submitCommandNative();
            }
            checkVkResult("提交 Vulkan 帧", result);
            checkVkResult("呈现 Vulkan 帧", presentNative());
        } finally {
            FRAME.markPresentedOrClosed();
        }
    }

    public static native void fuseWorld();

    /** Records a lit, opaque camera-inside-block layer for the current HDR world frame. */
    public static native boolean cameraBlockEffect(int textureId,
        float u0, float v0, float u1, float v1, float lightScale);

    /** Records a repeating translucent fluid texture for the current HDR world frame. */
    public static native boolean cameraFluidEffect(int textureId,
        float uBase, float vBase, float repeatU, float repeatV, float alpha, float brightness);

    /** Records the vanilla first-person fire sprites as a scene-linear emissive layer. */
    public static native boolean cameraFireEffect(int textureId,
        float u0, float v0, float u1, float v1, float alpha, float emissionScale);

    /** Returns true only when the Vulkan UI context recorded the blur candidate. */
    public static native boolean postBlur();

    /** Returns true only when the Vulkan UI context recorded the entity-effect candidate. */
    public static native boolean postEntityEffect(int effect);

    /** One-second presentation rates: high 32 bits rendered, low 32 bits generated. */
    public static long presentationRates() {
        return initialized ? presentationRatesNative() : 0;
    }

    private static native long presentationRatesNative();

    /** High 32 bits: sections with PT geometry; low 32 bits: allocated section slots. */
    public static long sectionCounts() {
        return initialized ? sectionCountsNative() : -1;
    }

    private static native long sectionCountsNative();

    public static native int beginGpuProfile();

    public static native boolean isGpuProfileReady(int sequence);

    public static native long gpuProfileTimeNs(int sequence);

    private static native int closeNative();
    private static native void injectRuntimeFatalForAcceptanceNative();
    private static native void probeNormalCallAfterFatalNative();
    private static native String lifecycleAcceptanceStateNative();
    private static native String roundTripAcceptanceStringNative(String input);

    public static void injectRuntimeFatalForAcceptance() {
        injectRuntimeFatalForAcceptanceNative();
    }

    public static void probeNormalCallAfterFatal() {
        probeNormalCallAfterFatalNative();
    }

    public static String lifecycleAcceptanceState() {
        return lifecycleAcceptanceStateNative();
    }

    public static String roundTripAcceptanceString(String input) {
        return roundTripAcceptanceStringNative(input);
    }

    private static native int beginResourceReloadNative();

    private static native int endResourceReloadNative();

    public static boolean beginResourceReload() {
        if (!initialized) {
            return false;
        }
        synchronized (TextureProxy.class) {
            checkVkResult("开始 Vulkan 资源重载", beginResourceReloadNative());
        }
        return true;
    }

    public static void endResourceReload(boolean transactionActive) {
        if (!transactionActive || !initialized) {
            return;
        }
        synchronized (TextureProxy.class) {
            checkVkResult("提交 Vulkan 资源重载", endResourceReloadNative());
        }
    }

    public static void close() {
        initialized = false;
        FRAME.markPresentedOrClosed();
        int result;
        synchronized (TextureProxy.class) {
            try {
                TextureProxy.TASKS.close();
            } finally {
                result = closeNative();
            }
        }
        if (result != 0) {
            com.radiance.client.RadianceClient.LOGGER.warn(
                "Vulkan renderer retained an earlier failure during shutdown: {}",
                formatVkFailure(result));
        }
    }

    /**
     * Releases the native renderer immediately before Minecraft terminates for a crash.
     * Minecraft's fatal path calls {@code handleExit} instead of {@code Minecraft.close()}, so the
     * ordinary close injection cannot run there. A close failure is diagnostic only: it must not
     * replace the original crash or prevent the integrated-server save that already completed.
     */
    public static void closeAfterFatalExit() {
        Throwable closeFailure = RendererShutdown.closeIfInitialized(initialized,
            RendererProxy::close);
        if (closeFailure != null) {
            com.radiance.client.RadianceClient.LOGGER.error(
                "Failed to close the Vulkan renderer during fatal client shutdown", closeFailure);
        }
    }

    public static native void shouldRenderWorld(boolean renderWorld);

    private static native int takeScreenshotNative(boolean withUI, int width, int height,
        int channel, long pointer);

    private static native String lastFailureDescriptionNative();

    public static void takeScreenshot(boolean withUI, int width, int height, int channel,
        long pointer) {
        checkVkResult("读取 Vulkan 截图",
            takeScreenshotNative(withUI, width, height, channel, pointer));
    }

    private static void checkVkResult(String operation, int result) {
        if (result == VK_SUCCESS) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (result == VK_NOT_READY && client != null && client.getWindow() != null
                && org.lwjgl.glfw.GLFW.glfwWindowShouldClose(client.getWindow().getWindow())) return;

        String nativeDescription = lastFailureDescriptionNative();
        String suffix = nativeDescription == null || nativeDescription.isBlank()
            ? formatVkFailure(result)
            : nativeDescription;
        throw new IllegalStateException(operation + "失败：" + suffix);
    }

    private static String formatVkFailure(int result) {
        if (result == VK_ERROR_DEVICE_LOST) {
            return "VK_ERROR_DEVICE_LOST（显卡设备已丢失，当前客户端无法继续渲染）";
        }
        return "VkResult=" + result;
    }

    public static NativeImage takeScreenshotWithoutUI() {
        Minecraft mc = Minecraft.getInstance();
        int
            width =
            mc.getWindow()
                .getScreenWidth();
        int
            height =
            mc.getWindow()
                .getScreenHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);
        ((INativeImageExt) (Object) nativeImage).radiance$loadFromTextureImageWithoutUI(0, true);
        return nativeImage;
    }
}
