package com.radiance.audit;

import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Fixed correctness route. No frame timing or producer profiling is collected. */
public final class DrawingCensusProbe {
    private static final boolean FULL = Boolean.getBoolean("radiance.audit.vanillaRoute");
    private static final boolean CITY = Boolean.getBoolean("radiance.audit.fullCensus");
    private static final boolean EFFECTS = Boolean.getBoolean("radiance.audit.screenEffects");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static int step;
    private static long since, start;
    private static boolean polling;
    private static boolean menuVisited, proceduralVisited;
    private static boolean galleryVisited, cityVisited;
    private static int originalScale;
    private static java.util.concurrent.CompletableFuture<?> pending;

    private DrawingCensusProbe() {}

    public static void poll(Minecraft mc) {
        if (!DrawingCensus.ENABLED || !ExperimentAccess.permitted() || step == 9 || polling) return;
        // disconnect() pumps nested runTick frames while the integrated server saves.
        // Those frames must render, but must not recursively execute this route action.
        polling = true;
        long now = System.nanoTime();
        try {
            if (start == 0) start = now;
            if (now - start > 480_000_000_000L) throw new IllegalStateException("Census timeout");
            if (step == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                if (!CityFrameComparisonProbe.viewportReady(mc, now)) return;
                if (FULL && !menuVisited) {
                    menuVisited = true;
                    mc.setScreen(
                            new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(
                                    new TitleScreen()));
                    since = now;
                    step = -1;
                    return;
                }
                step = 1;
                mc.createWorldOpenFlows()
                        .openWorld("Audit Benchmark", () -> mc.setScreen(new TitleScreen()));
            }
            if (step == -1 && now - since > 2_000_000_000L) {
                mc.setScreen(new TitleScreen());
                step = 0;
                return;
            }
            if (step == 1 && mc.player != null) {
                DrawingCensus.INSTANCE.window("R1-small-gallery");
                if (CITY) {
                    command(mc, "gamemode creative @a");
                    mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
                    mc.getEntityRenderDispatcher().setRenderHitBoxes(true);
                    mc.debugRenderer.switchRenderChunkborder();
                    command(mc, "tp @a 0.5 73 14.5 -180 0");
                }
                since = now;
                step = 2;
                LOG.info("DRAWING_CENSUS world-ready");
            } else if (step == 2 && now - since > 15_000_000_000L) {
                if (CITY && !galleryVisited) {
                    galleryVisited = true;
                    command(mc, "tp @a 2.5 73 14.5 -180 0");
                    since = now;
                    step = 30;
                    return;
                }
                if (FULL && !proceduralVisited) {
                    proceduralVisited = true;
                    DrawingCensus.INSTANCE.window("R4-procedural");
                    VanillaProceduralFixture.start(mc);
                    step = 20;
                    return;
                }
                DrawingCensus.INSTANCE.window("R5-inventory");
                mc.setScreen(new InventoryScreen(mc.player));
                since = now;
                step = 3;
            } else if (step == 20 && VanillaProceduralFixture.poll(mc, now)) {
                since = now - 15_000_000_000L;
                step = 2;
                OffscreenDependencyProbe.verify("vanilla-stage67");
            } else if (step == 30 && now - since > 2_000_000_000L) {
                DrawingCensus.INSTANCE.window("R2-entities-ber");
                command(mc, "tp @a 0 68 6 180 25");
                since = now;
                step = 31;
            } else if (step == 31 && now - since > 5_000_000_000L) {
                since = now - 15_000_000_000L;
                step = 2;
            } else if (step == 3 && now - since > 3_000_000_000L) {
                if (GuiReviewProbe.ENABLED) {
                    GuiReviewProbe.start(mc, now);
                    step = 80;
                    return;
                }
                if (EFFECTS) {
                    mc.setScreen(null);
                    VanillaScreenFixture.start(mc, now);
                    step = 70;
                    return;
                }
                if (CITY) {
                    originalScale = mc.options.guiScale().get();
                    mc.options.guiScale().set(2);
                    mc.resizeDisplay();
                    DrawingCensus.INSTANCE.window("R5-gui-scale-resize");
                    since = now;
                    step = 40;
                    return;
                }
                mc.setScreen(null);
                DrawingCensus.INSTANCE.window("R7-reload");
                pending = mc.reloadResourcePacks();
                step = 4;
            } else if (step == 80 && GuiReviewProbe.poll(mc, now)) {
                DrawingCensus.INSTANCE.window("R7-reload");
                pending = mc.reloadResourcePacks();
                step = 4;
            } else if (step == 70 && VanillaScreenFixture.poll(mc, now)) {
                DrawingCensus.INSTANCE.window("R7-reload");
                pending = mc.reloadResourcePacks();
                step = 4;
            } else if (step == 40 && now - since > 2_000_000_000L) {
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
                since = now;
                step = 41;
            } else if (step == 41 && now - since > 2_000_000_000L) {
                if (mc.getWindow().getWidth() != 1280 || mc.getWindow().getHeight() != 720)
                    throw new IllegalStateException("Census resize did not reach 1280x720");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
                mc.options.guiScale().set(originalScale);
                mc.resizeDisplay();
                mc.setScreen(null);
                // Exercise the actual screen-effect producer on this owned fixture.
                command(mc, "fill -1 68 5 1 71 7 water");
                command(mc, "tp @a 0 68 6 180 0");
                since = now;
                step = 42;
            } else if (step == 42 && now - since > 3_000_000_000L) {
                command(mc, "fill -1 68 5 1 71 7 air");
                mc.player.setRemainingFireTicks(80);
                since = now;
                step = 43;
            } else if (step == 43 && now - since > 2_000_000_000L) {
                mc.player.clearFire();
                DrawingCensus.INSTANCE.window("R7-reload");
                pending = mc.reloadResourcePacks();
                step = 4;
            } else if (step == 4 && pending.isDone()) {
                pending.join();
                since = now;
                step = 5;
            } else if (step == 5 && now - since > 5_000_000_000L) {
                if (GuiReviewProbe.ENABLED) GuiReviewProbe.afterReload(mc);
                if (CITY && !cityVisited) {
                    cityVisited = true;
                    closeWorld(mc);
                    DrawingCensus.INSTANCE.window("R3-city-entry");
                    mc.createWorldOpenFlows()
                            .openWorld("Newport City", () -> mc.setScreen(new TitleScreen()));
                    step = 50;
                    return;
                }
                write("baseline");
                DrawingCensus.INSTANCE.window("R7-save-close");
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
                since = now;
                step = 6;
            } else if (step == 50 && mc.player != null && mc.level != null) {
                DrawingCensus.INSTANCE.window("R3-city");
                since = now;
                step = 51;
            } else if (step == 51 && now - since > 15_000_000_000L) {
                command(mc, "tp @a 97.78 141.61 10.90 53.535 32.749");
                since = now;
                step = 52;
            } else if (step == 52 && now - since > 3_000_000_000L) {
                command(mc, "tp @a 95.784 141.607 12.897 53.535 32.749");
                since = now;
                step = 53;
            } else if (step == 53 && now - since > 3_000_000_000L) {
                closeWorld(mc);
                DrawingCensus.INSTANCE.window("R7-small-reentry");
                mc.createWorldOpenFlows()
                        .openWorld("Audit Benchmark", () -> mc.setScreen(new TitleScreen()));
                step = 54;
            } else if (step == 54 && mc.player != null && mc.level != null) {
                since = now;
                step = 55;
            } else if (step == 55 && now - since > 5_000_000_000L) {
                closeWorld(mc);
                DrawingCensus.INSTANCE.window("R7-city-reentry");
                mc.createWorldOpenFlows()
                        .openWorld("Newport City", () -> mc.setScreen(new TitleScreen()));
                step = 56;
            } else if (step == 56 && mc.player != null && mc.level != null) {
                since = now;
                step = 57;
            } else if (step == 57 && now - since > 5_000_000_000L) {
                write("baseline");
                closeWorld(mc);
                since = now;
                step = 6;
            } else if (step == 6 && now - since > 2_000_000_000L) {
                verifyCensus();
                write("baseline");
                step = 9;
                LOG.info("DRAWING_CENSUS BASELINE_COMPLETE");
                mc.stop();
            }
        } catch (Throwable failure) {
            step = 9;
            LOG.error("DRAWING_CENSUS FAIL", failure);
            try {
                write("failed");
            } catch (java.io.IOException ignored) {
            }
            mc.stop();
        } finally {
            polling = false;
        }
    }

