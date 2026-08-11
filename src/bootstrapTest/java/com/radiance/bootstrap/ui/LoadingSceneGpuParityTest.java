package com.radiance.bootstrap.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;

import com.radiance.bootstrap.NativeRuntime;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import net.neoforged.fml.earlydisplay.OfficialGlReference;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Hardware integration harness. Enable with RADIANCE_PARITY_RUNTIME in a dedicated test JVM. */
class LoadingSceneGpuParityTest {
    private static final int BASE_WIDTH = 854;
    private static final int BASE_HEIGHT = 480;
    private static final int MOJANG_KEY = 41;

    @Test
    void officialOpenGlAndVulkanCanvasArePixelExact() throws Exception {
        String runtimeValue = System.getenv("RADIANCE_PARITY_RUNTIME");
        assumeTrue(runtimeValue != null && !runtimeValue.isBlank(), "Set RADIANCE_PARITY_RUNTIME to run GPU parity");
        Path runtimeSource = Path.of(runtimeValue).toAbsolutePath().normalize();
        Path output = Path.of(System.getenv().getOrDefault("RADIANCE_PARITY_OUTPUT",
                "D:/Workspaces/Artifacts/Radiance-SPI-Loading-20260914/parity")).toAbsolutePath().normalize();
        boolean includeMojang = !"false".equalsIgnoreCase(System.getenv("RADIANCE_PARITY_MOJANG"));
        boolean includeSquirrel = "true".equalsIgnoreCase(System.getenv("RADIANCE_PARITY_SQUIRREL"));
        boolean includeProgress = "true".equalsIgnoreCase(System.getenv("RADIANCE_PARITY_PROGRESS"));
        int scale = Integer.parseInt(System.getenv().getOrDefault("RADIANCE_PARITY_SCALE", "1"));
        int width = BASE_WIDTH * scale;
        int height = BASE_HEIGHT * scale;
        Files.createDirectories(output);
        Path runtime = copyRuntime(runtimeSource, output.resolve("runtime-" + ProcessHandle.current().pid()));
        assertTrue(Files.isRegularFile(runtime.resolve("core.dll")), "Missing core.dll in " + runtime);
        assertTrue(Files.isRegularFile(runtime.resolve("libxess.dll")), "Missing libxess.dll in " + runtime);

        long glWindow = 0;
        long vkWindow = 0;
        boolean nativeInitialized = false;
        assertTrue(glfwInit(), "GLFW initialization failed");
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            glWindow = glfwCreateWindow(width, height, "FML 4.0.43 GL reference", 0, 0);
            assertNotEquals(0, glWindow, "Unable to create OpenGL reference window");
            glfwMakeContextCurrent(glWindow);
            GL.createCapabilities();

            ByteBuffer mojang = checkerTexture();
            int frame = Integer.parseInt(System.getenv().getOrDefault("RADIANCE_PARITY_FRAME", "12"));
            int alpha = Integer.parseInt(System.getenv().getOrDefault("RADIANCE_PARITY_ALPHA", "255"));
            float memory = 0.25f;
            String performanceText = "Memory";
            String version = "1.21.1-21.1.248";
            byte[] glPixels = OfficialGlReference.render(width, height, scale,
                    net.neoforged.fml.earlydisplay.ColourScheme.RED, version, frame, alpha,
                    memory, performanceText, includeMojang ? mojang.duplicate() : null, 4, 2, 10,
                    includeSquirrel, includeProgress);
            glfwMakeContextCurrent(0);

            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            vkWindow = glfwCreateWindow(width, height, "Radiance Vulkan reference", 0, 0);
            assertNotEquals(0, vkWindow, "Unable to create Vulkan reference window");

            System.load(runtime.resolve("libxess.dll").toString());
            System.load(runtime.resolve("core.dll").toString());
            NativeRuntime.initialize(runtime.toString(),
                    new String[] { System.mapLibraryName("glfw"), "glfw3.dll" }, vkWindow);
            nativeInitialized = true;

            byte[] vkPixels;
            try (LoadingScene scene = new LoadingScene(scale, ColourScheme.RED, "1.21.1", "21.1.248",
                    includeSquirrel, NativeRuntime::uploadTexture)) {
                if (includeMojang) {
                NativeRuntime.uploadTexture(MOJANG_KEY, 4, 2, mojang.duplicate(), true, false);
                    scene.addMojangTexture(MOJANG_KEY, 10);
                }
                LoadingScene.Frame sceneFrame = scene.render(new LoadingScene.FrameInput(frame, alpha,
                        List.of(), includeProgress ? List.of(new LoadingScene.ProgressBar("Step", 10, 0.3f)) : List.of(),
                        new LoadingScene.PerformanceSnapshot(memory, performanceText)));
                NativeRuntime.renderFrame(sceneFrame.vertices(), sceneFrame.batches(), sceneFrame.batchCount(),
                        sceneFrame.canvasWidth(), sceneFrame.canvasHeight(), sceneFrame.backgroundAbgr(),
                        width, height, 1.0f, false, true);
                vkPixels = NativeRuntime.captureCanvas();
            }

            assertEquals(glPixels.length, vkPixels.length, "Canvas byte length differs");
            Diff diff = compare(glPixels, vkPixels);
            writePng(output.resolve("official-gl.png"), glPixels, width, height);
            writePng(output.resolve("radiance-vulkan.png"), vkPixels, width, height);
            writePng(output.resolve("pixel-diff.png"), diff.visualization, width, height);
            String sceneName = (includeMojang ? "mojang+" : "") + (includeSquirrel ? "squirrel+" : "")
                    + "fox+version+performance" + (includeProgress ? "+progress" : "");
            String report = """
                    {
                      "scene": "%s",
                      "frame": %d,
                      "alpha": %d,
                      "scale": %d,
                      "canvas": [%d, %d],
                      "officialEarlyDisplay": "%s",
                      "fontSha256": "%s",
                      "foxSha256": "%s",
                      "diffPixels": %d,
                      "maxChannelDiff": %d
                    }
                    """.formatted(sceneName, frame, alpha, scale, width, height, OfficialGlReference.rendererSourceLocation(),
                    resourceSha256("/Monocraft.ttf"), resourceSha256("/fox_running.png"),
                    diff.differentPixels, diff.maxChannelDifference);
            Files.writeString(output.resolve("parity-report.json"), report);
            assertEquals(0, diff.differentPixels,
                    "GPU canvases differ; see " + output.resolve("parity-report.json"));
            assertEquals(0, diff.maxChannelDifference);
        } finally {
            if (nativeInitialized) NativeRuntime.closeBeforeGame();
            glfwMakeContextCurrent(0);
            if (vkWindow != 0) glfwDestroyWindow(vkWindow);
            if (glWindow != 0) glfwDestroyWindow(glWindow);
            glfwTerminate();
        }
    }

    private static ByteBuffer checkerTexture() {
        ByteBuffer rgba = BufferUtils.createByteBuffer(4 * 2 * 4).order(ByteOrder.nativeOrder());
        int[] colours = {
                0xff0000ff, 0xff00ff00, 0xffff0000, 0xffffffff,
                0xff00ffff, 0xffff00ff, 0xffffff00, 0xff202020
        };
        for (int colour : colours) rgba.putInt(colour);
        return rgba.flip();
    }

    private static Path copyRuntime(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        for (String library : List.of("libxess.dll", "libxess_dx11.dll", "libxess_fg.dll", "core.dll")) {
            Path input = source.resolve(library);
            if (Files.isRegularFile(input)) Files.copy(input, target.resolve(library), StandardCopyOption.REPLACE_EXISTING);
        }
        Path shaders = source.resolve("shaders");
        try (var paths = Files.walk(shaders)) {
            for (Path input : paths.toList()) {
                Path destination = target.resolve(source.relativize(input).toString());
                if (Files.isDirectory(input)) Files.createDirectories(destination);
                else Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    private static Diff compare(byte[] expected, byte[] actual) {
        byte[] visualization = new byte[expected.length];
        int differentPixels = 0;
        int maxChannelDifference = 0;
        for (int pixel = 0; pixel < expected.length / 4; pixel++) {
            boolean different = false;
            int base = pixel * 4;
            for (int channel = 0; channel < 4; channel++) {
                int delta = Math.abs(Byte.toUnsignedInt(expected[base + channel]) - Byte.toUnsignedInt(actual[base + channel]));
                maxChannelDifference = Math.max(maxChannelDifference, delta);
                different |= delta != 0;
            }
            if (different) {
                differentPixels++;
                visualization[base] = (byte) 0xff;
                visualization[base + 1] = 0;
                visualization[base + 2] = (byte) 0xff;
                visualization[base + 3] = (byte) 0xff;
            } else {
                visualization[base + 3] = (byte) 0xff;
            }
        }
        return new Diff(differentPixels, maxChannelDifference, visualization);
    }

    private static void writePng(Path path, byte[] rgba, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 4;
                int argb = Byte.toUnsignedInt(rgba[offset + 3]) << 24
                        | Byte.toUnsignedInt(rgba[offset]) << 16
                        | Byte.toUnsignedInt(rgba[offset + 1]) << 8
                        | Byte.toUnsignedInt(rgba[offset + 2]);
                image.setRGB(x, y, argb);
            }
        }
        if (!ImageIO.write(image, "PNG", path.toFile())) throw new IOException("PNG writer unavailable");
    }

    private static String resourceSha256(String name) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = OfficialGlReference.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing official resource " + name);
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record Diff(int differentPixels, int maxChannelDifference, byte[] visualization) {}
}
