package com.radiance.bootstrap.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;

import com.radiance.bootstrap.NativeRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Hardware lifecycle harness. Each enabled invocation owns exactly one native Renderer. */
class LoadingHandoffGpuTest {
    private static final int WIDTH = 854;
    private static final int HEIGHT = 480;

    @Test
    void handoffKeepsSameThreadEarlyGapUntilFirstGameFrame() throws Exception {
        String runtimeValue = System.getenv("RADIANCE_HANDOFF_RUNTIME");
        assumeTrue(runtimeValue != null && !runtimeValue.isBlank(),
                "Set RADIANCE_HANDOFF_RUNTIME to run handoff lifecycle validation");
        Path runtimeSource = Path.of(runtimeValue).toAbsolutePath().normalize();
        Path output = Path.of(System.getenv().getOrDefault("RADIANCE_HANDOFF_OUTPUT",
                "D:/Workspaces/Artifacts/Radiance-SPI-Loading-20260914/handoff-lifecycle"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output);
        Path runtime = copyRuntime(runtimeSource, output.resolve("runtime-" + ProcessHandle.current().pid()));

        long window = 0;
        boolean nativeInitialized = false;
        boolean gameClosed = false;
        Throwable foreignThreadFailure = null;
        Throwable postGameEarlyFailure = null;
        int submitResult = Integer.MIN_VALUE;
        int presentResult = Integer.MIN_VALUE;
        int closeResult = Integer.MIN_VALUE;
        long[] beforeRelease = null;
        long[] afterRelease = null;
        int[] initialFramebuffer = null;
        int[] resizedFramebuffer = null;
        int[] restoredFramebuffer = null;
        assertTrue(glfwInit(), "GLFW initialization failed");
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            window = glfwCreateWindow(WIDTH, HEIGHT, "Radiance handoff lifecycle", 0, 0);
            assertNotEquals(0, window, "Unable to create Vulkan lifecycle window");

            System.load(runtime.resolve("libxess.dll").toString());
            System.load(runtime.resolve("core.dll").toString());
            NativeRuntime.initialize(runtime.toString(),
                    new String[] { System.mapLibraryName("glfw"), "glfw3.dll" }, window);
            nativeInitialized = true;
            bindGameNatives();

            try (LoadingScene scene = new LoadingScene(1, ColourScheme.RED, "1.21.1", "21.1.248",
                    false, NativeRuntime::uploadTexture)) {
                LoadingScene.Frame frame = scene.render(new LoadingScene.FrameInput(12, 255, List.of(), List.of(),
                        new LoadingScene.PerformanceSnapshot(0.25f, "Memory")));

                // Normal early frame before handoff.
                initialFramebuffer = framebufferSize(window);
                render(frame, false, initialFramebuffer[0], initialFramebuffer[1]);

                // Vsync changes presentation mode only. The following present must not rebuild
                // size-dependent UI/world targets when the surface extent is unchanged.
                // Reading saved options after early initialization must also update presentation.
                GameNatives.setVsync(false, false);
                render(frame, false, initialFramebuffer[0], initialFramebuffer[1]);

                glfwSetWindowSize(window, 1000, 600);
                resizedFramebuffer = waitForFramebufferSize(window, initialFramebuffer[0], initialFramebuffer[1]);
                GameNatives.framebufferSizeChanged();
                render(frame, false, resizedFramebuffer[0], resizedFramebuffer[1]);
                assertEquals(WIDTH * HEIGHT * 4, NativeRuntime.captureCanvas().length,
                        "Loading canvas stays fixed while the swapchain-sized composite target changes");

                glfwSetWindowSize(window, WIDTH, HEIGHT);
                restoredFramebuffer = waitForFramebufferSize(window, resizedFramebuffer[0], resizedFramebuffer[1]);
                assertArrayEquals(initialFramebuffer, restoredFramebuffer,
                        "Restoring the original window size must restore its framebuffer extent");
                GameNatives.framebufferSizeChanged();
                render(frame, false, restoredFramebuffer[0], restoredFramebuffer[1]);
                GameNatives.setVsync(true, true);
                render(frame, false, restoredFramebuffer[0], restoredFramebuffer[1]);

                NativeRuntime.handoff(window);

                // The handoff thread owns the bounded gap and may still present early frames.
                final int handoffWidth = restoredFramebuffer[0];
                final int handoffHeight = restoredFramebuffer[1];
                render(frame, false, handoffWidth, handoffHeight);
                assertEquals(WIDTH * HEIGHT * 4, NativeRuntime.captureCanvas().length);

                // A foreign thread is forbidden as soon as handoff establishes thread ownership.
                AtomicReference<Throwable> foreignFailure = new AtomicReference<>();
                Thread foreign = new Thread(() -> {
                    try {
                        render(frame, false, handoffWidth, handoffHeight);
                    } catch (Throwable failure) {
                        foreignFailure.set(failure);
                    }
                }, "radiance-handoff-foreign-render");
                foreign.start();
                foreign.join(10_000);
                assertFalse(foreign.isAlive(), "Foreign render thread did not finish");
                foreignThreadFailure = foreignFailure.get();
                assertInstanceOf(RuntimeException.class, foreignThreadFailure,
                        "Foreign-thread early rendering must be rejected after handoff");
                assertMessageContains(foreignThreadFailure, "Only the handoff thread");

                // The first game frame records into the already-acquired command buffer.
                render(frame, true, handoffWidth, handoffHeight);
                submitResult = GameNatives.submit();
                assertEquals(0, submitResult, "Game submit must return VK_SUCCESS");
                presentResult = GameNatives.present();
                assertEquals(0, presentResult, "Game present must return VK_SUCCESS");

                // No early frame is accepted once a real game frame has been submitted.
                postGameEarlyFailure = assertThrows(RuntimeException.class,
                        () -> render(frame, false, handoffWidth, handoffHeight));
                assertMessageContains(postGameEarlyFailure, "Early repaint after GAME");

                beforeRelease = NativeRuntime.identities();
                assertEquals(5, beforeRelease.length);
                for (long identity : beforeRelease) assertNotEquals(0, identity);
                NativeRuntime.releaseLoading();
                afterRelease = NativeRuntime.identities();
                assertArrayEquals(beforeRelease, afterRelease,
                        "releaseLoading must retain window, instance, physical device, device, and swapchain");

                closeResult = GameNatives.close();
                gameClosed = true;
                assertEquals(0, closeResult, "Game close must return VK_SUCCESS");
            }
        } finally {
            if (nativeInitialized && !gameClosed) NativeRuntime.closeBeforeGame();
            if (window != 0) glfwDestroyWindow(window);
            glfwTerminate();
            Files.writeString(output.resolve("handoff-report.json"), report(foreignThreadFailure,
                    postGameEarlyFailure, submitResult, presentResult, closeResult, beforeRelease, afterRelease,
                    initialFramebuffer, resizedFramebuffer, restoredFramebuffer));
        }
    }

