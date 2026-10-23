package com.radiance.audit;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Drives real GUI producers without desktop input. State samples are not a performance test. */
final class GuiReviewProbe {
    static final boolean ENABLED = Boolean.getBoolean("radiance.audit.guiReview");
    private static final boolean POLICY = Boolean.getBoolean("radiance.audit.guiPolicy");
    private static final boolean NATIVE_ONLY = Boolean.getBoolean("radiance.audit.guiNativeOnly");
    private static final List<Map<String, Object>> samples = new ArrayList<>();
    private static int phase, positiveGeneratedSamples;
    private static long since, worldTime, pausedTime = -1;
    private static boolean previousFg;
    private static boolean captureRequested;
    private static int previousBlur;
    private static int policyCapturePhase = -1;

    private GuiReviewProbe() {}

    static void register() {
        if (ENABLED) NeoForge.EVENT_BUS.addListener(GuiReviewProbe::tooltip);
    }

    private static void tooltip(ScreenEvent.Render.Post event) {
        var mc = Minecraft.getInstance();
        if (phase == 2 && event.getScreen() instanceof InventoryScreen) {
            // Exercise vanilla tooltip drawing without moving the host cursor.
            var g = event.getGuiGraphics();
            g.renderTooltip(
                    mc.font,
                    mc.player.getInventory().getItem(0),
                    g.guiWidth() / 2 + 100,
                    g.guiHeight() / 2);
        }
    }

    static void start(Minecraft mc, long now) {
        previousFg = Options.dlssFrameGeneration;
        previousBlur = mc.options.menuBackgroundBlurriness().get();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = false;
        mc.options.menuBackgroundBlurriness().set(4);
        mc.setScreen(null);
        DrawingCensusProbe.command(mc, "gamemode survival @a");
        DrawingCensusProbe.command(mc, "fill -8 64 0 0 78 0 black_concrete");
        DrawingCensusProbe.command(mc, "fill 1 64 0 8 78 0 white_concrete");
        DrawingCensusProbe.command(mc, "tp @a -4.5 68 10.5 180 0");
        DrawingCensusProbe.command(mc, "item replace entity @a hotbar.0 with diamond_sword");
        DrawingCensusProbe.command(mc, "item replace entity @a hotbar.1 with apple");
        DrawingCensusProbe.command(mc, "item replace entity @a hotbar.2 with chest");
        Options.setDlssModels(Options.dlssSrModel, Options.dlssRrModel, 0, !NATIVE_ONLY, false);
        since = now;
        DrawingCensus.INSTANCE.window("R5-gui-review");
    }

    static boolean poll(Minecraft mc, long now) throws java.io.IOException {
        if ((RendererProxy.presentationRates() & 0xffffffffL) != 0) positiveGeneratedSamples++;
        long elapsed = now - since;
        if (phase == 0 && elapsed > 3_000_000_000L && !captureRequested) {
            Files.writeString(
                    mc.gameDirectory.toPath().resolve("radiance-fg-capture.request"),
                    "gui-dark-crosshair\n");
            captureRequested = true;
        }
        if (phase == 1 && elapsed < 3_000_000_000L) {
            mc.player.setYRot(180 + (float) Math.sin(elapsed / 400_000_000.0) * 8);
        }
        if (POLICY
                && (phase == 2 || phase == 5)
                && elapsed > 3_000_000_000L
                && policyCapturePhase != phase) {
            Files.writeString(
                    mc.gameDirectory.toPath().resolve("radiance-fg-capture.request"),
                    "gui-policy-" + phase + "\n");
            policyCapturePhase = phase;
        }
        if ((phase == 4 || POLICY && phase == 5)
                && mc.isPaused()
                && elapsed > 1_000_000_000L
                && pausedTime < 0) pausedTime = mc.level.getGameTime();
        if (elapsed < 5_000_000_000L) return false;
        switch (phase) {
            case 0 -> {
                sample(mc, "hud-dark");
                DrawingCensusProbe.command(mc, "tp @a 4.5 68 10.5 180 0");
            }
            case 1 -> {
                sample(mc, "hud-light-moving");
                mc.setScreen(new InventoryScreen(mc.player));
                worldTime = mc.level.getGameTime();
            }
            case 2 -> {
                require(
                        !mc.isPaused() && mc.level.getGameTime() > worldTime,
                        "Inventory must keep world updating");
                sample(mc, "inventory");
                mc.setScreen(new LiveMenu());
                worldTime = mc.level.getGameTime();
            }
            case 3 -> {
                require(
                        !mc.isPaused() && mc.level.getGameTime() > worldTime,
                        "Non-pausing menu froze world");
                sample(mc, "live-menu-blur");
                mc.setScreen(new PauseScreen(true));
            }
            case 4 -> {
                require(
                        mc.isPaused() && pausedTime >= 0 && mc.level.getGameTime() == pausedTime,
                        "PauseScreen did not actually pause the integrated world");
                sample(mc, "paused-menu-blur");
                if (POLICY) {
                    mc.options.menuBackgroundBlurriness().set(0);
                    pausedTime = -1;
                }
            }
            case 5 -> {
                if (POLICY) {
                    require(
                            mc.isPaused()
                                    && pausedTime >= 0
                                    && mc.level.getGameTime() == pausedTime,
                            "No-blur pause advanced the integrated world");
                    sample(mc, "paused-menu-no-blur");
                    mc.setScreen(new InventoryScreen(mc.player));
                    worldTime = mc.level.getGameTime();
                } else mc.setScreen(null);
            }
            case 6 -> {
                if (POLICY) {
                    require(
                            !mc.isPaused() && mc.level.getGameTime() > worldTime,
                            "Inventory after pause failed to resume simulation");
                    sample(mc, "inventory-after-pause");
                    mc.setScreen(null);
                    break;
                }
                return finish(mc);
            }
            case 7 -> {
                return finish(mc);
            }
            default -> throw new IllegalStateException("GUI review phase");
        }
        phase++;
        com.mojang.logging.LogUtils.getLogger().info("GUI_REVIEW_PHASE phase={}", phase);
        since = now;
        return false;
    }

