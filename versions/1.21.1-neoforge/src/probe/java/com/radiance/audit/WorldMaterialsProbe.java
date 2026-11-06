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
    private static boolean snapshotPending;
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
    private static final String[][] LIGHTS = {
        {
            "light-origin",
            "time set midnight",
            "fill -4 63 -7 4 70 -7 white_concrete",
            "setblock 5 66 -4 air",
            "setblock 0 67 -6 sea_lantern",
            "tp @a 0 67 0 facing 0.5 67.5 -5.5"
        },
        {"light-moved", "setblock 0 67 -6 air", "setblock 2 67 -6 sea_lantern"}
    };

    private static final String[][] MEDIA = {
        {"water-entry", "time set noon", "tp @a 0 71 2 facing 0 64 0"},
        {"water-exit-normal", "tp @a 0 65.2 0 0 -90"},
        {"water-exit-tir", "tp @a 0 65.2 0 0 -25"},
        {"water-camera-bottom", "tp @a 0 65.2 0 0 85"},
        {"water-camera-entity", "tp @a -3 65.2 -3 facing 0 66 0"},
        {"water-glass", "tp @a 5 65.2 0 facing 12 67 0"},
        {"water-glass-grazing", "tp @a 5 65.2 -4 facing 7 66.82 5"},
        {"glass-camera-opaque", "tp @a 13.5 65 11.5 facing 13.5 66.62 13"},
        {"pane-camera-air", "tp @a 20.1375 65.0625 0.3 facing 24 66.6825 0.3"},
        {"pane-camera-inside", "tp @a 20.5 65.0625 0.5 facing 24 66.6825 0.5"},
        {"red-blue-contact", "tp @a 10 65 -2 facing 18 66.62 -2"},
        {"blue-red-contact", "tp @a 15.5 65 -2 facing 9 66.62 -2"},
        {"grazing-glass", "tp @a 8.2 65 -5 facing 7 66.62 4"},
        {"dynamic-isolated", "tp @a 25 66 10.5 facing 29 67.62 10.5"},
        {"dynamic-internal-contact", "tp @a 25 64 10.5 facing 30 65.62 10.5"},
        {"dynamic-approach", "tp @a 27 64 10.5 facing 33 65.62 10.5"},
        {"dynamic-separate", "tp @a 27 64 10.5 facing 33 65.62 10.5"},
        {"dynamic-real-gap", "tp @a 27 64 10.5 facing 33 65.62 10.5"},
        {"cross-section-contact", "tp @a 28 65.1 18 facing 35 66.72 18"},
        {"partial-pane-water", "tp @a 37 64.9 18.5 facing 42 66.52 18.5"}
    };

    private WorldMaterialsProbe() {}

    public static void poll(Minecraft mc) {
        if (MODE.isEmpty() || done) return;
        long now = System.nanoTime();
        if (previous != 0) maximumGap = Math.max(maximumGap, now - previous);
        previous = now;
        try {
            if (snapshotPending) {
                finish(mc);
                return;
            }
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
            if (MODE.equals("scene-store") || MODE.equals("scene-store-auxiliary")) {
                if (SceneStoreProbe.poll(mc, RESULT)) finish(mc);
                return;
            }
            if (MODE.equals("loopback")) {
                if (mc.getSingleplayerServer() != null)
                    throw new IllegalStateException("Loopback check entered an integrated server");
                if (++frames >= 30) finish(mc);
                return;
            }
            if (MODE.startsWith("deep-")) {
                depthFixture(mc, now);
                return;
            }
            if (MODE.equals("levitite-stress") && !effectiveSettingsChecked) {
                EffectiveRenderSettings.checkpoint(mc, "levitite-stress");
                effectiveSettingsChecked = true;
            }
            if (MODE.equals("handoff")
                    || MODE.equals("levitite")
                    || MODE.equals("levitite-ponder")
                    || MODE.equals("levitite-stress")) return;
            if (MODE.equals("surfaces")
                    || MODE.equals("normals")
                    || MODE.equals("media")
                    || MODE.equals("lights")) {
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
        String[][] scenes =
                switch (MODE) {
                    case "media" -> MEDIA;
                    case "normals" -> NORMALS;
                    case "lights" -> LIGHTS;
                    default -> SURFACES;
                };
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
                                if (MODE.equals("media"))
                                    for (String c :
                                            java.util.List.of(
                                                    "kill @e[type=!minecraft:player]",
                                                    "fill -7 63 -7 18 72 14 air",
                                                    "fill -6 63 -6 6 63 6 white_concrete",
                                                    "fill -6 64 -6 6 68 6 water",
                                                    "fill 7 64 -6 7 69 6 cyan_stained_glass",
                                                    "fill 8 63 -6 18 63 14 white_concrete",
                                                    "fill 12 64 10 14 68 12 red_stained_glass",
                                                    "setblock 13 66 12 white_concrete",
                                                    "setblock 20 66 0 lime_stained_glass_pane[east=true,north=true,south=true,west=false]",
                                                    "fill 12 64 -5 12 69 5 red_stained_glass",
                                                    "fill 13 64 -5 13 69 5 blue_stained_glass",
                                                    "fill 16 64 -5 16 69 5 yellow_concrete",
                                                    "setblock 11 68 0 sea_lantern",
                                                    "fill 25 64 8 42 64 20 white_concrete",
                                                    "fill 32 65 10 32 65 10 blue_stained_glass",
                                                    "fill 30 65 17 31 68 19 glass",
                                                    "fill 32 65 17 33 68 19 blue_stained_glass",
                                                    "setblock 39 66 18 glass_pane[east=true,west=true,north=false,south=false]",
                                                    "fill 40 65 18 41 66 18 water",
                                                    "summon spider 0 65.5 0 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b}"))
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
        if (MODE.equals("media")) MediumContactFixture.select(scenes[scene][0]);
        if (MODE.equals("media") && !RESULT.containsKey("productionFluid")) {
            if (!mc.level.getFluidState(new net.minecraft.core.BlockPos(2, 68, 2)).isSource())
                return;
            var plain = mc.level.getBlockState(new net.minecraft.core.BlockPos(31, 66, 18));
            var coloured = mc.level.getBlockState(new net.minecraft.core.BlockPos(32, 66, 18));
            if (!plain.is(net.minecraft.world.level.block.Blocks.GLASS)
                    || !coloured.is(net.minecraft.world.level.block.Blocks.BLUE_STAINED_GLASS))
                return;
            RESULT.put(
                    "productionFluid",
                    FluidBoundaryProbe.capture(mc, new net.minecraft.core.BlockPos(2, 68, 2)));
            RESULT.put(
                    "productionFluidContact",
                    FluidBoundaryProbe.captureContact(
                            mc, new net.minecraft.core.BlockPos(6, 67, 0)));
            var plainLayer =
                    net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(plain);
            var colouredLayer =
                    net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(coloured);
            if (plainLayer == colouredLayer)
                throw new AssertionError("Contact fixture must cross actual source render layers");
            RESULT.put(
                    "productionCrossLayer",
                    Map.of(
                            "plain",
                            plain.toString(),
                            "coloured",
                            coloured.toString(),
                            "plainLayer",
                            plainLayer.toString(),
                            "colouredLayer",
                            colouredLayer.toString()));
        }
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
        if (MODE.equals("media")) {
            var camera = mc.gameRenderer.getMainCamera();
            var position = camera.getPosition();
            var cameraState =
                    mc.level.getBlockState(net.minecraft.core.BlockPos.containing(position));
            String name = scenes[scene][0];
            if ((name.startsWith("water-exit")
                            || name.startsWith("water-camera")
                            || name.startsWith("water-glass"))
                    && camera.getFluidInCamera()
                            != net.minecraft.world.level.material.FogType.WATER)
                throw new AssertionError("Medium camera not actually submerged: " + name);
            if (name.equals("glass-camera-opaque")
                    && !cameraState.is(net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS))
                throw new AssertionError("Medium camera not actually inside coloured glass");
            boolean hint = com.radiance.adapter.vanilla.CameraMediumHint.needed(mc, position);
            if ((name.equals("pane-camera-air") && hint)
                    || ((name.equals("pane-camera-inside") || name.equals("glass-camera-opaque"))
                            && !hint))
                throw new AssertionError(
                        "Camera medium admission disagrees with baked geometry: " + name);
            RESULT.put(
                    name + "-camera",
                    Map.of(
                            "position",
                            java.util.List.of(position.x, position.y, position.z),
                            "block",
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                    .getKey(cameraState.getBlock())
                                    .toString(),
                            "fluid",
                            camera.getFluidInCamera().name(),
                            "mediumProbe",
                            hint));
        }
        EntityTakeoverProbe.screenshot(mc, scenes[scene][0] + ".png");
        RESULT.put(
                scenes[scene][0],
                Map.of("frames", frames, "dimension", mc.level.dimension().location().toString()));
        if (++scene == scenes.length) finish(mc);
        else step = 0;
    }

    private static void depthFixture(Minecraft mc, long now) throws Exception {
        boolean refract = MODE.equals("deep-refraction");
        if (setup == null) {
            var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
            setup =
                    java.util.concurrent.CompletableFuture.runAsync(
                            () -> {
                                var source =
                                        server.createCommandSourceStack()
                                                .withSuppressedOutput()
                                                .withPermission(4);
                                var commands = server.getCommands();
                                commands.performPrefixedCommand(source, "gamemode spectator @a");
                                commands.performPrefixedCommand(
                                        source, "gamerule doDaylightCycle false");
                                commands.performPrefixedCommand(source, "time set noon");
                                commands.performPrefixedCommand(source, "weather clear");
                                commands.performPrefixedCommand(source, "fill 0 -63 0 8 205 8 air");
                                if (refract) {
                                    for (int y = -60; y < 196; y++) {
                                        String block =
                                                (y & 1) == 0
                                                        ? "yellow_stained_glass"
                                                        : "lime_stained_glass";
                                        commands.performPrefixedCommand(
                                                source,
                                                "fill 2 " + y + " 2 5 " + y + " 5 " + block);
                                    }
                                    commands.performPrefixedCommand(
                                            source, "fill 2 196 2 5 196 5 sea_lantern");
                                    commands.performPrefixedCommand(
                                            source, "tp @a 4 -62.5 4 0 -90");
                                } else {
                                    commands.performPrefixedCommand(
                                            source, "fill 2 -60 2 2 160 5 iron_block");
                                    commands.performPrefixedCommand(
                                            source, "fill 4 -60 2 4 160 5 iron_block");
                                    commands.performPrefixedCommand(
                                            source, "fill 3 161 2 3 161 5 sea_lantern");
                                    commands.performPrefixedCommand(
                                            source, "tp @a 3.5 -61 4 -90 -45");
                                }
                            },
                            server);
            transition = now;
            return;
        }
        if (!setup.isDone()) return;
        setup.join();
        var terminal = new net.minecraft.core.BlockPos(refract ? 4 : 3, refract ? 196 : 161, 4);
        if (!mc.level.getBlockState(terminal).is(net.minecraft.world.level.block.Blocks.SEA_LANTERN)
                || now - transition < 6_000_000_000L
                || ++frames < 120) return;
        RESULT.put("terminal", terminal.toString());
        EntityTakeoverProbe.screenshot(mc, MODE + ".png");
        finish(mc);
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
        if (!NativeSceneReplayProbe.ready()) {
            snapshotPending = true;
            return;
        }
        if (MODE.equals("media")) MediumContactFixture.close();
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
            // Also written at exit; a client that never finishes stopping still leaves this one.
            Files.writeString(
                    destination.resolveSibling("block-entity-rest.json"),
                    new GsonBuilder()
                                    .create()
                                    .toJson(
                                            java.util.Map.of(
                                                    "layout",
                                                    "sections,restingMembers,restingInstances,skippedMembersLastFrame,pollsPassed,pollsFailed,eventWakes,membersRested,sectionReleases",
                                                    "counters",
                                                    com.radiance.api.render.BlockEntityPublications
                                                            .restCounters(),
                                                    "blockers",
                                                    com.radiance.api.render.BlockEntityPublications
                                                            .restBlockers(),
                                                    "pollCaught",
                                                    com.radiance.api.render.BlockEntityPublications
                                                            .restMisses()))
                            + "\n");
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        com.mojang.logging.LogUtils.getLogger().info("WORLD_MATERIALS complete {}", MODE);
        mc.stop();
    }
}
