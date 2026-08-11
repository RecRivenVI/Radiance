package com.radiance.audit;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/** Bounded functional experiments. No timings, focus fabrication or final takeover API. */
public final class TakeoverBoundaryProbe {
    private static final boolean ENABLED =
            Boolean.getBoolean("radiance.audit.takeoverBoundaries") && ExperimentAccess.permitted();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final AtomicInteger extraGeometry = new AtomicInteger();
    private static TakeoverRigidBridge shared, changed;
    private static long started, resumed, nextSample;
    private static long stableModel;
    private static int step, samples, generatedSamples, focusedSamples;
    private static boolean finished;
    private static CompletableFuture<?> reload;
    private static Path result;

    private TakeoverBoundaryProbe() {}

    public static void register() {
        if (!ENABLED) return;
        NeoForge.EVENT_BUS.addListener(TakeoverBoundaryProbe::render);
        NeoForge.EVENT_BUS.addListener(TakeoverBoundaryProbe::additional);
    }

    private static void additional(AddSectionGeometryEvent event) {
        if (!event.getSectionOrigin().equals(new BlockPos(0, 80, 0))) return;
        // Retrieve the atlas on the client thread; only immutable UV values reach the worker.
        var sprite =
                Minecraft.getInstance()
                        .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                        .apply(ResourceLocation.withDefaultNamespace("block/stone"));
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        event.addRenderer(
                context -> {
                    var out = context.getOrCreateChunkBuffer(RenderType.cutout());
                    float[][] points = {
                        {1, 1, u0, v0}, {1, 2, u0, v1}, {2, 2, u1, v1}, {2, 1, u1, v0}
                    };
                    for (var p : points)
                        out.addVertex(p[0], .1f, p[1])
                                .setColor(-1)
                                .setUv(p[2], p[3])
                                .setLight(15728880)
                                .setNormal(0, 1, 0);
                    extraGeometry.incrementAndGet();
                });
    }

