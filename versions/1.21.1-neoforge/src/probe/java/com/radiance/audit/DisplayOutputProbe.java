package com.radiance.audit;

import com.radiance.client.gui.DisplaySettingsScreen;
import com.radiance.client.option.Options;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Owned display/material fixture; captures are encoding evidence, never HDR visual acceptance. */
final class DisplayOutputProbe {
    private static CompletableFuture<Void> setup;
    private static int scene = Boolean.getBoolean("radiance.audit.displayControlsOnly") ? 9 : 0;
    private static long changed;
    private static boolean requested;
    private static final List<Map<String, Object>> observations = new ArrayList<>();
    private static final String[] NAMES = {
        "sdr-no-bloom",
        "sdr-bloom",
        "auto-hdr-bloom",
        "glass",
        "outside-day",
        "outside-night",
        "inventory",
        "display-settings",
        "reload",
        "hdr-luminance-update",
        "return-sdr",
        "return-hdr",
        "system-ui-white",
        "automatic-display-settings"
    };

    static boolean poll(Minecraft mc, long now, Map<String, Object> result) throws Exception {
        if (setup == null) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    DisplayOutputProbe::referencePatch);

            var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
            setup =
                    CompletableFuture.runAsync(
                            () -> {
                                for (String command :
                                        List.of(
                                                "gamerule doMobSpawning false",
                                                "gamerule doDaylightCycle false",
                                                "weather clear",
                                                "time set noon",
                                                "gamemode creative @a",
                                                "fill -12 63 -12 12 76 12 white_concrete hollow",
                                                "fill -11 64 -11 11 75 11 air",
                                                "fill -3 69 -8 3 69 -8 sea_lantern",
                                                "fill -3 67 -4 3 71 -4 glass",
                                                "fill -7 65 -10 7 73 -10 iron_block",
                                                "tp @a 0 68 -6 facing 0 69 -8"))
                                    server.getCommands()
                                            .performPrefixedCommand(
                                                    server.createCommandSourceStack()
                                                            .withPermission(4)
                                                            .withSuppressedOutput(),
                                                    command);
                            },
                            server);
            return false;
        }
        if (!setup.isDone()) return false;
        setup.join();
        if (changed == 0) {
            Options.setDisplaySettings(scene >= 2, 200, 160, 0, scene != 0, 0.03f, false);
            if (scene == 3) command(mc, "tp @a 0 68 1 facing 0 69 -8");
            if (scene == 4) {
                command(mc, "time set noon");
                command(mc, "tp @a 0 80 8 facing 0 68 -8");
            }
            if (scene == 5) command(mc, "time set midnight");
            if (scene == 6) mc.setScreen(new InventoryScreen(mc.player));
            if (scene == 7) mc.setScreen(new DisplaySettingsScreen(null));
            if (scene == 8) {
                mc.setScreen(null);
                mc.reloadResourcePacks();
            }
            if (scene == 9) {
                System.out.println("DISPLAY_LUMINANCE_UPDATE begin");
                Options.setDisplaySettings(true, 180, 120, 800, true, 0.03f, false);
            }
            if (scene == 10) Options.setDisplaySettings(false, 200, 160, 0, true, 0.03f, false);
            if (scene == 12) Options.setDisplaySettings(true, 200, 0, 0, true, 0.03f, false);
            if (scene == 13) {
                Options.setDisplaySettings(true, 200, 0, 0, true, 0.03f, false);
                mc.setScreen(new DisplaySettingsScreen(null));
            }
            changed = now;
            requested = false;
            return false;
        }
        if (mc.getOverlay() != null || now - changed < 5_000_000_000L) return false;
        if ((scene < 3 || scene == 9 || scene == 12) && !requested) {
            Files.writeString(
                    mc.gameDirectory.toPath().resolve("radiance-display-capture.request"),
                    NAMES[scene] + "\n");
            requested = true;
            return false;
        }
        if ((scene < 3 || scene == 9 || scene == 12) && now - changed < 7_000_000_000L)
            return false;
        if (scene == 9) System.out.println("DISPLAY_LUMINANCE_UPDATE end");
        String status = Options.nativeDisplayStatus();
        if ((scene < 2 || scene == 10) && !status.equals("SDR"))
            throw new AssertionError("HDR remained active after explicit SDR selection: " + status);
        if (scene == 8 && !Options.hdrEnabled)
            throw new AssertionError("Reload lost the display option");
        EntityTakeoverProbe.screenshot(mc, "display-" + NAMES[scene] + ".png");
        observations.add(
                Map.of(
                        "scene",
                        NAMES[scene],
                        "output",
                        status,
                        "hdrRequested",
                        Options.hdrEnabled,
                        "bloom",
                        Options.bloomEnabled,
                        "uiWhite",
                        Options.uiWhiteNits));
        if (++scene == NAMES.length) {
            result.put("displayScenes", observations);
            result.put("referencePatchScale", mc.getWindow().getGuiScale());
            result.put("hdrVisualAcceptance", "pending-real-display");
            return true;
        }
        changed = 0;
        return false;
    }

    private static void referencePatch(
            net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        if (scene >= 3 && scene != 9 && scene != 12) return;
        event.getGuiGraphics().fill(8, 8, 24, 24, 0xffffffff);
        event.getGuiGraphics().fill(26, 8, 42, 24, 0xff808080);
    }

    private static void command(Minecraft mc, String command) {
        var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
        server.execute(
                () ->
                        server.getCommands()
                                .performPrefixedCommand(
                                        server.createCommandSourceStack()
                                                .withPermission(4)
                                                .withSuppressedOutput(),
                                        command));
    }
}
