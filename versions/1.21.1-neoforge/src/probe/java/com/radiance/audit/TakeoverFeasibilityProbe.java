package com.radiance.audit;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Rotations;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/** Functional feasibility, not the final takeover API and never a performance sample. */
public final class TakeoverFeasibilityProbe {
    private static final boolean ENABLED =
            Boolean.getBoolean("radiance.audit.takeover") && ExperimentAccess.permitted();
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final Map<String, Object> results = new LinkedHashMap<>();
    private static TakeoverRigidBridge bridge;
    private static long start, resumed, drawFrames, submissions;
    private static int step, epochs;
    private static CompletableFuture<?> reload;
    private static boolean finished;

    private TakeoverFeasibilityProbe() {}

    public static void register() {
        if (ENABLED) NeoForge.EVENT_BUS.addListener(TakeoverFeasibilityProbe::render);
    }

    private static void render(RenderLevelStageEvent event) {
        if (!ENABLED
                || finished
                || reload != null
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (bridge == null) bridge = new TakeoverRigidBridge(++epochs);
        var camera = event.getCamera();
        var origin = camera.getPosition().add(mc.player.getViewVector(1).scale(6));
        var left = new Matrix4f().rotate(camera.rotation()).translate(-.65f, 0, 0);
        var right = new Matrix4f().rotate(camera.rotation()).translate(.65f, 0, 0).scale(-1, 1, 1);
        // One local mesh, independent per-instance appearance, and a mirrored second instance.
        bridge.submit(
                origin.x,
                origin.y,
                origin.z,
                List.of(
                        new TakeoverRigidBridge.Intent(0x54414b450001L, left, 1, .02f, .02f),
                        new TakeoverRigidBridge.Intent(0x54414b450002L, right, .02f, 1, .02f)));
        drawFrames++;
    }

    public static void poll(Minecraft mc) {
        if (!ENABLED || finished || mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        if (start == 0) {
            require(
                    Files.isRegularFile(
                            mc.gameDirectory.toPath().resolve(".radiance-takeover-validation")),
                    "owned fixture");
            require(
                    mc.getWindow().getWidth() == 1920 && mc.getWindow().getHeight() == 1080,
                    "1920x1080");
            require(mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "muted");
            require(
                    !ModList.get().isLoaded("flywheel"),
                    "base feasibility must run without Flywheel");
            start = now;
            results.put("flywheelLoaded", false);
            results.put(
                    "scope",
                    "bounded rigid seam + actual vanilla parts + raster dependencies; not complete takeover");
            LOG.info(
                    "TAKEOVER_FEASIBILITY world-ready independent-core=true flywheel=false performance=false");
        }
        require(now - start < 180_000_000_000L, "bounded functional duration");
        long seconds = (now - start) / 1_000_000_000L;
        try {
            if (step == 0 && seconds >= 5) {
                MaterialStateBatchProbe.verify("takeover-initial");
                OffscreenDependencyProbe.verify("initial");
                require(PartModelProbe.checked() > 0, "actual vanilla part comparisons");
                PartModelProbe.checkpoint("takeover-initial");
                results.put("initialStateAndOffscreen", true);
                step = 1;
            } else if (step == 1 && seconds >= 10) {
                var server = java.util.Objects.requireNonNull(mc.getSingleplayerServer());
                var dimension = mc.level.dimension();
                server.execute(
                        () -> {
                            var level = server.getLevel(dimension);
                            for (var entity : level.getAllEntities())
                                if (entity instanceof ArmorStand stand) {
                                    stand.setHeadPose(new Rotations(25, 45, 10));
                                    stand.setBodyPose(new Rotations(0, -30, 0));
                                    stand.setShowArms(false);
                                }
                        });
                step = 2;
            } else if (step == 2 && seconds >= 16) {
                var stands = new ArrayList<ArmorStand>();
                for (var e : mc.level.entitiesForRendering())
                    if (e instanceof ArmorStand a) stands.add(a);
                require(stands.size() == 2, "two actual vanilla model owners");
                require(
                        stands.stream()
                                .allMatch(a -> a.getHeadPose().equals(new Rotations(25, 45, 10))),
                        "pose delivered to client");
                PartModelProbe.checkpoint("takeover-pose");
                RigidModelProbe.checkpoint("takeover-baked");
                Screenshot.grab(
                        mc.gameDirectory,
                        "takeover-before.png",
                        mc.getMainRenderTarget(),
                        message ->
                                LOG.info("TAKEOVER_FEASIBILITY capture={}", message.getString()));
                step = 3;
            } else if (step == 3 && seconds >= 22) {
                require(
                        bridge != null && bridge.submissions() > 0,
                        "independent native submissions");
                submissions += bridge.submissions();
                bridge.close();
                bridge = null;
                reload = mc.reloadResourcePacks();
                step = 4;
                LOG.info("TAKEOVER_FEASIBILITY reload-requested");
            } else if (step == 4 && reload != null && reload.isDone()) {
                reload.join();
                reload = null;
                resumed = now;
                step = 5;
                LOG.info("TAKEOVER_FEASIBILITY reload-complete");
            } else if (step == 5 && now - resumed >= 8_000_000_000L) {
                MaterialStateBatchProbe.verify("takeover-reloaded");
                OffscreenDependencyProbe.verify("reloaded");
                PartModelProbe.checkpoint("takeover-reloaded");
                require(epochs == 2 && bridge != null, "one mesh per resource epoch");
                submissions += bridge.submissions();
                Screenshot.grab(
                        mc.gameDirectory,
                        "takeover-after.png",
                        mc.getMainRenderTarget(),
                        message ->
                                LOG.info("TAKEOVER_FEASIBILITY capture={}", message.getString()));
                results.put("sourcePoseObserved", true);
                results.put("actualPartComparison", true);
                results.put("actualBakedComparison", true);
                results.put("reloadComplete", true);
                results.put("meshBuilds", epochs);
                results.put("instanceSubmissions", submissions);
                results.put("drawFrames", drawFrames);
                results.put("offscreenGpuReadback", true);
                results.put(
                        "visualAcceptance", "pending; screenshots are evidence, not user approval");
                Path output = Path.of(System.getProperty("validation.result"));
                Files.createDirectories(output);
                Files.writeString(
                        output.resolve("feasibility.json"),
                        new com.google.gson.GsonBuilder()
                                        .setPrettyPrinting()
                                        .create()
                                        .toJson(results)
                                + "\n");
                finished = true;
                bridge.close();
                bridge = null;
                LOG.info(
                        "TAKEOVER_FEASIBILITY normal-stop epochs={} submissions={} frames={}",
                        epochs,
                        submissions,
                        drawFrames);
                mc.stop();
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Takeover feasibility: " + message);
    }
}