    private static void render(RenderLevelStageEvent event) {
        if (!ENABLED
                || finished
                || reload != null
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (shared == null) shared = new TakeoverRigidBridge(101 + step);
        if (stableModel == 0) stableModel = shared.model();
        var camera = event.getCamera();
        var origin = camera.getPosition().add(mc.player.getViewVector(1).scale(6));
        var a = new Matrix4f().rotate(camera.rotation()).translate(-.65f, 0, 0);
        var b = new Matrix4f().rotate(camera.rotation()).translate(.65f, 0, 0).scale(-1, 1, 1);
        var first = new TakeoverRigidBridge.Intent(0x424f554e4401L, a, 1, .02f, .02f);
        var second = new TakeoverRigidBridge.Intent(0x424f554e4402L, b, .02f, 1, .02f);
        shared.submit(
                origin.x,
                origin.y,
                origin.z,
                changed == null ? List.of(first, second) : List.of(first));
        if (changed != null) changed.submit(origin.x, origin.y, origin.z, List.of(second));
    }

    public static void poll(Minecraft mc) {
        if (!ENABLED || finished || mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        try {
            if (started == 0) {
                require(
                        Files.isRegularFile(
                                mc.gameDirectory.toPath().resolve(".radiance-takeover-validation")),
                        "owned fixture");
                require(
                        mc.getWindow().getWidth() == 1920 && mc.getWindow().getHeight() == 1080,
                        "dimensions");
                require(mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "muted");
                result = Path.of(System.getProperty("validation.result"));
                Files.createDirectories(result);
                started = now;
                Options.setDlssModels(Options.dlssSrModel, Options.dlssRrModel, 0, false, false);
                report.put("performanceMeasured", false);
                report.put("defaultUnattendedFocusGuard", true);
                LOG.info("TAKEOVER_BOUNDARY world-ready functional-only");
            }
            require(now - started < 180_000_000_000L, "duration bound");
            if (now >= nextSample) {
                nextSample = now + 1_000_000_000L;
                long before = System.nanoTime();
                String trace = ChunkProxy.drainUpdateTraceNative();
                long after = System.nanoTime();
                Files.writeString(
                        result.resolve("chunk-trace.txt"),
                        "JAVA_BRACKET " + before + " " + after + "\n" + trace,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
                if (Options.dlssFrameGeneration) {
                    samples++;
                    if ((RendererProxy.presentationRates() & 0xffffffffL) > 0) generatedSamples++;
                    if (mc.isWindowActive()) focusedSamples++;
                }
            }
            // Move the world underneath fixed UI without synthesizing user input.
            if (step >= 4 && step <= 6) {
                float yaw = 180 + (float) Math.sin((now - started) * 1e-9) * 8;
                mc.player.setYRot(yaw);
                mc.player.yRotO = yaw;
            }
            long seconds = (now - started) / 1_000_000_000L;
            if (step == 0 && seconds >= 6) {
                SurfaceContractProbe.verify("boundary-initial");
                MaterialStateBatchProbe.verify("boundary-initial");
                OffscreenDependencyProbe.verify("boundary-initial");
                mc.setScreen(new BoundaryScreen());
                capture(mc, "boundary-shared");
                step++;
            } else if (step == 1 && seconds >= 12) {
                require(shared != null && shared.submissions() > 0, "shared model submitted");
                changed = new TakeoverRigidBridge(202, .35f);
                report.put("stableModel", stableModel);
                report.put("changedModel", changed.model());
                require(changed.model() != shared.model(), "deformation owns new resource version");
                step++;
            } else if (step == 2 && seconds >= 18) {
                require(shared.model() == stableModel, "other owner retains original resource");
                capture(mc, "boundary-deformed");
                changed.close();
                changed = null;
                report.put(
                        "deformation",
                        "immutable replacement for B; A retains shared version; not parameterized GPU deformation");
                step++;
            } else if (step == 3 && seconds >= 22) {
                var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
                server.execute(
                        () -> {
                            var level = server.overworld();
                            for (int i = 0; i < 6; i++) {
                                level.setBlockAndUpdate(
                                        new BlockPos(15, 65, 0),
                                        (i % 2 == 0 ? Blocks.STONE : Blocks.GLASS)
                                                .defaultBlockState());
                                level.setBlockAndUpdate(
                                        new BlockPos(16, 65, 0),
                                        (i % 2 == 0 ? Blocks.WATER : Blocks.AIR)
                                                .defaultBlockState());
                            }
                            level.setBlockAndUpdate(
                                    new BlockPos(15, 65, 0),
                                    Blocks.RED_CONCRETE.defaultBlockState());
                            level.setBlockAndUpdate(
                                    new BlockPos(16, 65, 0), Blocks.WATER.defaultBlockState());
                        });
                LOG.info("TAKEOVER_BOUNDARY terrain-edit-dispatched boundary=15/16");
                step++;
            } else if (step == 4 && seconds >= 31) {
                require(
                        mc.level.getBlockState(new BlockPos(15, 65, 0)).is(Blocks.RED_CONCRETE),
                        "final client block state");
                require(
                        mc.level.getBlockState(new BlockPos(16, 65, 0)).is(Blocks.WATER),
                        "final client fluid state");
                require(extraGeometry.get() > 0, "additional geometry in an air section");
                report.put("additionalSectionCallbacks", extraGeometry.get());
                report.put("clientBoundaryState", true);
                report.put("fgAvailable", Options.nativeIsDlssFrameGenerationAvailable());
                Options.setDlssModels(Options.dlssSrModel, Options.dlssRrModel, 0, true, false);
                LOG.info("TAKEOVER_BOUNDARY FG-requested available={}", report.get("fgAvailable"));
                step++;
            } else if (step == 5 && seconds >= 39) {
                Files.writeString(
                        mc.gameDirectory.toPath().resolve("radiance-fg-capture.request"),
                        "boundary-panel-invert-blur\n");
                capture(mc, "boundary-fg-real");
                step++;
            } else if (step == 6 && seconds >= 49) {
                report.put("fgStateSamples", samples);
                report.put("fgGeneratedPositiveSamples", generatedSamples);
                report.put("fgFocusedSamples", focusedSamples);
                report.put("generatedPixelsInspected", false);
                Options.setDlssModels(Options.dlssSrModel, Options.dlssRrModel, 0, false, false);
                mc.setScreen(null);
                shared.close();
                shared = null;
                reload = mc.reloadResourcePacks();
                step++;
            } else if (step == 7 && reload != null && reload.isDone()) {
                reload.join();
                reload = null;
                resumed = now;
                step++;
                LOG.info("TAKEOVER_BOUNDARY reload-complete");
            } else if (step == 8 && now - resumed >= 8_000_000_000L) {
                OffscreenDependencyProbe.verify("boundary-reloaded");
                require(shared != null && shared.model() != stableModel, "new epoch resource");
                capture(mc, "boundary-reloaded");
                report.put("reloadComplete", true);
                report.put("offscreenGpuReadback", true);
                report.put("currentPublicTakeoverContractImplemented", false);
                report.put(
                        "worldSurfaceEvidence",
                        "actual spider/enderman eye overlays and charged creeper; CPU+screenshots, not all surface programs");
                Files.writeString(
                        result.resolve("boundaries.json"),
                        new com.google.gson.GsonBuilder()
                                        .setPrettyPrinting()
                                        .create()
                                        .toJson(report)
                                + "\n");
                shared.close();
                shared = null;
                finished = true;
                LOG.info(
                        "TAKEOVER_BOUNDARY normal-stop generated-positive-samples={} focus-positive-samples={}",
                        generatedSamples,
                        focusedSamples);
                mc.stop();
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(
                mc.gameDirectory,
                name + ".png",
                mc.getMainRenderTarget(),
                message -> LOG.info("TAKEOVER_BOUNDARY capture={}", name));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Takeover boundary: " + message);
    }

    private static final class BoundaryScreen extends Screen {
        BoundaryScreen() {
            super(Component.literal("Takeover boundary probe"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(12, 16, 220, 78, 0x804070a0);
            g.drawString(font, "Before blur: fractional panel + text", 18, 24, 0xa0ffffff, false);
            g.renderItem(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 180, 45);
            g.flush();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            g.fill(85, 42, 120, 67, -1);
            g.flush();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            Minecraft.getInstance().gameRenderer.processBlurEffect(partialTick);
            g.fill(12, 90, 220, 115, 0x60703050);
            g.drawString(font, "After blur: sharp UI", 18, 97, 0xa0ffffff, false);
            g.flush();
        }
    }
}