    private static void render(LoadingScene.Frame frame, boolean gameFrame, int framebufferWidth,
                               int framebufferHeight) {
        NativeRuntime.renderFrame(frame.vertices(), frame.batches(), frame.batchCount(), frame.canvasWidth(),
                frame.canvasHeight(), frame.backgroundAbgr(), framebufferWidth, framebufferHeight,
                1.0f, gameFrame, true);
    }

    private static void bindGameNatives() {
        NativeRuntime.bindGameNatives(GameNatives.class,
                new String[] { "submit", "present", "close", "setVsync", "framebufferSizeChanged" },
                new String[] { "()I", "()I", "()I", "(ZZ)V", "()V" },
                new String[] {
                        "Java_com_radiance_client_proxy_vulkan_RendererProxy_submitCommandNative",
                        "Java_com_radiance_client_proxy_vulkan_RendererProxy_presentNative",
                        "Java_com_radiance_client_proxy_vulkan_RendererProxy_closeNative",
                        "Java_com_radiance_client_option_Options_nativeSetVsync",
                        "Java_com_radiance_client_proxy_vulkan_WindowProxy_onFramebufferSizeChanged"
                });
    }

    private static int[] framebufferSize(long window) {
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        assertTrue(width[0] > 0 && height[0] > 0, "Framebuffer must be drawable");
        return new int[] { width[0], height[0] };
    }

    private static int[] waitForFramebufferSize(long window, int oldWidth, int oldHeight) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        int[] size;
        do {
            glfwPollEvents();
            size = framebufferSize(window);
            if (size[0] != oldWidth || size[1] != oldHeight) return size;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("Framebuffer size did not change from " + oldWidth + "x" + oldHeight);
        return size;
    }

    private static void assertMessageContains(Throwable failure, String expected) {
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().contains(expected),
                () -> "Expected native rejection containing '" + expected + "' but got: " + failure.getMessage());
    }

    private static Path copyRuntime(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        for (String library : List.of("libxess.dll", "libxess_dx11.dll", "libxess_fg.dll", "core.dll")) {
            Path input = source.resolve(library);
            if (Files.isRegularFile(input)) Files.copy(input, target.resolve(library), StandardCopyOption.REPLACE_EXISTING);
        }
        Path shaders = source.resolve("shaders");
        copyTree(source, target, shaders);
        Path pipelineCache = source.resolve("cache/vulkan/pipelines");
        if (Files.isDirectory(pipelineCache)) { copyTree(source, target, pipelineCache); }
        return target;
    }

    private static void copyTree(Path sourceRoot, Path targetRoot, Path tree) throws IOException {
        try (var paths = Files.walk(tree)) {
            for (Path input : paths.toList()) {
                Path destination = targetRoot.resolve(sourceRoot.relativize(input).toString());
                if (Files.isDirectory(input)) Files.createDirectories(destination);
                else Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String report(Throwable foreignThreadFailure, Throwable postGameEarlyFailure,
                                 int submitResult, int presentResult, int closeResult,
                                 long[] beforeRelease, long[] afterRelease,
                                 int[] initialFramebuffer, int[] resizedFramebuffer, int[] restoredFramebuffer) {
        return """
                {
                  "foreignThreadFailure": "%s",
                  "postGameEarlyFailure": "%s",
                  "submitResult": %d,
                  "presentResult": %d,
                  "closeResult": %d,
                  "identitiesBeforeRelease": "%s",
                  "identitiesAfterRelease": "%s",
                  "initialFramebuffer": "%s",
                  "resizedFramebuffer": "%s",
                  "restoredFramebuffer": "%s"
                }
                """.formatted(describe(foreignThreadFailure), describe(postGameEarlyFailure),
                submitResult, presentResult, closeResult, Arrays.toString(beforeRelease), Arrays.toString(afterRelease),
                Arrays.toString(initialFramebuffer), Arrays.toString(resizedFramebuffer),
                Arrays.toString(restoredFramebuffer));
    }

    private static String describe(Throwable failure) {
        if (failure == null) return "none";
        return (failure.getClass().getName() + ": " + failure.getMessage())
                .replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static final class GameNatives {
        private static native int submit();
        private static native int present();
        private static native int close();
        private static native void setVsync(boolean vsync, boolean write);
        private static native void framebufferSizeChanged();
    }
}
