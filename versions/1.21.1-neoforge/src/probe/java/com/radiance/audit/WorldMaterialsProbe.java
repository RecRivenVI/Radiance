package com.radiance.audit;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.Level;

/** Bounded packaged startup/dimension checks; no desktop input and no product-only dependencies. */
public final class WorldMaterialsProbe {
    private static final String MODE = System.getProperty("radiance.audit.worldMaterials134", "");
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static long previous, maximumGap, ready, transition;
    private static int frames, step;
    private static int menuFrames;
    private static boolean done;
    private static boolean effectiveSettingsChecked;
    private static java.util.concurrent.CompletableFuture<Void> setup;
    private static int scene;
    private static long inkSpawn;
    private static final Map<String, String> savedTone = new LinkedHashMap<>();
    private static final String[][] SURFACES = {
        {"continuous-media", "time set noon", "tp @a 0 66 7 facing 0 66 0"},
        {"glowstone", "tp @a 5 66 1 facing 5 66 -4"},
        {"portal-through-glass", "tp @a 10 66 8 facing 10 64 0"},
        {"fixed-pbr", "tp @a 5 66 1 facing 5 66 -4"},
        {"fixed-aces", "tp @a 5 66 1 facing 5 66 -4"},
        {"dark-room", "time set midnight", "tp @a -4 66 1 facing -5 66 -4"},
        {"ink", "tp @a -4 66 1 facing -5 66 -4"},
        {
            "hand",
            "gamemode creative @a",
            "item replace entity @a weapon.mainhand with minecraft:torch",
            "tp @a 0 66 7 facing 0 65 0"
        },
        {"day-sky", "gamemode spectator @a", "time set noon", "tp @a 0 92 16 facing 0 130 0"},
        {"full-moon", "time set 18000", "tp @a 0 92 16 facing 0 130 0"},
        {"new-moon", "time set 114000", "tp @a 0 92 16 facing 0 130 0"},
        {"rain-sky", "time set noon", "weather rain", "tp @a 0 92 16 facing 0 130 0"},
        {"lower-sun", "weather clear", "time set midnight", "tp @a 0 -96 0 facing 0 -160 0"},
        {"lower-moon", "time set noon", "tp @a 0 -96 0 facing 0 -160 0"}
    };
    private static final String[][] NORMALS = {
        {"diffuse-front", "time set noon", "tp @a 14 74 -9 facing 14 65 -9"},
        {"diffuse-grazing-right", "tp @a 24 65.14 -9 facing 14 65 -9"},
        {"diffuse-grazing-left", "tp @a 10 65.14 -9 facing 20 65 -9"},
        {"metal-front", "tp @a 14 74 -2 facing 14 65 -2"},
        {"metal-grazing-right", "tp @a 24 65.14 -2 facing 14 65 -2"},
        {"metal-grazing-left", "tp @a 10 65.14 -2 facing 20 65 -2"},
        {"continuous-media", "tp @a 0 66 7 facing 0 66 0"}
    };

    private WorldMaterialsProbe() {}

