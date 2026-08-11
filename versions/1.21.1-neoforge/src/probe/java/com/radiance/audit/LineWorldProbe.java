package com.radiance.audit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.render.LineAudit;
import com.radiance.client.render.RenderCaptureContract;
import com.radiance.compatibility.veil.VeilStageAdapter;
import com.radiance.render.contract.DrawTargetContract;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Bounded functional exercise of original emitters and real stage/JNI consumers; no timing
 * benchmark.
 */
public final class LineWorldProbe {
    private static final boolean ENABLED =
            Boolean.getBoolean("radiance.audit.lines") && ExperimentAccess.permitted();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long start, callbacks, reentrySince, callbacksBeforeReentry;
    private static int step;
    private static CompletableFuture<?> reload;
    private static java.lang.reflect.Method lever, rope;
    private static Object leverEntity;
    private static it.unimi.dsi.fastutil.objects.ObjectArrayList<Object> points;

    private LineWorldProbe() {}

    public static void register() {
        if (ENABLED) NeoForge.EVENT_BUS.addListener(LineWorldProbe::render);
    }

    private static void initialize() throws ReflectiveOperationException {
        var block =
                BuiltInRegistries.BLOCK.get(
                        ResourceLocation.fromNamespaceAndPath("simulated", "throttle_lever"));
        if (!(block instanceof EntityBlock entityBlock))
            throw new IllegalStateException("Missing source lever");
        leverEntity = entityBlock.newBlockEntity(new BlockPos(0, 70, 0), block.defaultBlockState());
        lever =
                Class.forName(
                                "dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverRenderer")
                        .getDeclaredMethod(
                                "renderOutline",
                                leverEntity.getClass(),
                                PoseStack.class,
                                MultiBufferSource.class,
                                float.class);
        lever.setAccessible(true);
        var ropeClass =
                Class.forName(
                        "dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer");
        rope =
                ropeClass.getDeclaredMethod(
                        "renderOutline",
                        PoseStack.class,
                        MultiBufferSource.class,
                        float.class,
                        it.unimi.dsi.fastutil.objects.ObjectArrayList.class,
                        BlockPos.class);
        rope.setAccessible(true);
        var pointClass = Class.forName(ropeClass.getName() + "$RopeRenderPoint");
        var constructor =
                pointClass.getConstructor(org.joml.Quaternionf.class, org.joml.Vector3d.class);
        points = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        points.add(
                constructor.newInstance(
                        new org.joml.Quaternionf(), new org.joml.Vector3d(0, 70, 0)));
        points.add(
                constructor.newInstance(
                        new org.joml.Quaternionf().rotateY(.5f), new org.joml.Vector3d(0, 72, 1)));
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || start == 0
                || (step >= 4 && step != 7)) return;
        var mc = Minecraft.getInstance();
        var buffers = mc.renderBuffers().bufferSource();
        var origin = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.translate(-origin.x, 70 - origin.y, -origin.z);
        pose.mulPose(
                new org.joml.Matrix4f()
                        .rotateY((callbacks % 90) * .01f)
                        .rotateZ(.35f)
                        .scale((callbacks & 1) == 0 ? -1 : 1, 2, .5f));
        VertexConsumer out = buffers.getBuffer(RenderType.lines());
        line(out, pose, 0, 0, 0, 1, 1, 0);
        buffers.endBatch(RenderType.lines());
        for (double width : new double[] {1, 4}) {
            var layer = RenderType.debugLineStrip(width);
            if (callbacks == 0) {
                var style =
                        com.radiance.client.render.LineDrawAdapter.capture(
                                layer,
                                com.radiance.render.contract.LineContract.WidthPolicy.DEBUG,
                                1,
                                1);
                if (style.sourceWidth() != width
                        || Math.abs(style.worldThickness() - width * .0075) > 1e-6)
                    throw new IllegalStateException("Source width was overwritten");
                LOG.info(
                        "LINE_WORLD source-width={} physical-width={}",
                        width,
                        style.worldThickness());
            }
            line(buffers.getBuffer(layer), pose, 0, (float) width, 0, 1, (float) width + 1, 0);
            buffers.endBatch(layer);
        }
        if (callbacks == 0) {
            try (var cancelled =
                    com.radiance.client.render.WorldMeshSink.enterStage(
                            com.radiance.client.render.WorldMeshSink.StageKey.AFTER_PARTICLES,
                            com.radiance.client.render.WorldMeshSink.CoordinateSpace.CAMERA_SHIFT,
                            net.minecraft.world.phys.Vec3.ZERO,
                            com.radiance.client.render.WorldMeshSink.TargetKind.DEFAULT_WORLD,
                            "line_probe/cancelled")) {
                line(buffers.getBuffer(RenderType.lines()), pose, 0, 0, 0, 1, 0, 0);
                // Deliberately no commit: owned pending lines must be discarded before JNI.
            }
            LOG.info("LINE_WORLD cancelled-stage closed");
        }
        try (var source =
                com.radiance.client.render.LineSources.enter(
                        com.radiance.client.render.LineSources.SABLE)) {
            var wrapped =
                    new dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer(
                            buffers.getBuffer(RenderType.lines()));
            line(wrapped, pose, 2, 0, 0, 3, 1, 0);
        }
        buffers.endBatch(RenderType.lines());
        // Exercise the actual production wrapper: an unknown listener runs exactly once
        // in WORLD_STAGE and its explicit line request reaches the same receiver.
        VeilStageAdapter.wrap(
                        (a, b, c, d, e, f, g, h, i, j) -> {
                            if (RenderCaptureContract.currentScope().kind()
                                    == DrawTargetContract.ScopeKind.WORLD_RASTER)
                                throw new IllegalStateException(
                                        "Unknown listener obtained raster permission");
                            line(c.getBuffer(RenderType.lines()), pose, 1, 0, 0, 2, 1, 0);
                        })
                .onRenderLevelStage(null, null, buffers, null, null, null, 0, null, null, null);
        buffers.endBatch(RenderType.lines());
        try {
            lever.invoke(null, leverEntity, pose, buffers, .6f);
            PoseStack ropePose = new PoseStack();
            ropePose.translate(-origin.x, -origin.y, -origin.z);
            rope.invoke(null, ropePose, buffers, .12f, points, BlockPos.ZERO);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Original line emitter seam failed", failure);
        }
        callbacks++;
    }

    private static void line(
            VertexConsumer out,
            PoseStack pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            float w) {
        out.addVertex(pose.last(), x, y, z)
                .setColor(255, 50, 25, 102)
                .setNormal(pose.last(), u - x, v - y, w - z);
        out.addVertex(pose.last(), u, v, w)
                .setColor(20, 100, 255, 153)
                .setNormal(pose.last(), u - x, v - y, w - z);
    }

    public static void poll(Minecraft mc) {
        if (!ENABLED || step == 8) return;
        if (Files.isRegularFile(
                mc.gameDirectory.toPath().resolve("line-validation-stop.request"))) {
            step = 8;
            LOG.info("LINE_WORLD watchdog-stop requested");
            mc.stop();
            return;
        }
        if (step == 5 && System.nanoTime() - reentrySince >= 2_000_000_000L) {
            step = 6;
            mc.createWorldOpenFlows()
                    .openWorld(
                            "Audit Benchmark",
                            () -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
            LOG.info("LINE_WORLD reenter requested");
            return;
        }
        if (mc.level == null || mc.player == null || step == 4 || step == 5) return;
        try {
            if (start == 0) {
                if (!Files.isRegularFile(
                        mc.gameDirectory.toPath().resolve(".radiance-line-validation")))
                    throw new IllegalStateException("Isolated line fixture marker required");
                initialize();
                SurfaceContractProbe.verify("world-ready");
                if (mc.getWindow().getWidth() != 1920 || mc.getWindow().getHeight() != 1080)
                    throw new IllegalStateException("Line validation requires 1920x1080");
                if (mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) != 0)
                    throw new IllegalStateException("Line validation must be muted");
                start = System.nanoTime();
                mc.options.pauseOnLostFocus = false;
                LOG.info(
                        "LINE_WORLD world-ready; original lever/rope emitters + NeoForge/Veil seams");
            }
            long seconds = (System.nanoTime() - start) / 1_000_000_000L;
            if (seconds >= 6 && seconds < 10) {
                mc.player.setYRot((float) ((System.nanoTime() - start) / 10_000_000L % 360));
                mc.player.yRotO = mc.player.getYRot();
            }
            if (step == 0 && seconds >= 12) {
                step = 1;
                mc.getWindow().setWindowed(1280, 720);
                LOG.info("LINE_WORLD resized=1280x720");
            } else if (step == 1 && seconds >= 18) {
                step = 2;
                mc.getWindow().setWindowed(1920, 1080);
                reload = mc.reloadResourcePacks();
                LOG.info("LINE_WORLD reload requested");
            } else if (step == 2 && reload != null && reload.isDone()) {
                reload.join();
                step = 3;
                SurfaceContractProbe.verify("reload-complete");
                LOG.info("LINE_WORLD reload complete");
            } else if (step == 3 && seconds >= 45) {
                callbacksBeforeReentry = callbacks;
                step = 4;
                mc.level.disconnect();
                mc.disconnect(
                        new net.minecraft.client.gui.screens.GenericMessageScreen(
                                net.minecraft.network.chat.Component.translatable(
                                        "menu.savingLevel")));
                mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
                reentrySince = System.nanoTime();
                step = 5;
                LOG.info("LINE_WORLD left-world callbacks={}", callbacks);
            } else if (step == 6) {
                step = 7;
                reentrySince = System.nanoTime();
                SurfaceContractProbe.verify("world-reentered");
                LOG.info("LINE_WORLD reentered");
            } else if (step == 7 && System.nanoTime() - reentrySince >= 8_000_000_000L) {
                if (callbacks <= callbacksBeforeReentry)
                    throw new IllegalStateException("No new line callbacks after world reentry");
                var counters = LineAudit.snapshot();
                for (int source : new int[] {3, 4, 5, 6, 7}) {
                    if (counters[source][0] == 0 || counters[source][3] == 0)
                        throw new IllegalStateException("Missing source capture " + source);
                }
                if (counters[3][6] != 0)
                    throw new IllegalStateException("Sable wrapper discarded its line pose");
                for (var row : counters)
                    if (row[2] != 0) throw new IllegalStateException("Line invariant violation");
                if (FontSourceProbe.violations() != 0)
                    throw new IllegalStateException("Retired font source was observed");
                LOG.info("FONT_SOURCE final-violations=0");
                Path result = Path.of(System.getProperty("validation.result"));
                Files.createDirectories(result);
                Files.writeString(
                        result.resolve("line-contract.json"),
                        new com.google.gson.Gson().toJson(counters) + "\n");
                step = 8;
                LOG.info("LINE_WORLD normal-stop callbacks={} invalid=0", callbacks);
                mc.stop();
            }
            if (seconds > 150)
                throw new IllegalStateException("Line validation exceeded bounded duration");
        } catch (java.io.IOException | ReflectiveOperationException failure) {
            throw new IllegalStateException("Line validation", failure);
        }
    }
}