    private static void verifyCensus() {
        var families = new java.util.HashSet<String>();
        for (var row : DrawingCensus.INSTANCE.snapshot().entrySet()) {
            var c = row.getValue();
            if (c.unowned != 0 || c.duplicate != 0 || c.unknown != 0 || c.requests != c.consumed)
                throw new IllegalStateException("Drawing census unresolved: " + row.getKey());
            if (c.requests > 0) families.add(row.getKey().family());
        }
        if (DrawingCensus.INSTANCE.pendingCount() != 0)
            throw new IllegalStateException("Pending drawing requests");
        if (CITY)
            for (String family :
                    java.util.List.of(
                            "RASTER",
                            "PRESENT",
                            "SCENE",
                            "SECTION",
                            "PERSISTENT",
                            "PARTICLE",
                            "PARTICLE_CUSTOM",
                            "LIGHTNING",
                            "WORLD_TEXT",
                            "WEATHER_BORDER",
                            "CLOUD",
                            "LINE",
                            "SCREEN_EFFECT",
                            "ATTACHMENT",
                            "READBACK"))
                if (!families.contains(family))
                    throw new IllegalStateException("Unvisited drawing family: " + family);
        long[] loading = com.radiance.api.audit.DrawingAudit.loadingCounts();
        if (loading[0] == 0 || loading[0] != loading[1])
            throw new IllegalStateException("SERVICE loading requests unresolved");
    }