    private static boolean finish(Minecraft mc) throws java.io.IOException {
        require(!mc.isPaused(), "Closing pause screen did not resume");
        sample(mc, "restored-hud");
        Options.setDlssModels(Options.dlssSrModel, Options.dlssRrModel, 0, previousFg, false);
        mc.options.menuBackgroundBlurriness().set(previousBlur);
        write();
        return true;
    }

    static void afterReload(Minecraft mc) throws java.io.IOException {
        OffscreenDependencyProbe.verify("gui-review-after-reload");
        sample(mc, "after-reload");
        write();
        com.mojang.logging.LogUtils.getLogger()
                .info("GUI_REVIEW PASS generated-positive-samples={}", positiveGeneratedSamples);
    }

    private static void sample(Minecraft mc, String name) throws java.io.IOException {
        com.mojang.logging.LogUtils.getLogger()
                .info("GUI_REVIEW_SAMPLE name={} phase={}", name, phase);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty("validation.result"), name + ".png"));
        }
        if (name.equals("hud-dark")) {
            try (var world = RendererProxy.takeScreenshotWithoutUI()) {
                world.writeToFile(
                        Path.of(System.getProperty("validation.result"), "world-without-gui.png"));
            }
        }
        var row = new LinkedHashMap<String, Object>();
        row.put("name", name);
        row.put("screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
        row.put("paused", mc.isPaused());
        row.put("worldGameTime", mc.level.getGameTime());
        row.put("fgRequested", Options.dlssFrameGeneration);
        row.put("fgAvailable", Options.nativeIsDlssFrameGenerationAvailable());
        row.put(
                "windowVisible",
                GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_VISIBLE) != 0);
        row.put(
                "windowIconified",
                GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_ICONIFIED) != 0);
        row.put("tooltipViaVanillaDrawApi", name.equals("inventory"));
        row.put(
                "actualGlfwFocused",
                GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_FOCUSED) != 0);
        row.put("generatedRatePositive", (RendererProxy.presentationRates() & 0xffffffffL) != 0);
        samples.add(row);
    }

    private static void write() throws java.io.IOException {
        var report =
                Map.of(
                        "samples",
                        samples,
                        "generatedPositiveSamples",
                        positiveGeneratedSamples,
                        "generatedPixelsInspected",
                        false,
                        "performanceMeasured",
                        false);
        Files.writeString(
                Path.of(System.getProperty("validation.result"), "gui-review.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)
                        + "\n");
    }

    private static void require(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
    }

    private static final class LiveMenu extends Screen {
        LiveMenu() {
            super(Component.literal("GUI review: world keeps updating"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, float delta) {
            renderBackground(graphics, x, y, delta);
            graphics.drawCenteredString(font, title, width / 2, height / 2, -1);
            super.render(graphics, x, y, delta);
        }
    }
}
