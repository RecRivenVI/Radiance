package com.radiance.audit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.world.ChunkProxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Bounded stage driver. Formal windows and dual-execution checks are mutually exclusive. */
public final class TakeoverStageProbe {
    private static final boolean ENABLED =
            Boolean.getBoolean("radiance.audit.takeoverStages") && ExperimentAccess.permitted();
    private static final boolean PERFORMANCE =
            Boolean.getBoolean("radiance.audit.takeoverPerformance");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final AtomicInteger extras = new AtomicInteger();
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static long start, since, traceAt;
    private static int step, edits, window;
    private static long[] warmCounters;
    private static CompletableFuture<?> reload;
    private static CompletableFuture<List<Map<String, Object>>> paintingFixture;
    private static boolean done;

    private TakeoverStageProbe() {}

    public static void register() {
        if (!ENABLED || PERFORMANCE) return;
        NeoForge.EVENT_BUS.addListener(
                (AddSectionGeometryEvent event) -> {
                    if (!event.getSectionOrigin().equals(new BlockPos(16, 64, 0))) return;
                    var sprite =
                            Minecraft.getInstance()
                                    .getTextureAtlas(
                                            net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                                    .apply(
                                            net.minecraft.resources.ResourceLocation
                                                    .withDefaultNamespace("block/stone"));
                    float u0 = sprite.getU0(),
                            u1 = sprite.getU1(),
                            v0 = sprite.getV0(),
                            v1 = sprite.getV1();
                    event.addRenderer(
                            context -> {
                                var out = context.getOrCreateChunkBuffer(RenderType.cutout());
                                float[][] vertices = {
                                    {2, 3, u0, v0}, {2, 4, u0, v1}, {3, 4, u1, v1}, {3, 3, u1, v0}
                                };
                                for (var v : vertices)
                                    out.addVertex(v[0], 3, v[1])
                                            .setColor(-1)
                                            .setUv(v[2], v[3])
                                            .setLight(15728880)
                                            .setNormal(0, 1, 0);
                                extras.incrementAndGet();
                            });
                });
    }

    public static void poll(Minecraft mc) {
        if (!ENABLED || done || mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        try {
            if (start == 0) {
                require(
                        Files.isRegularFile(
                                mc.gameDirectory.toPath().resolve(".radiance-takeover-stages")),
                        "owned stage fixture");
                require(
                        mc.getWindow().getWidth() == 1920 && mc.getWindow().getHeight() == 1080,
                        "viewport");
                require(mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "muted");
                require(
                        !PERFORMANCE || !Boolean.getBoolean("radiance.audit.takeoverCompare"),
                        "formal window excludes dual execution");
                start = since = now;
                result.put("performanceMeasured", PERFORMANCE);
                result.put(
                        "sourceProperty",
                        System.getProperty("radiance.internal.bakedSource", "absent"));
                LOG.info("TAKEOVER_STAGE world-ready performance={}", PERFORMANCE);
            }
            require(now - start < 300_000_000_000L, "bounded duration");
            if (PERFORMANCE) {
                performance(mc, now);
                return;
            }
            Path output = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(output);
            if (now - traceAt >= 500_000_000L) {
                traceAt = now;
                Files.writeString(
                        output.resolve("chunk-trace.txt"),
                        ChunkProxy.drainUpdateTraceNative() + "\n",
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
            long seconds = (now - start) / 1_000_000_000L;
            if (paintingFixture == null && countPaintings(mc) == 24) {
                paintingFixture = new CompletableFuture<>();
                var server = Objects.requireNonNull(mc.getSingleplayerServer());
                server.execute(
                        () -> {
                            var rows = new ArrayList<Map<String, Object>>();
                            for (var entity : server.overworld().getAllEntities()) {
                                if (entity
                                        instanceof
                                        net.minecraft.world.entity.decoration.Painting painting) {
                                    var support =
                                            painting.getPos()
                                                    .relative(
                                                            painting.getDirection().getOpposite());
                                    rows.add(
                                            Map.of(
                                                    "position",
                                                    painting.getPos().toShortString(),
                                                    "direction",
                                                    painting.getDirection().name(),
                                                    "survives",
                                                    painting.survives(),
                                                    "support",
                                                    server.overworld()
                                                            .getBlockState(support)
                                                            .toString()));
                                }
                            }
                            paintingFixture.complete(List.copyOf(rows));
                        });
            }
            if (step == 0 && seconds >= 15 && paintingFixture != null && paintingFixture.isDone()) {
                result.put("paintingFixture", paintingFixture.join());
                require(
                        paintingFixture.join().size() == 24
                                && paintingFixture.join().stream()
                                        .allMatch(row -> Boolean.TRUE.equals(row.get("survives"))),
                        "valid attached painting fixture");
                require(
                        "independent".equals(System.getProperty("radiance.internal.bakedSource")),
                        "qualified source");
                warmCounters = counters();
                require(warmCounters[2] > 100, "actual direct item-frame submissions");
                verifyStates();
                step++;
                since = now;
                Screenshot.grab(
                        mc.gameDirectory,
                        "takeover-stage-before.png",
                        mc.getMainRenderTarget(),
                        message -> {});
            } else if (step == 1 && now - since >= 5_000_000_000L) {
                var current = counters();
                require(current[2] > warmCounters[2], "hot direct submissions");
                require(
                        current[1] == warmCounters[1],
                        "hot frames do not rebuild immutable local models");
                require(
                        ((Number) BakedTakeoverProbe.results().get("checks")).longValue() > 10,
                        "actual original baked comparison");
                result.put("warmBefore", warmCounters);
                result.put("warmAfter", current);
                result.put("bakedComparison", BakedTakeoverProbe.results());
                PaintingModelProbe.checkpoint("takeover-before-reload");
                step++;
                since = now;
            } else if (step == 2 && now - since >= 300_000_000L && edits < 12) {
                int revision = ++edits;
                since = now;
                var server = Objects.requireNonNull(mc.getSingleplayerServer());
                server.execute(
                        () -> {
                            server.overworld()
                                    .setBlockAndUpdate(
                                            new BlockPos(15, 66, 1),
                                            (revision == 12
                                                            ? Blocks.RED_CONCRETE
                                                            : revision % 2 == 0
                                                                    ? Blocks.GLASS
                                                                    : Blocks.STONE)
                                                    .defaultBlockState());
                            server.overworld()
                                    .setBlockAndUpdate(
                                            new BlockPos(16, 66, 1),
                                            (revision % 2 == 0 ? Blocks.WATER : Blocks.AIR)
                                                    .defaultBlockState());
                        });
                LOG.info("TAKEOVER_STAGE terrain-request sequence={} java-ns={}", revision, now);
            } else if (step == 2 && edits == 12 && now - since >= 6_000_000_000L) {
                require(
                        mc.level.getBlockState(new BlockPos(15, 66, 1)).is(Blocks.RED_CONCRETE),
                        "final block");
                require(
                        mc.level.getBlockState(new BlockPos(16, 66, 1)).is(Blocks.WATER),
                        "final water");
                require(extras.get() > 0, "actual additional section renderer");
                result.put("terrainEdits", edits);
                result.put("additionalSectionCallbacks", extras.get());
                result.put("boundaryClientState", true);
                PaintingModelProbe.checkpoint("before-reload");
                reload = mc.reloadResourcePacks();
                step++;
            } else if (step == 3 && reload.isDone()) {
                reload.join();
                step++;
                since = now;
            } else if (step == 4
                    && now - since >= 8_000_000_000L
                    && PaintingModelProbe.checkedCount() > 0) {
                PaintingModelProbe.checkpoint("reload-complete");
                verifyStates();
                OffscreenDependencyProbe.verify("takeover-reloaded");
                result.put("reloadComplete", true);
                result.put("countersAfterReload", counters());
                result.put("fontViolations", FontSourceProbe.violations());
                require(FontSourceProbe.violations() == 0, "font lifecycle");
                Screenshot.grab(
                        mc.gameDirectory,
                        "takeover-stage-after.png",
                        mc.getMainRenderTarget(),
                        message -> {});
                finish(mc);
            }
        } catch (Exception | LinkageError failure) {
            done = true;
            int paintings = 0, frames = 0;
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof net.minecraft.world.entity.decoration.Painting) paintings++;
                if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame) frames++;
            }
            LOG.error(
                    "TAKEOVER_STAGE failure-state paintings={} frames={} comparedPaintings={} phase={}",
                    paintings,
                    frames,
                    PaintingModelProbe.checkedCount(),
                    step);
            if (paintingFixture != null && paintingFixture.isDone())
                LOG.error("TAKEOVER_STAGE painting-fixture={}", paintingFixture.join());
            LOG.error("TAKEOVER_STAGE FAIL; no retry", failure);
            mc.stop();
        }
    }

    private static void performance(Minecraft mc, long now) throws Exception {
        int warm = Integer.getInteger("radiance.audit.takeoverWarmup", 30);
        if (now - start < (long) warm * 1_000_000_000L) return;
        if (step == 0) {
            if (FrameProfiler.request(
                            Integer.getInteger("radiance.audit.takeoverSeconds", 20),
                            "takeover-" + window + (window % 2 == 0 ? "-static" : "-moving"),
                            window < 2 ? ProfileMode.FRAME_ONLY : ProfileMode.TIMED)
                    != 1) throw new IllegalStateException("profile busy");
            step = 1;
            since = now;
            return;
        }
        boolean moving = window % 2 != 0;
        float yaw = moving ? 180 + (float) Math.sin((now - since) * 1e-9 * .45) * 12 : 180;
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        if (step == 1 && !FrameProfiler.requested()) {
            if (++window == 4) {
                result.put("windows", window);
                result.put("dualExecution", false);
                finish(mc);
            } else step = 0;
        }
    }

    private static int countPaintings(Minecraft mc) {
        int count = 0;
        for (var entity : mc.level.entitiesForRendering())
            if (entity instanceof net.minecraft.world.entity.decoration.Painting) count++;
        return count;
    }

    private static long[] counters() throws Exception {
        return (long[])
                Class.forName("com.radiance.client.vertex.BakedModelTakeover")
                        .getMethod("counters")
                        .invoke(null);
    }

    private static void verifyStates() {
        var saved = com.radiance.client.render.MaterialFaces.state();
        var colour = RenderSystem.getShaderColor().clone();
        try {
            for (boolean enabled : new boolean[] {false, true})
                for (int winding : new int[] {2304, 2305}) {
                    com.radiance.client.render.MaterialFaces.enabled(enabled);
                    com.radiance.client.render.MaterialFaces.frontFace(winding);
                    RenderSystem.setShaderColor(.2f, .4f, .6f, .75f);
                    for (var layer :
                            List.of(
                                    net.minecraft.client.renderer.Sheets.solidBlockSheet(),
                                    net.minecraft.client.renderer.Sheets.cutoutBlockSheet(),
                                    net.minecraft.client.renderer.Sheets.translucentItemSheet())) {
                        var actual = com.radiance.client.render.StaticDrawStates.capture(layer);
                        var reference =
                                com.radiance.client.render.MaterialFaces.captureSnapshot(layer);
                        require(
                                actual != null && actual.equals(reference),
                                "actual source material state");
                    }
                }
        } finally {
            saved.apply();
            RenderSystem.setShaderColor(colour[0], colour[1], colour[2], colour[3]);
        }
    }

    private static void finish(Minecraft mc) throws java.io.IOException {
        Path output = Path.of(System.getProperty("validation.result"));
        Files.createDirectories(output);
        Files.writeString(
                output.resolve("takeover-stages.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result)
                        + "\n");
        done = true;
        LOG.info("TAKEOVER_STAGE normal-stop performance={}", PERFORMANCE);
        mc.stop();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("Stage sample: " + message);
    }
}