    private static void closeWorld(Minecraft mc) {
        mc.level.disconnect();
        mc.disconnect(new TitleScreen());
    }

    static void command(Minecraft mc, String text) {
        var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
        server.execute(
                () ->
                        server.getCommands()
                                .performPrefixedCommand(
                                        server.createCommandSourceStack()
                                                .withSuppressedOutput()
                                                .withPermission(4),
                                        text));
    }

    static void write(String label) throws java.io.IOException {
        if (!com.radiance.api.audit.RenderAuditBridge.enabled())
            throw new java.io.IOException("Drawing census observer detached");
        var rows =
                new StringBuilder(
                        "scene,window,family,source,conclusion,target,requests,consumed,unowned,duplicate,unknown\n");
        for (var row : DrawingCensus.INSTANCE.snapshot().entrySet()) {
            var k = row.getKey();
            var c = row.getValue();
            rows.append(k.scene())
                    .append(',')
                    .append(
                            String.join(
                                    ",",
                                    k.window(),
                                    k.family(),
                                    k.source(),
                                    k.conclusion(),
                                    k.target()))
                    .append(',')
                    .append(c.requests)
                    .append(',')
                    .append(c.consumed)
                    .append(',')
                    .append(c.unowned)
                    .append(',')
                    .append(c.duplicate)
                    .append(',')
                    .append(c.unknown)
                    .append('\n');
        }
        long[] service = com.radiance.api.audit.DrawingAudit.loadingCounts();
        rows.append("0,R0-loading,LOADING,SERVICE,DIRECT,SWAPCHAIN,")
                .append(service[0])
                .append(',')
                .append(service[1])
                .append(',')
                .append(service[0] - service[1])
                .append(",0,0\n");
        Path directory = Path.of(System.getProperty("validation.result"));
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("drawing-census-" + label + ".csv"), rows);
        Files.writeString(
                directory.resolve("drawing-census-pending.txt"),
                "pending="
                        + DrawingCensus.INSTANCE.pendingCount()
                        + "\nCPU submission acknowledgements; not GPU completion.\n");
        if (proceduralVisited)
            Files.writeString(
                    directory.resolve("procedural.json"),
                    new com.google.gson.GsonBuilder()
                                    .setPrettyPrinting()
                                    .create()
                                    .toJson(VanillaProceduralFixture.results())
                            + "\n");
    }
}