    public static void poll(Minecraft mc) {
        if (MODE.isEmpty() || done) return;
        long now = System.nanoTime();
        if (previous != 0) maximumGap = Math.max(maximumGap, now - previous);
        previous = now;
        try {
            if (MODE.equals("startup")) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                if (!CityFrameComparisonProbe.viewportReady(mc, now)) return;
                if (ready == 0) {
                    ready = now;
                    System.setProperty("radiance.audit.menuInteractive", "true");
                    RESULT.put("interactiveMenuEpochMillis", System.currentTimeMillis());
                    RESULT.put(
                            "processStartedEpochMillis",
                            ProcessHandle.current()
                                    .info()
                                    .startInstant()
                                    .orElseThrow()
                                    .toEpochMilli());
                    com.mojang.logging.LogUtils.getLogger()
                            .info("WORLD_MATERIALS menu-interactive");
                }
                menuFrames++;
                if (now - ready > 8_000_000_000L) {
                    double elapsed = (now - ready) / 1_000_000_000.0;
                    double fps = (menuFrames - 1) / elapsed;
                    RESULT.put("interactiveMenuFrames", menuFrames);
                    RESULT.put("interactiveMenuSeconds", elapsed);
                    RESULT.put("interactiveMenuFps", fps);
                    if (fps > 62.0)
                        throw new IllegalStateException(
                                "Menu exceeded the source 60 FPS limit: " + fps);
                    finish(mc);
                }
                return;
            }
            if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
            if (!CityFrameComparisonProbe.viewportReady(mc, now)) return;
            if (MODE.equals("levitite-stress") && !effectiveSettingsChecked) {
                EffectiveRenderSettings.checkpoint(mc, "levitite-stress");
                effectiveSettingsChecked = true;
            }
            if (MODE.equals("handoff")
                    || MODE.equals("levitite")
                    || MODE.equals("levitite-ponder")
                    || MODE.equals("levitite-stress")) return;
            if (MODE.equals("surfaces") || MODE.equals("normals")) {
                surfaces(mc, now);
                return;
            }
            if (MODE.equals("display")) {
                if (DisplayOutputProbe.poll(mc, now, RESULT)) finish(mc);
                return;
            }
            if (MODE.startsWith("endsea")) {
                if (EndSeaWorldMaterialProbe.poll(mc, now, RESULT)) finish(mc);
                return;
            }
            if (MODE.equals("environment")) {
                environment(mc, now);
                return;
            }
            if (MODE.equals("height")) {
                height(mc, now);
                return;
            }
            if (step == 0) {
                if (++frames < 90) return;
                RESULT.put("overworldBefore", mc.level.dimension().location().toString());
                command(mc, "execute in minecraft:the_end run tp @a 0 80 0 0 0");
                transition = now;
                frames = 0;
                step = 1;
            } else if (step == 1 && mc.level.dimension() == Level.END) {
                if (++frames < 90 || now - transition < 5_000_000_000L) return;
                RESULT.put("endFrames", frames);
                EntityTakeoverProbe.screenshot(mc, "end.png");
                command(mc, "execute in minecraft:overworld run tp @a 0 75 34 0 0");
                transition = now;
                frames = 0;
                step = 2;
            } else if (step == 2 && mc.level.dimension() == Level.OVERWORLD) {
                if (++frames < 90 || now - transition < 3_000_000_000L) return;
                RESULT.put("returnedOverworldFrames", frames);
                EntityTakeoverProbe.screenshot(mc, "returned.png");
                finish(mc);
            }
        } catch (Throwable failure) {
            RESULT.put("failure", failure.toString());
            finish(mc);
            throw new AssertionError("World material functional check failed", failure);
        }
    }

    private static void environment(Minecraft mc, long now) throws Exception {
        String[] names = {
            "clear",
            "blindness",
            "darkness",
            "water",
            "lava",
            "powder_snow",
            "rain",
            "thunder",
            "nether",
            "end",
            "end-below"
        };
        String dimension = scene == 8 ? "the_nether" : scene >= 9 ? "the_end" : "overworld";
        if (transition == 0) {
            command(mc, "gamemode spectator @a");
            command(mc, "effect clear @a");
            command(mc, "time set noon");
            command(mc, "weather clear");
            if (scene == 6 || scene == 7) {
                boolean thunder = scene == 7;
                var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
                server.execute(
                        () -> {
                            var level = server.overworld();
                            level.setWeatherParameters(0, 12000, true, thunder);
                            level.setRainLevel(1);
                            level.setThunderLevel(thunder ? 1 : 0);
                        });
            }
            command(mc, "execute in minecraft:overworld run fill -2 100 -2 2 104 2 air");
            if (scene >= 3 && scene <= 5)
                command(
                        mc,
                        "execute in minecraft:overworld run fill -2 100 -2 2 104 2 "
                                + names[scene]);
            if (scene == 1 || scene == 2)
                command(mc, "effect give @a " + names[scene] + " 30 0 true");
            int y = scene == 8 ? 140 : scene == 10 ? -16 : 101;
            command(mc, "execute in minecraft:" + dimension + " run tp @a 0 " + y + " 0 0 -30");
            transition = now;
            frames = 0;
            return;
        }
        if (!mc.level.dimension().location().getPath().equals(dimension)) return;
        mc.level.setRainLevel(scene == 6 || scene == 7 ? 1 : 0);
        mc.level.setThunderLevel(scene == 7 ? 1 : 0);
        if (now - transition < 3_000_000_000L || ++frames < 60) return;
        float[] color =
                com.radiance.adapter.vanilla.VanillaEnvironment.atmosphere(
                        mc.level, mc.gameRenderer.getMainCamera().getPosition());
        for (float value : color)
            if (!Float.isFinite(value) || value < 0)
                throw new AssertionError("Invalid atmosphere color");
        if (scene == 0) RESULT.put("clearColor", color);
        if (scene > 0 && scene <= 7) {
            float[] clear = (float[]) RESULT.get("clearColor");
            for (int i = 0; i < 3; i++)
                if (Math.abs(clear[i] - color[i]) > 0.00001F)
                    throw new AssertionError(
                            "Camera status contaminated sky tint: " + names[scene]);
        }
        RESULT.put(
                names[scene],
                Map.of(
                        "atmosphere",
                        color,
                        "submersion",
                        mc.gameRenderer.getMainCamera().getFluidInCamera().name(),
                        "rain",
                        mc.level.getRainLevel(1),
                        "thunder",
                        mc.level.getThunderLevel(1)));
        EntityTakeoverProbe.screenshot(mc, "environment-" + names[scene] + ".png");
        transition = 0;
        if (++scene == names.length) finish(mc);
    }

    private static void height(Minecraft mc, long now) throws Exception {
        String[] dimensions = {
            "the_end", "the_end", "the_end", "overworld", "overworld", "the_nether", "the_nether"
        };
        int[] heights = {80, 1, -16, 80, -80, 80, -16};
        if (transition == 0) {
            command(mc, "gamemode spectator @a");
            command(mc, "effect clear @a");
            command(
                    mc,
                    "execute in minecraft:"
                            + dimensions[scene]
                            + " run tp @a 0 "
                            + heights[scene]
                            + " 0 0 -20");
            transition = now;
            frames = 0;
            return;
        }
        if (!mc.level.dimension().location().getPath().equals(dimensions[scene])
                || Math.abs(mc.player.getY() - heights[scene]) > 1
                || now - transition < 3_000_000_000L
                || ++frames < 60) return;
        float[] color =
                com.radiance.adapter.vanilla.VanillaEnvironment.atmosphere(
                        mc.level, mc.gameRenderer.getMainCamera().getPosition());
        RESULT.put(
                "height-" + scene,
                Map.of("dimension", dimensions[scene], "y", mc.player.getY(), "atmosphere", color));
        if (scene == 0) RESULT.put("endReferenceAtmosphere", color);
        else if (scene < 3) {
            float[] reference = (float[]) RESULT.get("endReferenceAtmosphere");
            for (int i = 0; i < 3; i++)
                if (Math.abs(reference[i] - color[i]) > 0.00001F)
                    throw new AssertionError("End atmosphere changed solely with camera height");
        }
        if (scene < 3 && color[0] + color[1] + color[2] <= 0)
            throw new AssertionError("End atmosphere became black");
        EntityTakeoverProbe.screenshot(mc, "height-" + scene + ".png");
        if (++scene == heights.length) finish(mc);
        else transition = 0;
    }

    private static void surfaces(Minecraft mc, long now) throws Exception {
        String[][] scenes = MODE.equals("normals") ? NORMALS : SURFACES;
        if (setup == null) {
            if (MODE.equals("normals")) {
                for (String material : java.util.List.of("yellow", "orange")) {
                    var location =
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                                    "textures/block/" + material + "_concrete_n.png");
                    var resource = mc.getResourceManager().getResourceOrThrow(location);
                    try (var stream = resource.open();
                            var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                        if ((image.getPixelRGBA(0, 0) & 255) != 37
                                || (image.getPixelRGBA(4, 0) & 255) != 128)
                            throw new AssertionError("Controlled labPBR normal map not active");
                    }
                    RESULT.put(material + "NormalResource", resource.sourcePackId());
                }
            }
            var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
            setup =
                    java.util.concurrent.CompletableFuture.runAsync(
                            () -> {
                                for (String c :
                                        java.util.List.of(
                                                "gamerule doMobSpawning false",
                                                "gamerule doDaylightCycle false",
                                                "gamemode spectator @a",
                                                "weather clear",
                                                "kill @e[type=!minecraft:player]",
                                                "fill -23 60 -23 23 73 23 air",
                                                "fill -8 60 -8 8 73 8 stone hollow",
                                                "fill -7 61 -7 7 72 7 air",
                                                "fill -5 64 4 5 69 4 glass",
                                                "fill -5 64 2 5 69 2 glass",
                                                "fill -5 64 -1 5 66 0 water",
                                                "fill -5 64 -5 -1 69 -5 red_concrete",
                                                "fill 1 64 -5 5 69 -5 blue_concrete",
                                                "setblock 5 66 -4 glowstone",
                                                "fill 9 63 -2 11 63 2 white_concrete",
                                                "fill 9 64 -2 11 64 2 end_portal",
                                                "fill 9 65 4 11 68 4 glass",
                                                "summon enderman -5 64 -3 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b}",
                                                "summon spider -3 64 -3 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b}"))
                                    server.getCommands()
                                            .performPrefixedCommand(
                                                    server.createCommandSourceStack()
                                                            .withSuppressedOutput()
                                                            .withPermission(4),
                                                    c);
                                if (MODE.equals("normals"))
                                    for (String c :
                                            java.util.List.of(
                                                    "fill 9 64 -12 23 64 -7 yellow_concrete",
                                                    "fill 9 64 -5 23 64 0 orange_concrete"))
                                        server.getCommands()
                                                .performPrefixedCommand(
                                                        server.createCommandSourceStack()
                                                                .withSuppressedOutput()
                                                                .withPermission(4),
                                                        c);
                            },
                            server);
            transition = now;
            return;
        }
        if (!setup.isDone()) return;
        setup.join();
        if (step == 0) {
            String name = scenes[scene][0];
            if (name.startsWith("fixed-")) tone(name.equals("fixed-aces") ? "aces" : "pbr_neutral");
            else if (!savedTone.isEmpty()) restoreTone();
            for (int i = 1; i < scenes[scene].length; i++) command(mc, scenes[scene][i]);
            transition = now;
            frames = 0;
            step = 1;
            return;
        }
        if (scenes[scene][0].equals("hand") && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        if (scenes[scene][0].equals("ink")
                && now - transition > 3_500_000_000L
                && now - inkSpawn > 500_000_000L) {
            inkSpawn = now;
            for (int i = 0; i < 12; i++)
                mc.level.addParticle(
                        net.minecraft.core.particles.ParticleTypes.GLOW_SQUID_INK,
                        -4.5 + i * .025,
                        66,
                        -1,
                        0,
                        0,
                        0);
        }
        if (now - transition < 4_000_000_000L || ++frames < 45) return;
        EntityTakeoverProbe.screenshot(mc, scenes[scene][0] + ".png");
        RESULT.put(
                scenes[scene][0],
                Map.of("frames", frames, "dimension", mc.level.dimension().location().toString()));
        if (++scene == scenes.length) finish(mc);
        else step = 0;
    }

    private static void tone(String method) {
        for (var module : com.radiance.client.pipeline.Pipeline.INSTANCE.getModules()) {
            if (module.attributeConfigs == null) continue;
            for (var attribute : module.attributeConfigs) {
                String name = attribute.name;
                if (!name.startsWith("render_pipeline.module.tone_mapping.attribute.")) continue;
                savedTone.putIfAbsent(name, attribute.value);
                if (name.endsWith("enable_auto_exposure"))
                    attribute.value = "render_pipeline.false";
                else if (name.endsWith("manual_exposure")) attribute.value = "1.0";
                else if (name.endsWith("method")) attribute.value = name + "." + method;
            }
        }
        com.radiance.client.pipeline.Pipeline.build();
    }

    private static void restoreTone() {
        for (var module : com.radiance.client.pipeline.Pipeline.INSTANCE.getModules())
            if (module.attributeConfigs != null)
                for (var attribute : module.attributeConfigs)
                    if (savedTone.containsKey(attribute.name))
                        attribute.value = savedTone.get(attribute.name);
        savedTone.clear();
        com.radiance.client.pipeline.Pipeline.build();
    }

    private static void command(Minecraft mc, String command) {
        var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
        server.execute(
                () ->
                        server.getCommands()
                                .performPrefixedCommand(
                                        server.createCommandSourceStack()
                                                .withSuppressedOutput()
                                                .withPermission(4),
                                        command));
    }

    private static void finish(Minecraft mc) {
        done = true;
        RESULT.put("mode", MODE);
        RESULT.put(
                "viewport",
                Map.of("width", mc.getWindow().getWidth(), "height", mc.getWindow().getHeight()));
        RESULT.put("maximumObservedFrameGapMillis", maximumGap / 1_000_000.0);
        RESULT.put("passed", !RESULT.containsKey("failure"));
        try {
            Path destination =
                    Path.of(System.getProperty("validation.result"), "world-materials.json");
            Files.createDirectories(destination.getParent());
            Files.writeString(
                    destination,
                    new GsonBuilder().setPrettyPrinting().create().toJson(RESULT) + "\n");
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        com.mojang.logging.LogUtils.getLogger().info("WORLD_MATERIALS complete {}", MODE);
        mc.stop();
    }
}
